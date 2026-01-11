import type { FastifyInstance } from 'fastify';
import { prisma } from '../db.js';
import { authMiddleware } from '../middleware/auth.js';
import { notificationService } from '../services/notifications.js';
import { config } from '../config.js';

export async function notificationRoutes(fastify: FastifyInstance): Promise<void> {
  // Protected routes - require user auth
  fastify.register(async (protectedRoutes) => {
    protectedRoutes.addHook('onRequest', authMiddleware);

    // Update permission status for current device
    protectedRoutes.put<{
      Body: {
        notificationPermission: boolean;
        deviceId?: string;
      };
    }>(
      '/user/permission-status',
      {
        schema: {
          body: {
            type: 'object',
            required: ['notificationPermission'],
            properties: {
              notificationPermission: { type: 'boolean' },
              deviceId: { type: 'string' },
            },
          },
        },
      },
      async (request, reply) => {
        const { notificationPermission, deviceId } = request.body;
        const userId = request.userId!;

        try {
          if (deviceId) {
            await prisma.device.updateMany({
              where: { id: deviceId, userId },
              data: {
                notificationPermission,
                permissionUpdatedAt: new Date(),
                lastSeenAt: new Date(),
              },
            });
          } else {
            const device = await prisma.device.findFirst({
              where: { userId },
              orderBy: { lastSeenAt: 'desc' },
            });

            if (device) {
              await prisma.device.update({
                where: { id: device.id },
                data: {
                  notificationPermission,
                  permissionUpdatedAt: new Date(),
                  lastSeenAt: new Date(),
                },
              });
            }
          }

          return reply.send({ success: true });
        } catch (error: any) {
          console.error('[Permission] Failed to update status:', error);
          return reply.status(500).send({
            success: false,
            error: 'Failed to update permission status',
          });
        }
      }
    );

    // Update FCM token for current device
    protectedRoutes.put<{
      Body: {
        fcmToken: string;
        deviceId?: string;
      };
    }>(
      '/user/fcm-token',
      {
        schema: {
          body: {
            type: 'object',
            required: ['fcmToken'],
            properties: {
              fcmToken: { type: 'string' },
              deviceId: { type: 'string' },
            },
          },
        },
      },
      async (request, reply) => {
        const { fcmToken, deviceId } = request.body;
        const userId = request.userId!;

        try {
          // If deviceId provided, update that specific device
          if (deviceId) {
            await prisma.device.updateMany({
              where: {
                id: deviceId,
                userId,
              },
              data: {
                deviceToken: fcmToken,
                lastSeenAt: new Date(),
              },
            });
          } else {
            // Otherwise update the most recent device for this user
            const device = await prisma.device.findFirst({
              where: { userId },
              orderBy: { lastSeenAt: 'desc' },
            });

            if (device) {
              await prisma.device.update({
                where: { id: device.id },
                data: {
                  deviceToken: fcmToken,
                  lastSeenAt: new Date(),
                },
              });
            }
          }

          return reply.send({ success: true });
        } catch (error: any) {
          console.error('[FCM] Failed to update token:', error);
          return reply.status(500).send({
            success: false,
            error: 'Failed to update FCM token',
          });
        }
      }
    );
  });

  // Admin routes - require admin API key
  fastify.register(async (adminRoutes) => {
    // Admin auth middleware
    adminRoutes.addHook('onRequest', async (request, reply) => {
      const apiKey = request.headers['x-admin-api-key'];

      if (!apiKey || apiKey !== config.ADMIN_API_KEY) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }
    });

    // Send notification to specific user
    adminRoutes.post<{
      Body: {
        userId: string;
        title: string;
        body: string;
        data?: Record<string, string>;
      };
    }>(
      '/admin/notifications/send-to-user',
      {
        schema: {
          body: {
            type: 'object',
            required: ['userId', 'title', 'body'],
            properties: {
              userId: { type: 'string' },
              title: { type: 'string' },
              body: { type: 'string' },
              data: { type: 'object' },
            },
          },
        },
      },
      async (request, reply) => {
        const { userId, title, body, data } = request.body;

        const result = await notificationService.sendToUser(userId, {
          title,
          body,
          data,
        });

        return reply.send(result);
      }
    );

    // Send notification to all users (broadcast)
    adminRoutes.post<{
      Body: {
        title: string;
        body: string;
        data?: Record<string, string>;
      };
    }>(
      '/admin/notifications/broadcast',
      {
        schema: {
          body: {
            type: 'object',
            required: ['title', 'body'],
            properties: {
              title: { type: 'string' },
              body: { type: 'string' },
              data: { type: 'object' },
            },
          },
        },
      },
      async (request, reply) => {
        const { title, body, data } = request.body;

        const result = await notificationService.sendToAll({
          title,
          body,
          data,
        });

        return reply.send(result);
      }
    );

    // Send permission reminder to user
    adminRoutes.post<{
      Body: {
        userId: string;
      };
    }>(
      '/admin/notifications/permission-reminder',
      {
        schema: {
          body: {
            type: 'object',
            required: ['userId'],
            properties: {
              userId: { type: 'string' },
            },
          },
        },
      },
      async (request, reply) => {
        const { userId } = request.body;

        const result = await notificationService.sendPermissionReminder(userId);

        return reply.send(result);
      }
    );

    // Get notification permission stats
    adminRoutes.get('/admin/notifications/stats', async (request, reply) => {
      const totalDevices = await prisma.device.count();
      const permissionEnabled = await prisma.device.count({
        where: { notificationPermission: true },
      });
      const withFcmToken = await prisma.device.count({
        where: { deviceToken: { not: null } },
      });
      const totalUsers = await prisma.user.count();

      return reply.send({
        totalUsers,
        totalDevices,
        permissionEnabled,
        permissionDisabled: totalDevices - permissionEnabled,
        withFcmToken,
        permissionRate: totalDevices > 0
          ? Math.round((permissionEnabled / totalDevices) * 100)
          : 0,
      });
    });

    // Get recent captured events (for admin dashboard)
    // Query params: ?limit=50&userId=optional
    adminRoutes.get<{
      Querystring: { limit?: string; userId?: string };
    }>('/admin/events', async (request, reply) => {
      const limit = Math.min(parseInt(request.query.limit || '50'), 200);
      const userId = request.query.userId;

      const events = await prisma.event.findMany({
        where: userId ? { userId } : undefined,
        orderBy: { postedAt: 'desc' },
        take: limit,
        include: {
          user: {
            select: {
              id: true,
              whatsappE164: true,
              consent: {
                select: {
                  cloudAiEnabled: true,
                },
              },
            },
          },
          transaction: {
            select: {
              id: true,
              amount: true,
              direction: true,
              merchant: true,
              category: true,
            },
          },
        },
      });

      const totalEvents = await prisma.event.count();
      const todayEvents = await prisma.event.count({
        where: {
          postedAt: {
            gte: new Date(new Date().setHours(0, 0, 0, 0)),
          },
        },
      });
      const parsedEvents = await prisma.event.count({
        where: { parseStatus: 'PARSED' },
      });
      const pendingEvents = await prisma.event.count({
        where: { parseStatus: 'PENDING' },
      });

      return reply.send({
        events: events.map((e) => ({
          id: e.id,
          userId: e.userId,
          phone: e.user?.whatsappE164 || 'N/A',
          cloudAiEnabled: e.user?.consent?.cloudAiEnabled || false,
          appSource: e.appSource,
          postedAt: e.postedAt.toISOString(),
          textRedacted: e.textRedacted,
          hasRawText: !!e.textRaw,
          parseStatus: e.parseStatus,
          parseError: e.parseError,
          transaction: e.transaction ? {
            amount: Number(e.transaction.amount),
            direction: e.transaction.direction,
            merchant: e.transaction.merchant,
            category: e.transaction.category,
          } : null,
          createdAt: e.createdAt.toISOString(),
        })),
        stats: {
          total: totalEvents,
          today: todayEvents,
          parsed: parsedEvents,
          pending: pendingEvents,
          returned: events.length,
        },
      });
    });

    // Get all users with FCM tokens (for admin dashboard)
    // Query params: ?filter=all|permission_enabled|permission_disabled
    adminRoutes.get<{
      Querystring: { filter?: string };
    }>('/admin/notifications/users', async (request, reply) => {
      const filter = request.query.filter || 'all';

      let deviceWhere: any = { deviceToken: { not: null } };
      let userDeviceWhere: any = { some: { deviceToken: { not: null } } };

      if (filter === 'permission_enabled') {
        deviceWhere = { deviceToken: { not: null }, notificationPermission: true };
        userDeviceWhere = { some: { deviceToken: { not: null }, notificationPermission: true } };
      } else if (filter === 'permission_disabled') {
        deviceWhere = { deviceToken: { not: null }, notificationPermission: false };
        userDeviceWhere = { some: { deviceToken: { not: null }, notificationPermission: false } };
      }

      const users = await prisma.user.findMany({
        where: {
          devices: userDeviceWhere,
        },
        select: {
          id: true,
          createdAt: true,
          whatsappE164: true,
          devices: {
            where: deviceWhere,
            select: {
              id: true,
              platform: true,
              lastSeenAt: true,
              notificationPermission: true,
              permissionUpdatedAt: true,
            },
          },
        },
      });

      return reply.send({
        users: users.map((u) => ({
          id: u.id,
          phone: u.whatsappE164 || 'N/A',
          createdAt: u.createdAt.toISOString(),
          deviceCount: u.devices.length,
          notificationEnabled: u.devices.some(d => d.notificationPermission),
          devices: u.devices.map((d) => ({
            id: d.id,
            platform: d.platform,
            lastSeenAt: d.lastSeenAt.toISOString(),
            notificationPermission: d.notificationPermission,
            permissionUpdatedAt: d.permissionUpdatedAt?.toISOString() || null,
          })),
        })),
        totalUsers: users.length,
      });
    });
  });
}
