import { FastifyRequest, FastifyReply } from 'fastify';
import { verifySupabaseJwt } from '../services/supabaseJwt.js';
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
