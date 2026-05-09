import Fastify from 'fastify';
import helmet from '@fastify/helmet';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import { getEnv } from './config/env.js';
import { registerErrorHandler } from './utils/errors.js';
import { registerUsersRoutes } from './routes/users.js';
import { registerVoiceRoutes } from './routes/voice.js';
import { registerTasksRoutes } from './routes/tasks.js';
import { registerDevicesRoutes } from './routes/devices.js';
import { registerWebhookRoutes } from './routes/webhooks.js';

async function main() {
  const env = getEnv();

  const fastify = Fastify({
    logger: {
      level: env.LOG_LEVEL,
      ...(env.NODE_ENV !== 'production' && {
        transport: {
          target: 'pino-pretty',
          options: {
            colorize: true,
            translateTime: 'SYS:standard',
            ignore: 'pid,hostname',
          },
        },
      }),
    },
  });

  // Plugins
  await fastify.register(helmet);
  await fastify.register(cors, { origin: true });
  await fastify.register(multipart);

  // Error handler
  registerErrorHandler(fastify);

  // Routes
  await registerUsersRoutes(fastify);
  await registerVoiceRoutes(fastify);
  await registerTasksRoutes(fastify);
  await registerDevicesRoutes(fastify);
  await registerWebhookRoutes(fastify);

  // Health check
  fastify.get('/health', async (request, reply) => {
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
    };
  });

  // Graceful shutdown
  const signals = ['SIGTERM', 'SIGINT'];
  signals.forEach((signal) => {
    process.on(signal, async () => {
      fastify.log.info(`Received ${signal}, shutting down gracefully...`);
      await fastify.close();
      process.exit(0);
    });
  });

  // Start server
  try {
    await fastify.listen({ port: env.PORT, host: '0.0.0.0' });
    fastify.log.info(`Server running on port ${env.PORT}`);
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
}

main();
