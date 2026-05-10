import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { getSupabase } from '../config/supabase.js';
import { BadRequestError, NotFoundError } from '../utils/errors.js';
import { sendScheduleAlarmMessage } from '../services/fcm.js';

const registerDeviceSchema = z.object({
  fcmToken: z.string().min(1),
  platform: z.string().min(1),
});

type RegisterDeviceInput = z.infer<typeof registerDeviceSchema>;

async function postDevice(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const result = registerDeviceSchema.safeParse(request.body);
  if (!result.success) {
    throw new BadRequestError('Invalid request body');
  }

  const { fcmToken, platform } = result.data;

  const supabase = getSupabase();

  // Remove old tokens for this user+platform before registering new one
  await supabase
    .from('devices')
    .delete()
    .eq('user_id', request.user!.id)
    .eq('platform', platform)
    .neq('fcm_token', fcmToken);

  // Upsert device
  const { data: device, error } = (await supabase
    .from('devices')
    .upsert(
      {
        fcm_token: fcmToken,
        user_id: request.user!.id,
        platform,
        last_seen_at: new Date().toISOString(),
      } as any,
      { onConflict: 'fcm_token' }
    )
    .select()
    .single()) as any;

  if (error) throw new BadRequestError(error.message);

  // Send SCHEDULE_ALARM for all pending future tasks — catches up tasks created before
  // the device was registered (first install, reinstall, FCM token refresh)
  const now = new Date().toISOString();
  const { data: pendingTasks } = (await supabase
    .from('tasks')
    .select('*')
    .eq('user_id', request.user!.id)
    .eq('status', 'PENDING')
    .not('due_at', 'is', null)
    .gt('due_at', now)) as any;

  if (pendingTasks?.length) {
    for (const task of pendingTasks) {
      sendScheduleAlarmMessage([device], task, supabase).catch((err) =>
        request.log.error({ err, taskId: task.id }, 'Failed to send SCHEDULE_ALARM on device register')
      );
    }
  }

  reply.status(200).send(device);
}

async function deleteDevice(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const { fcmToken } = request.params as { fcmToken: string };

  const supabase = getSupabase();

  // Get device to verify ownership
  const { data: device, error: fetchError } = (await supabase
    .from('devices')
    .select('*')
    .eq('fcm_token', fcmToken)
    .single()) as any;

  if (fetchError) {
    if (fetchError.code === 'PGRST116') throw new NotFoundError('Device not found');
    throw new BadRequestError(fetchError.message);
  }

  if (device.user_id !== request.user!.id) {
    throw new BadRequestError('Device does not belong to current user');
  }

  const { error: deleteError } = await supabase
    .from('devices')
    .delete()
    .eq('fcm_token', fcmToken);

  if (deleteError) throw new BadRequestError(deleteError.message);

  reply.status(204).send();
}

export async function registerDevicesRoutes(fastify: FastifyInstance) {
  fastify.post('/devices', postDevice);
  fastify.delete('/devices/:fcmToken', deleteDevice);
}
