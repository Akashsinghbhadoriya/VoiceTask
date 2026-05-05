import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { z } from 'zod';
import { getSupabase } from '../config/supabase.js';
import { authMiddleware } from '../middleware/auth.js';
import { NotFoundError, BadRequestError } from '../utils/errors.js';

const updateUserSchema = z.object({
  name: z.string().optional(),
  picture: z.string().url().optional(),
  timezone: z.string().optional(),
});

type UpdateUserInput = z.infer<typeof updateUserSchema>;

async function postUserMe(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const result = updateUserSchema.safeParse(request.body);
  if (!result.success) {
    throw new BadRequestError('Invalid request body');
  }

  const supabase = getSupabase();
  const userId = request.user!.id;

  // Check if user exists
  const { data: existingUser } = (await supabase
    .from('users')
    .select('id')
    .eq('id', userId)
    .single()) as any;

  if (existingUser) {
    // Update existing user
    const { data, error } = (await (supabase.from('users') as any)
      .update({
        email: request.user!.email,
        name: result.data.name,
        picture: result.data.picture,
        timezone: result.data.timezone,
        updated_at: new Date().toISOString(),
      })
      .eq('id', userId)
      .select()
      .single()) as any;

    if (error) throw new BadRequestError(error.message);

    reply.status(200).send({
      id: data.id,
      email: data.email,
      name: data.name,
      picture: data.picture,
      timezone: data.timezone,
    });
  } else {
    // Create new user
    const { data, error } = (await supabase
      .from('users')
      .insert({
        id: userId,
        email: request.user!.email,
        name: result.data.name,
        picture: result.data.picture,
        timezone: result.data.timezone || 'Asia/Kolkata',
      } as any)
      .select()
      .single()) as any;

    if (error) throw new BadRequestError(error.message);

    reply.status(200).send({
      id: data.id,
      email: data.email,
      name: data.name,
      picture: data.picture,
      timezone: data.timezone,
    });
  }
}

async function patchUserMe(request: FastifyRequest, reply: FastifyReply) {
  await authMiddleware(request, reply);

  const result = updateUserSchema.safeParse(request.body);
  if (!result.success) {
    throw new BadRequestError('Invalid request body');
  }

  const supabase = getSupabase();
  const userId = request.user!.id;

  const { data, error } = (await (supabase.from('users') as any)
    .update({
      name: result.data.name,
      picture: result.data.picture,
      timezone: result.data.timezone,
      updated_at: new Date().toISOString(),
    })
    .eq('id', userId)
    .select()
    .single()) as any;

  if (error) {
    if (error.code === 'PGRST116') {
      throw new NotFoundError('User not found');
    }
    throw new BadRequestError(error.message);
  }

  reply.status(200).send({
    id: data.id,
    email: data.email,
    name: data.name,
    picture: data.picture,
    timezone: data.timezone,
  });
}

export async function registerUsersRoutes(fastify: FastifyInstance) {
  fastify.post('/users/me', postUserMe);
  fastify.patch('/users/me', patchUserMe);
}
