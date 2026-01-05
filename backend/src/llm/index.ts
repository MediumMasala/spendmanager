import { config, isDev } from '../config.js';
import type { LlmProvider, ParseContext, ParseResult, CategoryResultWithUsage } from './provider.js';
import { OpenAIProvider } from './openai.js';
import { AnthropicProvider } from './anthropic.js';
import { MockProvider } from './mock.js';
import { parseCache } from './cache.js';
import { costGuard } from './cost_guard.js';
import { parseWithHeuristics, HEURISTIC_HIGH_CONFIDENCE } from './heuristics.js';
import { hashText } from '../utils/crypto.js';
import type { ParsedTransaction } from './schemas.js';

export * from './provider.js';
export * from './schemas.js';
export { parseCache } from './cache.js';
export { costGuard } from './cost_guard.js';

type ProviderType = 'openai' | 'anthropic' | 'mock';

class LlmService {
  private providers: Map<ProviderType, LlmProvider> = new Map();
  private primaryProvider: ProviderType;

  constructor() {
    // Initialize providers based on config
    if (config.OPENAI_API_KEY) {
      this.providers.set('openai', new OpenAIProvider());
    }

    if (config.ANTHROPIC_API_KEY) {
      this.providers.set('anthropic', new AnthropicProvider());
    }

    // Always have mock for testing
    this.providers.set('mock', new MockProvider());

    // Set primary provider
    if (config.OPENAI_API_KEY) {
      this.primaryProvider = 'openai';
    } else if (isDev) {
      this.primaryProvider = 'mock';
      console.warn('No LLM API keys configured, using mock provider');
    } else {
      throw new Error('No LLM provider configured');
    }
  }

  getProvider(type?: ProviderType): LlmProvider {
    const provider = this.providers.get(type ?? this.primaryProvider);
    if (!provider) {
      throw new Error(`Provider ${type ?? this.primaryProvider} not available`);
    }
    return provider;
  }

  /**
   * Parse transaction with caching and cost controls.
   * Strategy:
   * 1. Check cache
   * 2. Try heuristics
   * 3. Fall back to LLM
   */
  async parseTransaction(
    userId: string,
    text: string,
    context: ParseContext,
    providerType?: ProviderType
  ): Promise<ParseResult & { source: 'cache' | 'heuristic' | 'llm' }> {
    const textHash = hashText(text);

    // 1. Check cache
    const cached = await parseCache.get(textHash);
    if (cached) {
      return {
        transaction: cached.transaction,
        usage: { inputTokens: 0, outputTokens: 0, model: 'cache' },
        cached: true,
        source: 'cache',
      };
    }

    // 2. Try heuristics first
    const heuristicResult = parseWithHeuristics(text, context.appSource, context.postedAt);

    if (heuristicResult.confidence >= HEURISTIC_HIGH_CONFIDENCE && heuristicResult.transaction) {
      // High confidence heuristic result - cache and return
      await parseCache.set(textHash, heuristicResult.transaction, 'heuristic');

      return {
        transaction: heuristicResult.transaction,
        usage: { inputTokens: 0, outputTokens: 0, model: 'heuristic' },
        cached: false,
        source: 'heuristic',
      };
    }

    // If heuristic says it's not a transaction with high confidence, trust it
    if (
      heuristicResult.transaction?.isTransaction === false &&
      heuristicResult.confidence >= HEURISTIC_HIGH_CONFIDENCE
    ) {
      await parseCache.set(textHash, heuristicResult.transaction, 'heuristic');

      return {
        transaction: heuristicResult.transaction,
        usage: { inputTokens: 0, outputTokens: 0, model: 'heuristic' },
        cached: false,
        source: 'heuristic',
      };
    }

    // 3. Use LLM
    const provider = this.getProvider(providerType);

    // Check budget before calling LLM
    await costGuard.checkBudget(userId, provider.name);

    try {
      const result = await provider.parseTransaction(text, context);

      // Record usage
      await costGuard.recordUsage(
        userId,
        provider.name,
        result.usage.model,
        'parse',
        result.usage.inputTokens,
        result.usage.outputTokens
      );

      // Cache successful result
      await parseCache.set(textHash, result.transaction, provider.name);

      return {
        ...result,
        source: 'llm',
      };
    } catch (error) {
      costGuard.recordFailure(provider.name);
      throw error;
    }
  }

  /**
   * Categorize transaction.
   */
  async categorize(
    userId: string,
    merchant: string | null,
    payee: string | null,
    amount: number,
    direction: 'DEBIT' | 'CREDIT',
    providerType?: ProviderType
  ): Promise<CategoryResultWithUsage> {
    const provider = this.getProvider(providerType);

    // Check budget
    await costGuard.checkBudget(userId, provider.name);

    try {
      const result = await provider.categorize(merchant, payee, amount, direction);

      // Record usage
      await costGuard.recordUsage(
        userId,
        provider.name,
        result.usage.model,
        'categorize',
        result.usage.inputTokens,
        result.usage.outputTokens
      );

      return result;
    } catch (error) {
      costGuard.recordFailure(provider.name);
      throw error;
    }
  }

  async healthCheck(): Promise<Record<string, boolean>> {
    const results: Record<string, boolean> = {};

    for (const [name, provider] of this.providers) {
      results[name] = await provider.healthCheck();
    }

    return results;
  }
}

export const llmService = new LlmService();
