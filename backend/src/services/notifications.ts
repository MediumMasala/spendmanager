import admin from 'firebase-admin';
import { prisma } from '../db.js';

interface SendNotificationOptions {
  title: string;
  body: string;
  data?: Record<string, string>;
  imageUrl?: string;
}

interface NotificationResult {
  success: boolean;
  successCount: number;
  failureCount: number;
  errors?: string[];
}

/**
 * Notification service for sending push notifications via FCM.
 */
export class NotificationService {
  /**
   * Send notification to a specific user (all their devices)
   */
  async sendToUser(
    userId: string,
    options: SendNotificationOptions
  ): Promise<NotificationResult> {
    const devices = await prisma.device.findMany({
      where: {
        userId,
        deviceToken: { not: null },
      },
      select: { deviceToken: true },
    });

    const tokens = devices
      .map((d) => d.deviceToken)
      .filter((t): t is string => t !== null);

    if (tokens.length === 0) {
      return {
        success: false,
        successCount: 0,
        failureCount: 0,
        errors: ['No devices with FCM tokens found for user'],
      };
    }

    return this.sendToTokens(tokens, options);
  }

  /**
   * Send notification to multiple users
   */
  async sendToUsers(
    userIds: string[],
    options: SendNotificationOptions
  ): Promise<NotificationResult> {
    const devices = await prisma.device.findMany({
      where: {
        userId: { in: userIds },
        deviceToken: { not: null },
      },
      select: { deviceToken: true },
    });

    const tokens = devices
      .map((d) => d.deviceToken)
      .filter((t): t is string => t !== null);

    if (tokens.length === 0) {
      return {
        success: false,
        successCount: 0,
        failureCount: 0,
        errors: ['No devices with FCM tokens found'],
      };
    }

    return this.sendToTokens(tokens, options);
  }

  /**
   * Send notification to all users (broadcast)
   */
  async sendToAll(options: SendNotificationOptions): Promise<NotificationResult> {
    const devices = await prisma.device.findMany({
      where: {
        deviceToken: { not: null },
      },
      select: { deviceToken: true },
    });

    const tokens = devices
      .map((d) => d.deviceToken)
      .filter((t): t is string => t !== null);

    if (tokens.length === 0) {
      return {
        success: false,
        successCount: 0,
        failureCount: 0,
        errors: ['No devices with FCM tokens found'],
      };
    }

    return this.sendToTokens(tokens, options);
  }

  /**
   * Send permission reminder to users who have disabled notification listener
   */
  async sendPermissionReminder(userId: string): Promise<NotificationResult> {
    return this.sendToUser(userId, {
      title: 'Enable Notification Access',
      body: 'SpendManager needs notification access to track your transactions. Tap to enable.',
      data: {
        type: 'permission_reminder',
        navigate_to: 'setup',
      },
    });
  }

  /**
   * Send weekly summary notification
   */
  async sendWeeklySummaryNotification(
    userId: string,
    summary: { totalSpent: number; transactionCount: number }
  ): Promise<NotificationResult> {
    const formattedAmount = new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(summary.totalSpent);

    return this.sendToUser(userId, {
      title: 'Weekly Summary Ready',
      body: `You spent ${formattedAmount} across ${summary.transactionCount} transactions this week. Tap to see details.`,
      data: {
        type: 'weekly_summary',
        navigate_to: 'summary',
      },
    });
  }

  /**
   * Send notification to specific FCM tokens
   */
  private async sendToTokens(
    tokens: string[],
    options: SendNotificationOptions
  ): Promise<NotificationResult> {
    if (tokens.length === 0) {
      return {
        success: false,
        successCount: 0,
        failureCount: 0,
        errors: ['No tokens provided'],
      };
    }

    try {
      const message: admin.messaging.MulticastMessage = {
        tokens,
        notification: {
          title: options.title,
          body: options.body,
          imageUrl: options.imageUrl,
        },
        data: options.data,
        android: {
          priority: 'high',
          notification: {
            channelId: 'spendmanager_notifications',
            priority: 'high',
          },
        },
      };

      const response = await admin.messaging().sendEachForMulticast(message);

      const errors: string[] = [];
      const invalidTokens: string[] = [];

      response.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error) {
          errors.push(`Token ${idx}: ${resp.error.message}`);

          // Track invalid tokens for cleanup
          if (
            resp.error.code === 'messaging/invalid-registration-token' ||
            resp.error.code === 'messaging/registration-token-not-registered'
          ) {
            invalidTokens.push(tokens[idx]);
          }
        }
      });

      // Clean up invalid tokens
      if (invalidTokens.length > 0) {
        await this.removeInvalidTokens(invalidTokens);
      }

      return {
        success: response.successCount > 0,
        successCount: response.successCount,
        failureCount: response.failureCount,
        errors: errors.length > 0 ? errors : undefined,
      };
    } catch (error: any) {
      console.error('[Notifications] Failed to send:', error);
      return {
        success: false,
        successCount: 0,
        failureCount: tokens.length,
        errors: [error.message],
      };
    }
  }

  /**
   * Remove invalid FCM tokens from database
   */
  private async removeInvalidTokens(tokens: string[]): Promise<void> {
    try {
      await prisma.device.updateMany({
        where: {
          deviceToken: { in: tokens },
        },
        data: {
          deviceToken: null,
        },
      });
      console.log(`[Notifications] Removed ${tokens.length} invalid FCM tokens`);
    } catch (error) {
      console.error('[Notifications] Failed to remove invalid tokens:', error);
    }
  }
}

export const notificationService = new NotificationService();
