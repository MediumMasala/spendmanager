import type { ParsedTransaction, CategoryResult } from './schemas.js';

export interface ParseContext {
  appSource: string;
  locale: string;
  timezone: string;
  postedAt: string;
}

export interface LlmUsage {
  inputTokens: number;
  outputTokens: number;
  model: string;
}

export interface ParseResult {
  transaction: ParsedTransaction;
  usage: LlmUsage;
  cached: boolean;
}

export interface CategoryResultWithUsage {
  result: CategoryResult;
  usage: LlmUsage;
  cached: boolean;
}

export interface LlmProvider {
  readonly name: string;

  parseTransaction(
    text: string,
    context: ParseContext
  ): Promise<ParseResult>;

  categorize(
    merchant: string | null,
    payee: string | null,
    amount: number,
    direction: 'DEBIT' | 'CREDIT'
  ): Promise<CategoryResultWithUsage>;

  healthCheck(): Promise<boolean>;
}

export class LlmProviderError extends Error {
  constructor(
    message: string,
    public readonly provider: string,
    public readonly retryable: boolean = false,
    public readonly statusCode?: number
  ) {
    super(message);
    this.name = 'LlmProviderError';
  }
}

export class RateLimitError extends LlmProviderError {
  constructor(provider: string, retryAfterMs?: number) {
    super(`Rate limit exceeded for ${provider}`, provider, true);
    this.name = 'RateLimitError';
    this.retryAfterMs = retryAfterMs;
  }
  retryAfterMs?: number;
}

export class BudgetExceededError extends LlmProviderError {
  constructor(provider: string, budgetType: 'daily' | 'user') {
    super(`${budgetType} budget exceeded for ${provider}`, provider, false);
    this.name = 'BudgetExceededError';
  }
}
