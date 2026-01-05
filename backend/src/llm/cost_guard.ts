import { config } from '../config.js';
import { redis, incrementCounter, getCache, setCache } from '../redis.js';
import { prisma } from '../db.js';
import { BudgetExceededError } from './provider.js';

// Token to USD cost estimates (approximate for gpt-4o-mini)
const COST_PER_1K_INPUT_TOKENS = 0.00015;
const COST_PER_1K_OUTPUT_TOKENS = 0.0006;

interface CircuitBreakerState {
  failures: number;
  lastFailure: number;
  open: boolean;
}

const circuitBreakers = new Map<string, CircuitBreakerState>();

export class CostGuard {
  private dailyBudgetUsd: number;
  private perUserDailyBudgetUsd: number;
  private circuitBreakerThreshold: number;
  private circuitBreakerTimeoutMs: number;

  constructor() {
    this.dailyBudgetUsd = config.LLM_DAILY_BUDGET_USD;
    this.perUserDailyBudgetUsd = config.LLM_PER_USER_DAILY_BUDGET_USD;
    this.circuitBreakerThreshold = config.LLM_CIRCUIT_BREAKER_THRESHOLD;
    this.circuitBreakerTimeoutMs = config.LLM_CIRCUIT_BREAKER_TIMEOUT_MS;
  }

  async checkBudget(userId: string, provider: string): Promise<void> {
    // Check circuit breaker first
    this.checkCircuitBreaker(provider);

    const today = this.getTodayKey();

    // Check global daily budget
    const globalKey = `llm:budget:global:${today}`;
    const globalSpend = await this.getSpend(globalKey);

    if (globalSpend >= this.dailyBudgetUsd) {
      throw new BudgetExceededError(provider, 'daily');
    }

    // Check per-user daily budget
    const userKey = `llm:budget:user:${userId}:${today}`;
    const userSpend = await this.getSpend(userKey);

    if (userSpend >= this.perUserDailyBudgetUsd) {
      throw new BudgetExceededError(provider, 'user');
    }
  }

  async recordUsage(
    userId: string,
    provider: string,
    model: string,
    operation: string,
    inputTokens: number,
    outputTokens: number
  ): Promise<void> {
    const cost = this.calculateCost(inputTokens, outputTokens);
    const today = this.getTodayKey();

    // Update Redis counters
    const globalKey = `llm:budget:global:${today}`;
    const userKey = `llm:budget:user:${userId}:${today}`;

    await Promise.all([
      this.incrementSpend(globalKey, cost),
      this.incrementSpend(userKey, cost),
    ]);

    // Record in database for analytics
    await prisma.tokenUsage.create({
      data: {
        userId,
        provider,
        model,
        operation,
        inputTokens,
        outputTokens,
        costUsd: cost,
      },
    });

    // Reset circuit breaker on success
    this.resetCircuitBreaker(provider);
  }

  recordFailure(provider: string): void {
    const state = circuitBreakers.get(provider) ?? {
      failures: 0,
      lastFailure: 0,
      open: false,
    };

    state.failures++;
    state.lastFailure = Date.now();

    if (state.failures >= this.circuitBreakerThreshold) {
      state.open = true;
      console.warn(`Circuit breaker opened for ${provider}`);
    }

    circuitBreakers.set(provider, state);
  }

  private checkCircuitBreaker(provider: string): void {
    const state = circuitBreakers.get(provider);
    if (!state?.open) return;

    const elapsed = Date.now() - state.lastFailure;
    if (elapsed > this.circuitBreakerTimeoutMs) {
      // Half-open: allow one request through
      state.open = false;
      state.failures = 0;
      console.info(`Circuit breaker half-open for ${provider}`);
    } else {
      throw new Error(`Circuit breaker open for ${provider}`);
    }
  }

  private resetCircuitBreaker(provider: string): void {
    circuitBreakers.delete(provider);
  }

  private calculateCost(inputTokens: number, outputTokens: number): number {
    return (
      (inputTokens / 1000) * COST_PER_1K_INPUT_TOKENS +
      (outputTokens / 1000) * COST_PER_1K_OUTPUT_TOKENS
    );
  }

  private getTodayKey(): string {
    return new Date().toISOString().split('T')[0]!;
  }

  private async getSpend(key: string): Promise<number> {
    const value = await redis.get(key);
    return value ? parseFloat(value) : 0;
  }

  private async incrementSpend(key: string, amount: number): Promise<void> {
    await redis.incrbyfloat(key, amount);
    // Set TTL of 2 days for cleanup
    await redis.expire(key, 2 * 24 * 60 * 60);
  }

  async getDailyStats(): Promise<{
    globalSpend: number;
    globalBudget: number;
    remaining: number;
  }> {
    const today = this.getTodayKey();
    const globalKey = `llm:budget:global:${today}`;
    const globalSpend = await this.getSpend(globalKey);

    return {
      globalSpend,
      globalBudget: this.dailyBudgetUsd,
      remaining: Math.max(0, this.dailyBudgetUsd - globalSpend),
    };
  }

  async getUserDailyStats(userId: string): Promise<{
    userSpend: number;
    userBudget: number;
    remaining: number;
  }> {
    const today = this.getTodayKey();
    const userKey = `llm:budget:user:${userId}:${today}`;
    const userSpend = await this.getSpend(userKey);

    return {
      userSpend,
      userBudget: this.perUserDailyBudgetUsd,
      remaining: Math.max(0, this.perUserDailyBudgetUsd - userSpend),
    };
  }
}

export const costGuard = new CostGuard();
