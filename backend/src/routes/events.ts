import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { eventService } from '../services/events.js';
import { authMiddleware } from '../middleware/auth.js';
import {
  ingestEventsSchema,
  paginationSchema,
  type IngestEventsInput,
} from '../schemas/api.js';

export async function eventRoutes(fastify: FastifyInstance): Promise<void> {
  // All event routes require auth
  fastify.addHook('onRequest', authMiddleware);

  // Ingest events
  fastify.post<{ Body: IngestEventsInput }>(
    '/events/ingest',
    {
      schema: {
        body: {
          type: 'object',
          required: ['events'],
          properties: {
            events: {
              type: 'array',
              items: {
                type: 'object',
                required: [
                  'eventId',
                  'deviceId',
                  'appSource',
                  'postedAt',
                  'textRedacted',
                ],
                properties: {
                  eventId: { type: 'string' },
                  deviceId: { type: 'string' },
                  appSource: { type: 'string' },
                  postedAt: { type: 'string' },
                  textRedacted: { type: 'string' },
                  textRaw: { type: 'string' },
                  locale: { type: 'string' },
                  timezone: { type: 'string' },
                },
              },
            },
          },
        },
      },
    },
    async (request, reply) => {
      const parsed = ingestEventsSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: parsed.error.message });
      }

      const results = await eventService.ingestEvents(
        request.userId!,
        parsed.data.events
      );

      // Trigger async parsing (fire and forget)
      eventService.parsePendingEvents(request.userId!, 10).catch((err) => {
        console.error('Background parse failed:', err);
      });

      const accepted = results.filter((r) => r.status === 'accepted').length;
      const duplicates = results.filter((r) => r.status === 'duplicate').length;
      const errors = results.filter((r) => r.status === 'error').length;

      return reply.send({
        success: true,
        accepted,
        duplicates,
        errors,
        details: results,
      });
    }
  );

  // Retry failed events
  fastify.post('/events/retry-failed', async (request, reply) => {
    const result = await eventService.retryFailedEvents(request.userId!);
    return reply.send(result);
  });

  // Get recent transactions
  fastify.get<{
    Querystring: { limit?: string; offset?: string };
  }>('/transactions/recent', async (request, reply) => {
    const parsed = paginationSchema.safeParse(request.query);
    const { limit, offset } = parsed.success
      ? parsed.data
      : { limit: 20, offset: 0 };

    const transactions = await eventService.getRecentTransactions(
      request.userId!,
      limit,
      offset
    );

    return reply.send({
      transactions,
      pagination: {
        limit,
        offset,
        hasMore: transactions.length === limit,
      },
    });
  });
}
