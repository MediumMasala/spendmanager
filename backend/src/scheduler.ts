import cron from 'node-cron';
import { summaryService } from './services/summary.js';
import { whatsappService } from './services/whatsapp.js';
import { eventService } from './services/events.js';
import { parseCache } from './llm/cache.js';
import { prisma } from './db.js';

export function startScheduler(): void {
  console.log('Starting scheduler...');

  // Weekly summary job - runs every Sunday at 8 PM IST (2:30 PM UTC)
  cron.schedule('30 14 * * 0', async () => {
    console.log('Running weekly summary job...');

    try {
      const users = await summaryService.getUsersForWeeklySummary();
      console.log(`Processing ${users.length} users for weekly summary`);

      for (const { userId, whatsappE164 } of users) {
        try {
          // First, parse any pending events
          await eventService.parsePendingEvents(userId, 100);

          // Compute summary
          const summary = await summaryService.computeWeeklySummary(userId);

          // Send WhatsApp
          const result = await whatsappService.sendWeeklySummary(
            userId,
            whatsappE164,
            summary
          );

          if (result.success) {
            console.log(`Weekly summary sent to user ${userId}`);
          } else {
            console.error(
              `Failed to send summary to ${userId}:`,
              result.error
            );
          }
        } catch (error) {
          console.error(`Error processing user ${userId}:`, error);
        }
      }

      console.log('Weekly summary job completed');
    } catch (error) {
      console.error('Weekly summary job failed:', error);
    }
  });

  // Daily cleanup job - runs at 3 AM IST (9:30 PM UTC previous day)
  cron.schedule('30 21 * * *', async () => {
    console.log('Running daily cleanup job...');

    try {
      // Clean old parse cache entries
      const deletedCache = await parseCache.cleanup(30);
      console.log(`Cleaned ${deletedCache} old cache entries`);

      // Clean old OTP requests
      const deletedOtps = await prisma.otpRequest.deleteMany({
        where: {
          expiresAt: { lt: new Date() },
        },
      });
      console.log(`Cleaned ${deletedOtps.count} expired OTP requests`);

      console.log('Daily cleanup completed');
    } catch (error) {
      console.error('Daily cleanup failed:', error);
    }
  });

  // Hourly parse job - process pending events
  cron.schedule('0 * * * *', async () => {
    console.log('Running hourly parse job...');

    try {
      // Get users with pending events
      const usersWithPending = await prisma.event.groupBy({
        by: ['userId'],
        where: { parseStatus: 'PENDING' },
        _count: { id: true },
        orderBy: { userId: 'asc' },
        take: 50,
      });

      for (const group of usersWithPending) {
        const userId = group.userId;
        try {
          const result = await eventService.parsePendingEvents(userId, 20);
          console.log(
            `User ${userId}: parsed ${result.processed}, failed ${result.failed}`
          );
        } catch (error) {
          console.error(`Error parsing events for ${userId}:`, error);
        }
      }

      console.log('Hourly parse job completed');
    } catch (error) {
      console.error('Hourly parse job failed:', error);
    }
  });

  console.log('Scheduler started with jobs:');
  console.log('  - Weekly summary: Sunday 8 PM IST');
  console.log('  - Daily cleanup: 3 AM IST');
  console.log('  - Hourly parse: Every hour');
}
