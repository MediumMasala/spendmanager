import { prisma } from '../db.js';
import { Decimal } from '@prisma/client/runtime/library';

interface WeekRange {
  start: Date;
  end: Date;
}

interface CategoryTotal {
  category: string;
  total: number;
  count: number;
}

interface MerchantTotal {
  merchant: string;
  total: number;
  count: number;
}

interface SummaryTotals {
  totalSpent: number;
  totalReceived: number;
  netFlow: number;
  transactionCount: number;
}

export interface WeeklySummaryData {
  weekStart: string;
  weekEnd: string;
  totals: SummaryTotals;
  topMerchants: MerchantTotal[];
  categories: CategoryTotal[];
  subscriptions: MerchantTotal[];
  transactionCount: number;
}

export class SummaryService {
  /**
   * Get the current week's date range (Monday to Sunday, IST).
   */
  getCurrentWeekRange(): WeekRange {
    const now = new Date();
    // Adjust for IST (UTC+5:30)
    const istOffset = 5.5 * 60 * 60 * 1000;
    const istNow = new Date(now.getTime() + istOffset);

    // Get Monday of current week
    const dayOfWeek = istNow.getUTCDay();
    const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
    const monday = new Date(istNow);
    monday.setUTCDate(monday.getUTCDate() + mondayOffset);
    monday.setUTCHours(0, 0, 0, 0);

    // Get Sunday
    const sunday = new Date(monday);
    sunday.setUTCDate(sunday.getUTCDate() + 6);
    sunday.setUTCHours(23, 59, 59, 999);

    // Convert back to UTC
    return {
      start: new Date(monday.getTime() - istOffset),
      end: new Date(sunday.getTime() - istOffset),
    };
  }

  /**
   * Compute weekly summary for a user.
   */
  async computeWeeklySummary(
    userId: string,
    weekStart?: Date,
    weekEnd?: Date
  ): Promise<WeeklySummaryData> {
    const range = weekStart && weekEnd
      ? { start: weekStart, end: weekEnd }
      : this.getCurrentWeekRange();

    // Get all transactions in the week
    const transactions = await prisma.transaction.findMany({
      where: {
        userId,
        occurredAt: {
          gte: range.start,
          lte: range.end,
        },
      },
      orderBy: { occurredAt: 'desc' },
    });

    // Calculate totals
    let totalSpent = 0;
    let totalReceived = 0;

    for (const tx of transactions) {
      const amount = tx.amount.toNumber();
      if (tx.direction === 'DEBIT') {
        totalSpent += amount;
      } else {
        totalReceived += amount;
      }
    }

    // Category breakdown (debits only)
    const categoryMap = new Map<string, { total: number; count: number }>();
    for (const tx of transactions) {
      if (tx.direction === 'DEBIT' && tx.category) {
        const existing = categoryMap.get(tx.category) ?? { total: 0, count: 0 };
        existing.total += tx.amount.toNumber();
        existing.count += 1;
        categoryMap.set(tx.category, existing);
      }
    }

    const categories: CategoryTotal[] = Array.from(categoryMap.entries())
      .map(([category, data]) => ({
        category,
        total: Math.round(data.total * 100) / 100,
        count: data.count,
      }))
      .sort((a, b) => b.total - a.total);

    // Top merchants (debits only)
    const merchantMap = new Map<string, { total: number; count: number }>();
    for (const tx of transactions) {
      if (tx.direction === 'DEBIT' && tx.merchant) {
        const name = tx.merchant.toLowerCase();
        const existing = merchantMap.get(name) ?? { total: 0, count: 0 };
        existing.total += tx.amount.toNumber();
        existing.count += 1;
        merchantMap.set(name, existing);
      }
    }

    const topMerchants: MerchantTotal[] = Array.from(merchantMap.entries())
      .map(([merchant, data]) => ({
        merchant,
        total: Math.round(data.total * 100) / 100,
        count: data.count,
      }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 5);

    // Identify subscriptions (recurring payments to same merchant)
    const subscriptions: MerchantTotal[] = Array.from(merchantMap.entries())
      .filter(([_, data]) => data.count >= 2)
      .map(([merchant, data]) => ({
        merchant,
        total: Math.round(data.total * 100) / 100,
        count: data.count,
      }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 3);

    const summary: WeeklySummaryData = {
      weekStart: range.start.toISOString(),
      weekEnd: range.end.toISOString(),
      totals: {
        totalSpent: Math.round(totalSpent * 100) / 100,
        totalReceived: Math.round(totalReceived * 100) / 100,
        netFlow: Math.round((totalReceived - totalSpent) * 100) / 100,
        transactionCount: transactions.length,
      },
      topMerchants,
      categories,
      subscriptions,
      transactionCount: transactions.length,
    };

    // Store summary
    await prisma.weeklySummary.upsert({
      where: {
        userId_weekStart: {
          userId,
          weekStart: range.start,
        },
      },
      create: {
        userId,
        weekStart: range.start,
        weekEnd: range.end,
        totalsJson: summary.totals,
        topMerchantsJson: summary.topMerchants,
        categoriesJson: summary.categories,
        subscriptionsJson: summary.subscriptions,
        transactionCount: transactions.length,
      },
      update: {
        totalsJson: summary.totals,
        topMerchantsJson: summary.topMerchants,
        categoriesJson: summary.categories,
        subscriptionsJson: summary.subscriptions,
        transactionCount: transactions.length,
      },
    });

    return summary;
  }

  /**
   * Get latest summary for user.
   */
  async getLatestSummary(userId: string): Promise<WeeklySummaryData | null> {
    const summary = await prisma.weeklySummary.findFirst({
      where: { userId },
      orderBy: { weekStart: 'desc' },
    });

    if (!summary) return null;

    return {
      weekStart: summary.weekStart.toISOString(),
      weekEnd: summary.weekEnd.toISOString(),
      totals: summary.totalsJson as unknown as SummaryTotals,
      topMerchants: summary.topMerchantsJson as unknown as MerchantTotal[],
      categories: summary.categoriesJson as unknown as CategoryTotal[],
      subscriptions: (summary.subscriptionsJson as unknown as MerchantTotal[]) ?? [],
      transactionCount: summary.transactionCount,
    };
  }

  /**
   * Get users who opted in for WhatsApp summaries.
   */
  async getUsersForWeeklySummary(): Promise<
    Array<{ userId: string; whatsappE164: string }>
  > {
    const users = await prisma.user.findMany({
      where: {
        deletedAt: null,
        whatsappOptInAt: { not: null },
        whatsappE164: { not: null },
        consent: {
          cloudAiEnabled: true,
          whatsappEnabled: true,
        },
      },
      select: {
        id: true,
        whatsappE164: true,
      },
    });

    return users
      .filter((u) => u.whatsappE164)
      .map((u) => ({
        userId: u.id,
        whatsappE164: u.whatsappE164!,
      }));
  }
}

export const summaryService = new SummaryService();
