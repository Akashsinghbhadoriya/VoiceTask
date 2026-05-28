import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { authMiddleware } from '../middleware/auth.js';
import { getSupabase } from '../config/supabase.js';
import { createTaskSchema, updateTaskSchema } from '../schemas/task.js';
import { NotFoundError, BadRequestError, ForbiddenError } from '../utils/errors.js';
import { scheduleTaskReminder, cancelTaskReminder } from '../services/qstash.js';
import { convertUtcToUserTimezone, transcribeAudio, extractTaskDetails } from '../services/openai.js';
import { sendScheduleAlarmMessage, sendCancelAlarmMessage } from '../services/fcm.js';

async function getDevicesForUser(supabase: any, userId: string): Promise<any[]> {
  const { data, error } = await supabase.from('devices').select('*').eq('user_id', userId);
  if (error) {
    console.error('Failed to fetch user devices:', error.message);
    return [];
  }
  return data ?? [];
}

// Helper to add dueAtUser field for user's timezone display
async function addDueAtUserField(tasks: any[], userId: string): Promise<any[]> {
  const supabase = getSupabase();
  const { data: user } = (await supabase
    .from('users')
    .select('timezone')
    .eq('id', userId)
    .single()) as any;

  const timezone = user?.timezone || 'Asia/Kolkata';

  return tasks.map(task => ({
    ...task,
    dueAtUser: convertUtcToUserTimezone(task.due_at, timezone),
  }));
}

async function getTasks(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const status = (request.query as Record<string, any>).status as string | undefined;

  const supabase = getSupabase();
  let query = supabase
    .from('tasks')
    .select('*')
    .eq('user_id', request.user!.id);

  if (status) {
    query = query.eq('status', status.toUpperCase());
  }

  const { data, error } = (await query.order('due_at', { ascending: true, nullsFirst: false })) as any;

  if (error) throw new BadRequestError(error.message);

  const tasksWithUserTime = await addDueAtUserField(data || [], request.user!.id);
  reply.status(200).send(tasksWithUserTime);
}

async function getTaskById(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const { id } = request.params as { id: string };

  const supabase = getSupabase();
  const { data: task, error } = (await supabase
    .from('tasks')
    .select('*')
    .eq('id', id)
    .single()) as any;

  if (error) {
    if (error.code === 'PGRST116') throw new NotFoundError('Task not found');
    throw new BadRequestError(error.message);
  }

  if (task.user_id !== request.user!.id) {
    throw new ForbiddenError('You do not own this task');
  }

  const [taskWithUserTime] = await addDueAtUserField([task], request.user!.id);
  reply.status(200).send(taskWithUserTime);
}

async function postTask(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const bodyResult = createTaskSchema.safeParse(request.body);
  if (!bodyResult.success) {
    request.log.error(
      { errors: bodyResult.error.errors, body: request.body },
      'Task creation validation failed'
    );
    throw new BadRequestError('Invalid request body');
  }

  const { title, description, dueAt, reminderOffsetMinutes, priority, transcript } = bodyResult.data;

  request.log.info(
    {
      title,
      description,
      dueAt,
      reminderOffsetMinutes,
      priority,
      hasTranscript: !!transcript,
    },
    'Creating task with extracted details'
  );

  const supabase = getSupabase();

  const { data: task, error } = (await supabase
    .from('tasks')
    .insert({
      user_id: request.user!.id,
      title,
      description: description || null,
      due_at: dueAt || null,
      reminder_offset_minutes: reminderOffsetMinutes,
      priority: priority.toUpperCase(),
      transcript: transcript || null,
      status: 'PENDING',
      qstash_message_id: null,
    } as any)
    .select()
    .single()) as any;

  if (error) {
    request.log.error({ error }, 'Task creation database error');
    throw new BadRequestError(error.message);
  }

  // Schedule reminder with the actual task ID
  let qstashMessageId: string | null = null;
  if (dueAt) {
    const dueDate = new Date(dueAt);
    const fireTime = new Date(dueDate.getTime() - reminderOffsetMinutes * 60 * 1000);

    request.log.info(
      { taskId: task.id, fireTime, dueDate, reminderOffsetMinutes },
      'Scheduling reminder'
    );

    try {
      qstashMessageId = await scheduleTaskReminder(task.id, fireTime) || null;
      request.log.info({ taskId: task.id, qstashMessageId }, 'Reminder scheduled');

      // Update task with qstash message ID
      if (qstashMessageId) {
        const { error: updateError } = await (supabase.from('tasks') as any)
          .update({ qstash_message_id: qstashMessageId })
          .eq('id', task.id);

        if (updateError) {
          request.log.error({ updateError }, 'Failed to update qstash_message_id');
        }
      } else {
        request.log.warn({ taskId: task.id }, 'scheduleTaskReminder returned empty messageId');
      }
    } catch (error) {
      request.log.error({ error, taskId: task.id }, 'Failed to schedule reminder');
    }
  } else {
    request.log.warn('Task created without dueAt - no reminder will be scheduled');
  }

  // Send schedule alarm data message to all user devices (fire-and-forget)
  if (dueAt) {
    const devices = await getDevicesForUser(supabase, request.user!.id);
    sendScheduleAlarmMessage(devices, task, supabase).catch((err) =>
      request.log.error({ err, taskId: task.id }, 'Failed to send SCHEDULE_ALARM FCM')
    );
  }

  const [taskWithUserTime] = await addDueAtUserField([task], request.user!.id);
  reply.status(201).send(taskWithUserTime);
}

async function patchTask(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const { id } = request.params as { id: string };

  const bodyResult = updateTaskSchema.safeParse(request.body);
  if (!bodyResult.success) {
    request.log.error(
      { errors: bodyResult.error.errors, body: request.body },
      'Task update validation failed'
    );
    throw new BadRequestError('Invalid request body');
  }

  const supabase = getSupabase();
  const { data: task, error: fetchError } = (await supabase
    .from('tasks')
    .select('*')
    .eq('id', id)
    .single()) as any;

  if (fetchError) {
    if (fetchError.code === 'PGRST116') throw new NotFoundError('Task not found');
    throw new BadRequestError(fetchError.message);
  }

  if (task.user_id !== request.user!.id) {
    throw new ForbiddenError('You do not own this task');
  }

  const { title, description, dueAt, reminderOffsetMinutes, priority, status, transcript } = bodyResult.data;

  const newDueAt = dueAt !== undefined ? (dueAt ? new Date(dueAt) : null) : task.due_at;
  const newReminderOffset = reminderOffsetMinutes ?? task.reminder_offset_minutes;
  const newStatus = status ? status.toUpperCase() : task.status;

  // Handle QStash rescheduling
  let newQstashMessageId = task.qstash_message_id;
  if (
    task.qstash_message_id &&
    (newStatus !== 'PENDING' || newDueAt !== task.due_at || newReminderOffset !== task.reminder_offset_minutes)
  ) {
    await cancelTaskReminder(task.qstash_message_id);
    newQstashMessageId = null;
  }

  // Schedule new reminder if needed
  if (newStatus === 'PENDING' && newDueAt && !newQstashMessageId) {
    const fireTime = new Date(newDueAt.getTime() - newReminderOffset * 60 * 1000);
    newQstashMessageId = await scheduleTaskReminder(id, fireTime) || null;
  }

  const { data: updated, error: updateError } = (await (supabase.from('tasks') as any)
    .update({
      title: title ?? task.title,
      description: description ?? task.description,
      due_at: newDueAt,
      reminder_offset_minutes: newReminderOffset,
      priority: priority ? priority.toUpperCase() : task.priority,
      status: newStatus,
      transcript: transcript ?? task.transcript,
      qstash_message_id: newQstashMessageId,
      updated_at: new Date().toISOString(),
    })
    .eq('id', id)
    .select()
    .single()) as any;

  if (updateError) throw new BadRequestError(updateError.message);

  // Send alarm data message to all user devices (fire-and-forget)
  {
    const devices = await getDevicesForUser(supabase, request.user!.id);
    if (newStatus === 'PENDING' && newDueAt) {
      sendScheduleAlarmMessage(devices, updated, supabase).catch((err) =>
        request.log.error({ err, taskId: id }, 'Failed to send SCHEDULE_ALARM FCM')
      );
    } else {
      sendCancelAlarmMessage(devices, id).catch((err) =>
        request.log.error({ err, taskId: id }, 'Failed to send CANCEL_ALARM FCM')
      );
    }
  }

  const [updatedWithUserTime] = await addDueAtUserField([updated], request.user!.id);
  reply.status(200).send(updatedWithUserTime);
}

async function deleteTask(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const { id } = request.params as { id: string };

  const supabase = getSupabase();
  const { data: task, error: fetchError } = (await supabase
    .from('tasks')
    .select('*')
    .eq('id', id)
    .single()) as any;

  if (fetchError) {
    if (fetchError.code === 'PGRST116') throw new NotFoundError('Task not found');
    throw new BadRequestError(fetchError.message);
  }

  if (task.user_id !== request.user!.id) {
    throw new ForbiddenError('You do not own this task');
  }

  // Cancel QStash reminder
  if (task.qstash_message_id) {
    await cancelTaskReminder(task.qstash_message_id);
  }

  const { error: deleteError } = await supabase
    .from('tasks')
    .delete()
    .eq('id', id);

  if (deleteError) throw new BadRequestError(deleteError.message);

  // Send cancel alarm data message to all user devices (fire-and-forget)
  {
    const devices = await getDevicesForUser(supabase, task.user_id);
    sendCancelAlarmMessage(devices, id).catch((err) =>
      request.log.error({ err, taskId: id }, 'Failed to send CANCEL_ALARM FCM')
    );
  }

  reply.status(204).send();
}

async function postRescheduleVoice(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const { id } = request.params as { id: string };

  const supabase = getSupabase();
  const { data: task, error: fetchError } = (await supabase
    .from('tasks')
    .select('*')
    .eq('id', id)
    .single()) as any;

  if (fetchError) {
    if (fetchError.code === 'PGRST116') throw new NotFoundError('Task not found');
    throw new BadRequestError(fetchError.message);
  }

  if (task.user_id !== request.user!.id) {
    throw new ForbiddenError('You do not own this task');
  }

  // Parse multipart: audio file + optional timezone field
  let audioBuffer: Buffer | null = null;
  let timezone = 'Asia/Kolkata';

  try {
    const parts = request.parts();
    for await (const part of parts) {
      if (part.type === 'file' && part.fieldname === 'audio') {
        const chunks: Buffer[] = [];
        for await (const chunk of part.file) {
          chunks.push(chunk instanceof Buffer ? chunk : Buffer.from(chunk));
        }
        audioBuffer = Buffer.concat(chunks);
      } else if (part.type === 'field' && part.fieldname === 'timezone') {
        timezone = (part as any).value as string;
      }
    }
  } catch (error) {
    throw new BadRequestError('Failed to parse multipart request');
  }

  if (!audioBuffer || audioBuffer.length === 0) {
    throw new BadRequestError('No audio provided');
  }

  // Transcribe audio → extract new dueAt
  let transcript: string;
  try {
    transcript = await transcribeAudio(audioBuffer);
  } catch (error) {
    request.log.error(error, 'Transcription failed in reschedule-voice');
    throw new BadRequestError('Transcription failed');
  }

  let extracted: Awaited<ReturnType<typeof extractTaskDetails>>;
  try {
    extracted = await extractTaskDetails(transcript, timezone);
  } catch (error) {
    request.log.error(error, 'Extraction failed in reschedule-voice');
    throw new BadRequestError('Time extraction failed');
  }

  if (!extracted.dueAt) {
    return reply.status(400).send({
      error: 'could_not_parse_time',
      message: 'Could not understand the time from your voice. Please try again.',
    });
  }

  const newDueAt = new Date(extracted.dueAt);
  if (newDueAt <= new Date()) {
    return reply.status(400).send({
      error: 'time_in_past',
      message: 'The time you specified is in the past. Please say a future time.',
    });
  }

  // Cancel old QStash message
  if (task.qstash_message_id) {
    await cancelTaskReminder(task.qstash_message_id);
  }

  // Schedule new QStash message
  const reminderOffset = task.reminder_offset_minutes ?? 15;
  const fireTime = new Date(newDueAt.getTime() - reminderOffset * 60 * 1000);
  const newQstashMessageId = (await scheduleTaskReminder(id, fireTime)) || null;

  // Update task with new dueAt + qstashMessageId
  const { data: updated, error: updateError } = (await (supabase.from('tasks') as any)
    .update({
      due_at: newDueAt.toISOString(),
      qstash_message_id: newQstashMessageId,
      status: 'PENDING',
      updated_at: new Date().toISOString(),
    })
    .eq('id', id)
    .select()
    .single()) as any;

  if (updateError) throw new BadRequestError(updateError.message);

  // Send SCHEDULE_ALARM FCM so device reschedules local alarm
  const devices = await getDevicesForUser(supabase, request.user!.id);
  sendScheduleAlarmMessage(devices, updated, supabase).catch((err: Error) =>
    request.log.error({ err, taskId: id }, 'Failed to send SCHEDULE_ALARM FCM after reschedule-voice')
  );

  const [updatedWithUserTime] = await addDueAtUserField([updated], request.user!.id);
  reply.status(200).send(updatedWithUserTime);
}

export async function registerTasksRoutes(fastify: FastifyInstance) {
  fastify.get('/tasks', getTasks);
  fastify.get('/tasks/:id', getTaskById);
  fastify.post('/tasks', postTask);
  fastify.patch('/tasks/:id', patchTask);
  fastify.delete('/tasks/:id', deleteTask);
  fastify.post('/tasks/:id/reschedule-voice', postRescheduleVoice);
}
