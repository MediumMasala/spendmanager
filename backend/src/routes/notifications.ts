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

    // Get all users with FCM tokens (for admin dashboard)
    adminRoutes.get('/admin/notifications/users', async (request, reply) => {
      const users = await prisma.user.findMany({
        where: {
          devices: {
            some: {
              deviceToken: { not: null },
            },
          },
        },
        select: {
          id: true,
          createdAt: true,
          whatsappE164: true,
          devices: {
            where: { deviceToken: { not: null } },
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
