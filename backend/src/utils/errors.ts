import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';

export class AppError extends Error {
  constructor(
    public statusCode: number,
    public code: string,
    message: string
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export class UnauthorizedError extends AppError {
  constructor(message = 'Unauthorized') {
    super(401, 'UNAUTHORIZED', message);
  }
}

export class ForbiddenError extends AppError {
  constructor(message = 'Forbidden') {
    super(403, 'FORBIDDEN', message);
  }
}

export class NotFoundError extends AppError {
  constructor(message = 'Not found') {
    super(404, 'NOT_FOUND', message);
  }
}

export class BadRequestError extends AppError {
  constructor(message = 'Bad request') {
    super(400, 'BAD_REQUEST', message);
  }
}

export class ConflictError extends AppError {
  constructor(message = 'Conflict') {
    super(409, 'CONFLICT', message);
  }
}

export function registerErrorHandler(fastify: FastifyInstance) {
  fastify.setErrorHandler(
    (error: Error, request: FastifyRequest, reply: FastifyReply) => {
      const logger = fastify.log;

      if (error instanceof AppError) {
        logger.warn(
          { statusCode: error.statusCode, code: error.code, message: error.message },
          'Application error'
        );
        return reply.status(error.statusCode).send({
          error: error.message,
          code: error.code,
        });
      }

      logger.error(error, 'Unhandled error');
      return reply.status(500).send({
        error: 'Internal server error',
        code: 'INTERNAL_SERVER_ERROR',
      });
    }
  );
}
