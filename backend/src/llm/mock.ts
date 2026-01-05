import {
  type LlmProvider,
  type ParseContext,
  type ParseResult,
  type CategoryResultWithUsage,
} from './provider.js';
import type { ParsedTransaction, CategoryResult } from './schemas.js';

/**
 * Mock provider for testing and development.
 * Uses simple heuristics to simulate parsing.
 */
export class MockProvider implements LlmProvider {
  readonly name = 'mock';
  private delay: number;

  constructor(delayMs: number = 100) {
    this.delay = delayMs;
  }

  async parseTransaction(
    text: string,
    context: ParseContext
  ): Promise<ParseResult> {
    await this.simulateDelay();

    const lowerText = text.toLowerCase();

    // Simple heuristics for mock
    const isTransaction =
      /(?:rs\.?|inr|₹)\s*[\d,]+/.test(lowerText) &&
      /(?:debited|credited|paid|received|sent|transferred)/.test(lowerText);

    if (!isTransaction) {
      return {
        transaction: {
          isTransaction: false,
          amount: null,
          currency: 'INR',
          direction: null,
          occurredAt: null,
          merchant: null,
          payee: null,
          instrument: null,
          bankHint: null,
          appHint: context.appSource,
          referenceId: null,
          confidence: 0.9,
          flags: [],
          reason: 'Not a financial transaction',
        },
        usage: { inputTokens: 50, outputTokens: 30, model: 'mock' },
        cached: false,
      };
    }

    // Extract amount
    const amountMatch = text.match(/(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{2})?)/i);
    const amount = amountMatch
      ? parseFloat(amountMatch[1]!.replace(/,/g, ''))
      : null;

    // Determine direction
    const isDebit = /(?:debited|paid|sent|transferred to|purchase)/.test(
      lowerText
    );
    const direction = isDebit ? 'DEBIT' : 'CREDIT';

    // Extract merchant/payee (simplified)
    let merchant: string | null = null;
    let payee: string | null = null;

    const toMatch = text.match(/(?:to|at)\s+([A-Za-z][A-Za-z0-9\s]+?)(?:\s+on|\s+ref|\.|\s*$)/i);
    if (toMatch) {
      if (direction === 'DEBIT') {
        merchant = toMatch[1]!.trim();
      } else {
        payee = toMatch[1]!.trim();
      }
    }

    // Determine instrument
    let instrument: ParsedTransaction['instrument'] = null;
    if (/upi|@/.test(lowerText)) instrument = 'UPI';
    else if (/card|debit card|credit card/.test(lowerText)) instrument = 'CARD';
    else if (/neft/.test(lowerText)) instrument = 'NEFT';
    else if (/imps/.test(lowerText)) instrument = 'IMPS';
    else if (/wallet/.test(lowerText)) instrument = 'WALLET';

    return {
      transaction: {
        isTransaction: true,
        amount,
        currency: 'INR',
        direction,
        occurredAt: context.postedAt,
        merchant,
        payee,
        instrument,
        bankHint: this.extractBank(text),
        appHint: context.appSource,
        referenceId: this.extractRefId(text),
        confidence: 0.7,
        flags: [],
        reason: null,
      },
      usage: { inputTokens: 50, outputTokens: 80, model: 'mock' },
      cached: false,
    };
  }

  async categorize(
    merchant: string | null,
    payee: string | null,
    amount: number,
    direction: 'DEBIT' | 'CREDIT'
  ): Promise<CategoryResultWithUsage> {
    await this.simulateDelay();

    const name = (merchant || payee || '').toLowerCase();
    let category = 'Other';
    let subcategory: string | null = null;

    // Simple keyword matching
    if (/swiggy|zomato|restaurant|cafe|food/.test(name)) {
      category = 'Food & Dining';
    } else if (/amazon|flipkart|myntra|shop/.test(name)) {
      category = 'Shopping';
    } else if (/uber|ola|metro|fuel|petrol/.test(name)) {
      category = 'Transport';
    } else if (/netflix|spotify|hotstar|prime/.test(name)) {
      category = 'Entertainment';
      subcategory = 'Streaming';
    } else if (/bigbasket|dmart|grocery|reliance fresh/.test(name)) {
      category = 'Groceries';
    } else if (/electricity|water|gas|internet|jio|airtel/.test(name)) {
      category = 'Utilities';
    } else if (direction === 'CREDIT' && amount > 10000) {
      category = 'Salary';
    }

    return {
      result: {
        category,
        subcategory,
        confidence: 0.6,
      },
      usage: { inputTokens: 20, outputTokens: 20, model: 'mock' },
      cached: false,
    };
  }

  async healthCheck(): Promise<boolean> {
    return true;
  }

  private async simulateDelay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, this.delay));
  }

  private extractBank(text: string): string | null {
    const banks = ['hdfc', 'icici', 'sbi', 'axis', 'kotak', 'yes bank', 'pnb'];
    const lower = text.toLowerCase();
    for (const bank of banks) {
      if (lower.includes(bank)) {
        return bank.toUpperCase();
      }
    }
    return null;
  }

  private extractRefId(text: string): string | null {
    const match = text.match(/(?:ref(?:erence)?|txn|utr)[:\s#]*([A-Z0-9]{8,})/i);
    return match ? match[1]! : null;
  }
}
