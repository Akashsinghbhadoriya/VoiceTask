import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { authMiddleware } from '../middleware/auth.js';
import { getSupabase } from '../config/supabase.js';
import { createTaskSchema, updateTaskSchema } from '../schemas/task.js';
import { NotFoundError, BadRequestError, ForbiddenError } from '../utils/errors.js';
import { scheduleTaskReminder, cancelTaskReminder } from '../services/qstash.js';

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

  reply.status(200).send(data || []);
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

  reply.status(200).send(task);
}

async function postTask(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const bodyResult = createTaskSchema.safeParse(request.body);
  if (!bodyResult.success) {
    throw new BadRequestError('Invalid request body');
  }

  const { title, description, dueAt, reminderOffsetMinutes, priority, transcript } = bodyResult.data;

  const supabase = getSupabase();

  // Schedule reminder if dueAt is provided
  let qstashMessageId: string | null = null;
  if (dueAt) {
    const dueDate = new Date(dueAt);
    const fireTime = new Date(dueDate.getTime() - reminderOffsetMinutes * 60 * 1000);
    qstashMessageId = await scheduleTaskReminder('temp-id', fireTime) || null;
  }

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
      qstash_message_id: qstashMessageId,
    } as any)
    .select()
    .single()) as any;

  if (error) throw new BadRequestError(error.message);

  reply.status(201).send(task);
}

async function patchTask(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const { id } = request.params as { id: string };

  const bodyResult = updateTaskSchema.safeParse(request.body);
  if (!bodyResult.success) {
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

  reply.status(200).send(updated);
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

  reply.status(204).send();
}

export async function registerTasksRoutes(fastify: FastifyInstance) {
  fastify.get('/tasks', getTasks);
  fastify.get('/tasks/:id', getTaskById);
  fastify.post('/tasks', postTask);
  fastify.patch('/tasks/:id', patchTask);
  fastify.delete('/tasks/:id', deleteTask);
}
