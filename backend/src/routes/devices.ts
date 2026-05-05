import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { getSupabase } from '../config/supabase.js';
import { BadRequestError, NotFoundError } from '../utils/errors.js';

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
