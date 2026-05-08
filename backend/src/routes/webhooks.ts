import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { Receiver } from '@upstash/qstash';
import { getEnv } from '../config/env.js';
import { getSupabase } from '../config/supabase.js';
import { sendTaskReminderNotifications } from '../services/fcm.js';
import { BadRequestError } from '../utils/errors.js';

const webhookBodySchema = z.object({
  taskId: z.string(),
});

async function postQstashWebhook(request: FastifyRequest, reply: FastifyReply) {
  const env = getEnv();

  // Verify QStash signature
  const receiver = new Receiver({
    currentSigningKey: env.QSTASH_CURRENT_SIGNING_KEY,
    nextSigningKey: env.QSTASH_NEXT_SIGNING_KEY,
  });

  try {
    const signature = request.headers['upstash-signature'] as string;
    if (!signature) {
      throw new BadRequestError('Missing Upstash-Signature header');
    }

    const verified = await receiver.verify({
      signature,
      body: JSON.stringify(request.body),
    });

    if (!verified) {
      throw new BadRequestError('Invalid QStash signature');
    }
  } catch (error) {
    request.log.error(error, 'QStash signature verification failed');
    return reply.status(400).send({
      error: 'Invalid signature',
      code: 'INVALID_SIGNATURE',
    });
  }

  // Parse body
  const bodyResult = webhookBodySchema.safeParse(request.body);
  if (!bodyResult.success) {
    throw new BadRequestError('Invalid webhook body');
  }

  const { taskId } = bodyResult.data;

  const supabase = getSupabase();

  // Fetch task with user devices
  const { data: task, error: taskError } = (await supabase
    .from('tasks')
    .select(
      `
      *,
      user:user_id (
        devices (*)
      )
    `
    )
    .eq('id', taskId)
    .single()) as any;

  if (taskError) {
    // Task was deleted - return 200 to acknowledge
    return reply.status(200).send({ success: true });
  }

  // Skip if task is no longer pending
  if (task.status !== 'PENDING') {
    return reply.status(200).send({ success: true });
  }

  // Send FCM notifications
  try {
    const devices = task.user.devices || [];
    await sendTaskReminderNotifications(devices, task, supabase);

    // Update task status to COMPLETED after successful notification
    await (supabase.from('tasks') as any)
      .update({ status: 'COMPLETED' })
      .eq('id', taskId);
  } catch (error) {
    request.log.error(error, 'Failed to send FCM notifications');
    return reply.status(200).send({ success: false, error: String(error) });
  }

  reply.status(200).send({ success: true });
}

export async function registerWebhookRoutes(fastify: FastifyInstance) {
  fastify.post('/webhooks/qstash', postQstashWebhook);
}
