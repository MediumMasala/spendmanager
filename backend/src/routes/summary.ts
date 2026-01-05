import type { FastifyInstance } from 'fastify';
import { summaryService } from '../services/summary.js';
import { whatsappService } from '../services/whatsapp.js';
import { authMiddleware } from '../middleware/auth.js';
import { prisma } from '../db.js';

export async function summaryRoutes(fastify: FastifyInstance): Promise<void> {
  // All summary routes require auth
  fastify.addHook('onRequest', authMiddleware);

  // Get latest summary
  fastify.get('/summary/latest', async (request, reply) => {
    const summary = await summaryService.getLatestSummary(request.userId!);

    if (!summary) {
      return reply.status(404).send({
        error: 'No summary available. Process some transactions first.',
      });
    }

    return reply.send(summary);
  });

  // Compute/refresh current week summary
  fastify.post('/summary/compute', async (request, reply) => {
    // First parse any pending events
    const user = await prisma.user.findUnique({
      where: { id: request.userId! },
      include: { consent: true },
    });

    if (!user?.consent?.cloudAiEnabled) {
      return reply.status(400).send({
        error: 'Cloud AI parsing must be enabled to compute summaries',
      });
    }

    const summary = await summaryService.computeWeeklySummary(request.userId!);

    // If WhatsApp opted in, send summary
    if (user.consent.whatsappEnabled && user.whatsappE164) {
      const sendResult = await whatsappService.sendWeeklySummary(
        request.userId!,
        user.whatsappE164,
        summary
      );

      return reply.send({
        summary,
        whatsappSent: sendResult.success,
        whatsappError: sendResult.error,
      });
    }

    return reply.send({ summary, whatsappSent: false });
  });
}
