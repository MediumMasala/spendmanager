import { prisma } from '../db.js';
import { hashText } from '../utils/crypto.js';
import { llmService } from '../llm/index.js';
import { categorizationService } from './categorization.js';
import type { EventInput } from '../schemas/api.js';
import type { ParseStatus, TransactionDir, CategorySource } from '@prisma/client';

interface IngestResult {
  eventId: string;
  status: 'accepted' | 'duplicate' | 'error';
  error?: string;
}

interface ParseEventResult {
  success: boolean;
  transactionId?: string;
  error?: string;
}

export class EventService {
  /**
   * Ingest batch of events from device.
   */
  async ingestEvents(
    userId: string,
    events: EventInput[]
  ): Promise<IngestResult[]> {
    const results: IngestResult[] = [];

    for (const event of events) {
      try {
        const textHash = hashText(event.textRedacted);

        // Check for duplicate
        const existing = await prisma.event.findFirst({
          where: {
            userId,
            textHash,
            postedAt: {
              gte: new Date(
                new Date(event.postedAt).getTime() - 60 * 60 * 1000
              ), // 1 hour window
            },
          },
        });

        if (existing) {
          results.push({
            eventId: event.eventId,
            status: 'duplicate',
          });
          continue;
        }

        // Create event
        await prisma.event.create({
          data: {
            id: event.eventId,
            userId,
            deviceId: event.deviceId,
            appSource: event.appSource,
            postedAt: new Date(event.postedAt),
            textRedacted: event.textRedacted,
            textRaw: event.textRaw,
            textHash,
            locale: event.locale,
            timezone: event.timezone,
            parseStatus: 'PENDING',
          },
        });

        results.push({
          eventId: event.eventId,
          status: 'accepted',
        });
      } catch (error) {
        results.push({
          eventId: event.eventId,
          status: 'error',
          error: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }

    return results;
  }

  /**
   * Parse a single event and create transaction if valid.
   */
  async parseEvent(eventId: string): Promise<ParseEventResult> {
    const event = await prisma.event.findUnique({
      where: { id: eventId },
      include: { user: { include: { consent: true } } },
    });

    if (!event) {
      return { success: false, error: 'Event not found' };
    }

    if (event.parseStatus !== 'PENDING') {
      return { success: false, error: 'Event already processed' };
    }

    // Check if cloud AI is enabled
    if (!event.user.consent?.cloudAiEnabled) {
      await prisma.event.update({
        where: { id: eventId },
        data: {
          parseStatus: 'SKIPPED',
          parseError: 'Cloud AI not enabled',
        },
      });
      return { success: false, error: 'Cloud AI not enabled for user' };
    }

    try {
      // Use redacted text for parsing (or raw if user opted in)
      const textToParse = event.user.consent.uploadRawEnabled && event.textRaw
        ? event.textRaw
        : event.textRedacted;

      const parseResult = await llmService.parseTransaction(
        event.userId,
        textToParse,
        {
          appSource: event.appSource,
          locale: event.locale,
          timezone: event.timezone,
          postedAt: event.postedAt.toISOString(),
        }
      );

      const parsed = parseResult.transaction;

      // If not a transaction, mark as skipped
      if (!parsed.isTransaction) {
        await prisma.event.update({
          where: { id: eventId },
          data: {
            parseStatus: 'SKIPPED',
            parseConfidence: parsed.confidence,
            parseError: parsed.reason,
          },
        });
        return { success: true };
      }

      // If it's a transaction, categorize and store
      const categoryResult = await categorizationService.categorize(
        event.userId,
        parsed.merchant,
        parsed.payee,
        parsed.amount ?? 0,
        parsed.direction as 'DEBIT' | 'CREDIT'
      );

      // Create transaction
      const transaction = await prisma.transaction.create({
        data: {
          userId: event.userId,
          eventId,
          occurredAt: parsed.occurredAt
            ? new Date(parsed.occurredAt)
            : event.postedAt,
          amount: parsed.amount ?? 0,
          currency: parsed.currency,
          direction: parsed.direction as TransactionDir,
          instrument: parsed.instrument,
          merchant: parsed.merchant,
          payee: parsed.payee,
          bankHint: parsed.bankHint,
          refId: parsed.referenceId,
          category: categoryResult.category,
          categorySource: categoryResult.source.toUpperCase() as CategorySource,
          rawTokens: event.user.consent.uploadRawEnabled
            ? JSON.stringify({
                merchant: parsed.merchant,
                payee: parsed.payee,
              })
            : null,
          confidence: parsed.confidence,
        },
      });

      // Update event status
      await prisma.event.update({
        where: { id: eventId },
        data: {
          parseStatus: 'PARSED',
          parseConfidence: parsed.confidence,
        },
      });

      return { success: true, transactionId: transaction.id };
    } catch (error) {
      await prisma.event.update({
        where: { id: eventId },
        data: {
          parseStatus: 'FAILED',
          parseError: error instanceof Error ? error.message : 'Unknown error',
        },
      });

      return {
        success: false,
        error: error instanceof Error ? error.message : 'Parse failed',
      };
    }
  }

  /**
   * Parse all pending events for a user.
   */
  async parsePendingEvents(
    userId: string,
    limit: number = 50
  ): Promise<{ processed: number; failed: number }> {
    const pendingEvents = await prisma.event.findMany({
      where: {
        userId,
        parseStatus: 'PENDING',
      },
      orderBy: { postedAt: 'asc' },
      take: limit,
    });

    let processed = 0;
    let failed = 0;

    for (const event of pendingEvents) {
      const result = await this.parseEvent(event.id);
      if (result.success) {
        processed++;
      } else {
        failed++;
      }
    }

    return { processed, failed };
  }

  /**
   * Retry failed events.
   */
  async retryFailedEvents(userId: string): Promise<{ retried: number }> {
    const failedEvents = await prisma.event.updateMany({
      where: {
        userId,
        parseStatus: 'FAILED',
      },
      data: {
        parseStatus: 'PENDING',
        parseError: null,
      },
    });

    return { retried: failedEvents.count };
  }

  /**
   * Get recent transactions for user.
   */
  async getRecentTransactions(
    userId: string,
    limit: number = 20,
    offset: number = 0
  ) {
    const transactions = await prisma.transaction.findMany({
      where: { userId },
      orderBy: { occurredAt: 'desc' },
      take: limit,
      skip: offset,
      include: {
        event: {
          select: {
            appSource: true,
            postedAt: true,
          },
        },
      },
    });

    return transactions.map((tx) => ({
      id: tx.id,
      occurredAt: tx.occurredAt.toISOString(),
      amount: tx.amount.toNumber(),
      currency: tx.currency,
      direction: tx.direction,
      instrument: tx.instrument,
      merchant: tx.merchant,
      payee: tx.payee,
      category: tx.category,
      appSource: tx.event.appSource,
    }));
  }
}

export const eventService = new EventService();
