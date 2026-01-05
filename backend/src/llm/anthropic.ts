import {
  type LlmProvider,
  type ParseContext,
  type ParseResult,
  type CategoryResultWithUsage,
  LlmProviderError,
} from './provider.js';
import { config } from '../config.js';

/**
 * Anthropic provider stub - implement when needed.
 *
 * To implement:
 * 1. Install @anthropic-ai/sdk
 * 2. Use tool_use with JSON schema for structured outputs
 * 3. Follow similar pattern to OpenAI provider
 */
export class AnthropicProvider implements LlmProvider {
  readonly name = 'anthropic';

  constructor() {
    if (!config.ANTHROPIC_API_KEY) {
      console.warn('ANTHROPIC_API_KEY not set - Anthropic provider disabled');
    }
  }

  async parseTransaction(
    text: string,
    context: ParseContext
  ): Promise<ParseResult> {
    throw new LlmProviderError(
      'Anthropic provider not implemented',
      this.name,
      false
    );
  }

  async categorize(
    merchant: string | null,
    payee: string | null,
    amount: number,
    direction: 'DEBIT' | 'CREDIT'
  ): Promise<CategoryResultWithUsage> {
    throw new LlmProviderError(
      'Anthropic provider not implemented',
      this.name,
      false
    );
  }

  async healthCheck(): Promise<boolean> {
    return false;
  }
}
