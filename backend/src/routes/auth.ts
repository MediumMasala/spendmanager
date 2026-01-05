import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { authService } from '../services/auth.js';
import { prisma } from '../db.js';
import {
  requestOtpSchema,
  verifyOtpSchema,
  updateConsentSchema,
  optInWhatsappSchema,
  type RequestOtpInput,
  type VerifyOtpInput,
  type UpdateConsentInput,
  type OptInWhatsappInput,
} from '../schemas/api.js';
import { authMiddleware } from '../middleware/auth.js';
import { hashPhone } from '../utils/crypto.js';

export async function authRoutes(fastify: FastifyInstance): Promise<void> {
  // Request OTP
  fastify.post<{ Body: RequestOtpInput }>(
    '/auth/request-otp',
    {
      schema: {
        body: {
          type: 'object',
          required: ['phone'],
          properties: {
            phone: { type: 'string' },
            countryCode: { type: 'string', default: 'IN' },
          },
        },
      },
    },
    async (request, reply) => {
      const parsed = requestOtpSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: parsed.error.message });
      }

      const result = await authService.requestOtp(
        parsed.data.phone,
        parsed.data.countryCode
      );

      return reply.send(result);
    }
  );

  // Verify OTP
  fastify.post<{ Body: VerifyOtpInput }>(
    '/auth/verify-otp',
    {
      schema: {
        body: {
          type: 'object',
          required: ['phone', 'otp'],
          properties: {
            phone: { type: 'string' },
            otp: { type: 'string' },
            deviceId: { type: 'string' },
            deviceInfo: {
              type: 'object',
              properties: {
                platform: { type: 'string' },
                appVersion: { type: 'string' },
                osVersion: { type: 'string' },
              },
            },
          },
        },
      },
    },
    async (request, reply) => {
      const parsed = verifyOtpSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.status(400).send({ error: parsed.error.message });
      }

      const result = await authService.verifyOtp(
        parsed.data.phone,
        parsed.data.otp,
        parsed.data.deviceInfo
      );

      if (!result.success) {
        return reply.status(401).send(result);
      }

      return reply.send(result);
    }
  );

  // Protected routes
  fastify.register(async (protectedRoutes) => {
    protectedRoutes.addHook('onRequest', authMiddleware);

    // Get current user
    protectedRoutes.get('/user/me', async (request, reply) => {
      const user = await prisma.user.findUnique({
        where: { id: request.userId },
        include: {
          consent: true,
          devices: {
            orderBy: { lastSeenAt: 'desc' },
            take: 5,
          },
        },
      });

      if (!user) {
        return reply.status(404).send({ error: 'User not found' });
      }

      return reply.send({
        id: user.id,
        country: user.country,
        timezone: user.timezone,
        createdAt: user.createdAt.toISOString(),
        whatsappOptedIn: !!user.whatsappOptInAt,
        consent: user.consent
          ? {
              cloudAiEnabled: user.consent.cloudAiEnabled,
              uploadRawEnabled: user.consent.uploadRawEnabled,
              whatsappEnabled: user.consent.whatsappEnabled,
            }
          : null,
        devices: user.devices.map((d) => ({
          id: d.id,
          platform: d.platform,
          lastSeenAt: d.lastSeenAt.toISOString(),
        })),
      });
    });

    // Update consent
    protectedRoutes.put<{ Body: UpdateConsentInput }>(
      '/user/consent',
      async (request, reply) => {
        const parsed = updateConsentSchema.safeParse(request.body);
        if (!parsed.success) {
          return reply.status(400).send({ error: parsed.error.message });
        }

        const consent = await prisma.consent.upsert({
          where: { userId: request.userId! },
          create: {
            userId: request.userId!,
            ...parsed.data,
          },
          update: parsed.data,
        });

        return reply.send({
          cloudAiEnabled: consent.cloudAiEnabled,
          uploadRawEnabled: consent.uploadRawEnabled,
          whatsappEnabled: consent.whatsappEnabled,
        });
      }
    );

    // Opt-in WhatsApp
    protectedRoutes.post<{ Body: OptInWhatsappInput }>(
      '/user/opt-in-whatsapp',
      async (request, reply) => {
        const parsed = optInWhatsappSchema.safeParse(request.body);
        if (!parsed.success) {
          return reply.status(400).send({ error: parsed.error.message });
        }

        // Verify consent for WhatsApp is enabled
        const consent = await prisma.consent.findUnique({
          where: { userId: request.userId! },
        });

        if (!consent?.whatsappEnabled) {
          return reply.status(400).send({
            error: 'Please enable WhatsApp in privacy settings first',
          });
        }

        await prisma.user.update({
          where: { id: request.userId! },
          data: {
            whatsappE164: parsed.data.whatsappNumber,
            whatsappOptInAt: new Date(),
          },
        });

        return reply.send({ success: true });
      }
    );

    // Delete user
    protectedRoutes.delete('/user/delete', async (request, reply) => {
      await authService.deleteUser(request.userId!);
      return reply.send({ success: true });
    });

    // Export user data
    protectedRoutes.get('/user/export', async (request, reply) => {
      const user = await prisma.user.findUnique({
        where: { id: request.userId! },
        include: {
          consent: true,
          transactions: {
            orderBy: { occurredAt: 'desc' },
            take: 1000,
          },
          weeklySummaries: {
            orderBy: { weekStart: 'desc' },
            take: 52,
          },
        },
      });

      if (!user) {
        return reply.status(404).send({ error: 'User not found' });
      }

      const exportData = {
        exportedAt: new Date().toISOString(),
        user: {
          id: user.id,
          country: user.country,
          timezone: user.timezone,
          createdAt: user.createdAt.toISOString(),
        },
        consent: user.consent,
        transactions: user.transactions.map((tx) => ({
          id: tx.id,
          occurredAt: tx.occurredAt.toISOString(),
          amount: tx.amount.toNumber(),
          currency: tx.currency,
          direction: tx.direction,
          merchant: tx.merchant,
          payee: tx.payee,
          category: tx.category,
          instrument: tx.instrument,
        })),
        weeklySummaries: user.weeklySummaries.map((s) => ({
          weekStart: s.weekStart.toISOString(),
          weekEnd: s.weekEnd.toISOString(),
          totals: s.totalsJson,
          categories: s.categoriesJson,
        })),
      };

      reply.header('Content-Type', 'application/json');
      reply.header(
        'Content-Disposition',
        `attachment; filename="spendmanager-export-${new Date().toISOString().split('T')[0]}.json"`
      );

      return reply.send(exportData);
    });
  });
}
