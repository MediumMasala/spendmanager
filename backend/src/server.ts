import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import swagger from '@fastify/swagger';
import swaggerUi from '@fastify/swagger-ui';

import { config, isDev } from './config.js';
import { connectDatabase, disconnectDatabase } from './db.js';
import { connectRedis, disconnectRedis } from './redis.js';
import { authRoutes } from './routes/auth.js';
import { eventRoutes } from './routes/events.js';
import { summaryRoutes } from './routes/summary.js';
import { notificationRoutes } from './routes/notifications.js';
import { startScheduler } from './scheduler.js';
import { llmService, costGuard } from './llm/index.js';

const fastify = Fastify({
  logger: {
    level: config.LOG_LEVEL,
    transport: isDev
      ? {
          target: 'pino-pretty',
          options: { colorize: true },
        }
      : undefined,
  },
});

// Plugins
await fastify.register(cors, {
  origin: isDev ? true : [
    'https://spendmanager.app',
    /\.vercel\.app$/,  // Allow all Vercel preview/production URLs
  ],
  credentials: true,
});

await fastify.register(helmet, {
  contentSecurityPolicy: false, // Disable for API
});

await fastify.register(rateLimit, {
  max: config.RATE_LIMIT_MAX,
  timeWindow: config.RATE_LIMIT_WINDOW_MS,
});

// Swagger documentation
await fastify.register(swagger, {
  openapi: {
    info: {
      title: 'SpendManager API',
      description: 'AI-powered money tracking backend',
      version: '1.0.0',
    },
    servers: [
      { url: `http://localhost:${config.PORT}`, description: 'Local' },
    ],
    components: {
      securitySchemes: {
        bearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT',
        },
      },
    },
  },
});

await fastify.register(swaggerUi, {
  routePrefix: '/docs',
});

// Health check
fastify.get('/health', async () => ({
  status: 'ok',
  timestamp: new Date().toISOString(),
  version: '1.0.0',
}));

// LLM health check
fastify.get('/health/llm', async () => {
  const providers = await llmService.healthCheck();
  const stats = await costGuard.getDailyStats();

  return {
    providers,
    budget: stats,
  };
});

// Routes
await fastify.register(authRoutes, { prefix: '/v1' });
await fastify.register(eventRoutes, { prefix: '/v1' });
await fastify.register(summaryRoutes, { prefix: '/v1' });
await fastify.register(notificationRoutes, { prefix: '/v1' });

// WhatsApp webhook (public)
fastify.get<{
  Querystring: {
    'hub.mode'?: string;
    'hub.verify_token'?: string;
    'hub.challenge'?: string;
  };
}>('/webhook/whatsapp', async (request, reply) => {
  const mode = request.query['hub.mode'];
  const token = request.query['hub.verify_token'];
  const challenge = request.query['hub.challenge'];

  if (mode && token && challenge) {
    const { whatsappService } = await import('./services/whatsapp.js');
    const result = whatsappService.verifyWebhook(mode, token, challenge);

    if (result) {
      return reply.type('text/plain').send(result);
    }
  }

  return reply.status(403).send('Forbidden');
});

fastify.post('/webhook/whatsapp', async (request, reply) => {
  // Handle WhatsApp webhook events (status updates, etc.)
  const body = request.body as any;

  if (body?.entry?.[0]?.changes?.[0]?.value?.statuses) {
    const statuses = body.entry[0].changes[0].value.statuses;
    const { whatsappService } = await import('./services/whatsapp.js');

    for (const status of statuses) {
      if (status.id && status.status) {
        await whatsappService.handleStatusUpdate(status.id, status.status);
      }
    }
  }

  return reply.send({ success: true });
});

// Error handler
fastify.setErrorHandler((error, request, reply) => {
  fastify.log.error(error);

  const statusCode = error.statusCode ?? 500;
  const message = isDev ? error.message : 'Internal Server Error';

  reply.status(statusCode).send({
    error: message,
    statusCode,
  });
});

// Graceful shutdown
const shutdown = async () => {
  console.log('Shutting down...');

  await fastify.close();
  await disconnectDatabase();
  await disconnectRedis();

  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

// Start server
const start = async () => {
  try {
    // Connect to databases
    await connectDatabase();
    await connectRedis();

    // Start scheduler
    startScheduler();

    // Start server
    await fastify.listen({
      port: config.PORT,
      host: config.HOST,
    });

    console.log(`Server running at http://${config.HOST}:${config.PORT}`);
    console.log(`API docs at http://${config.HOST}:${config.PORT}/docs`);
  } catch (error) {
    fastify.log.error(error);
    process.exit(1);
  }
};

start();
