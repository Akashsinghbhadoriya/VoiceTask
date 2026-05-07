import { FastifyRequest, FastifyReply } from 'fastify';
import { verifySupabaseJwt } from '../services/supabaseJwt.js';
import { getSupabase } from '../config/supabase.js';
import { UnauthorizedError } from '../utils/errors.js';

export interface AuthUser {
  id: string;
  email: string;
}

export async function authMiddleware(request: FastifyRequest, reply: FastifyReply) {
  const authHeader = request.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    throw new UnauthorizedError('Missing or invalid Authorization header');
  }

  const token = authHeader.slice(7);

  try {
    const user = await verifySupabaseJwt(token);
    request.user = user;

    // Ensure user exists in database (create or update)
    const supabase = getSupabase();
    const { data: existingUser } = (await supabase
      .from('users')
      .select('id')
      .eq('id', user.id)
      .single()) as any;

    if (!existingUser) {
      // Create user with default timezone
      await supabase
        .from('users')
        .insert({
          id: user.id,
          email: user.email,
          timezone: 'Asia/Kolkata',
        } as any)
        .select()
        .single();
    }
  } catch (error) {
    throw new UnauthorizedError(
      error instanceof Error ? error.message : 'Invalid token'
    );
  }
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthUser;
    }
  }
}

// For Fastify, we extend the FastifyRequest type
declare module 'fastify' {
  interface FastifyRequest {
    user?: AuthUser;
  }
}
