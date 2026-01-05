import OpenAI from 'openai';
import { config } from '../config.js';
import {
  type LlmProvider,
  type ParseContext,
  type ParseResult,
  type CategoryResultWithUsage,
  LlmProviderError,
  RateLimitError,
} from './provider.js';
import {
  parsedTransactionSchema,
  categorySchema,
  openAiTransactionJsonSchema,
  openAiCategoryJsonSchema,
  type ParsedTransaction,
} from './schemas.js';
import {
  PARSE_TRANSACTION_SYSTEM_PROMPT,
  PARSE_TRANSACTION_USER_PROMPT,
  CATEGORIZE_SYSTEM_PROMPT,
  CATEGORIZE_USER_PROMPT,
} from './prompts.js';

export class OpenAIProvider implements LlmProvider {
  readonly name = 'openai';
  private client: OpenAI;
  private model: string;
  private maxTokens: number;

  constructor() {
    if (!config.OPENAI_API_KEY) {
      throw new Error('OPENAI_API_KEY is required for OpenAI provider');
    }

    this.client = new OpenAI({
      apiKey: config.OPENAI_API_KEY,
    });
    this.model = config.OPENAI_MODEL;
    this.maxTokens = config.OPENAI_MAX_TOKENS;
  }

  async parseTransaction(
    text: string,
    context: ParseContext
  ): Promise<ParseResult> {
    try {
      const response = await this.client.chat.completions.create({
        model: this.model,
        max_tokens: this.maxTokens,
        temperature: 0.1,
        // Use structured outputs with JSON schema
        response_format: {
          type: 'json_schema',
          json_schema: openAiTransactionJsonSchema as any,
        },
        // Don't store the request (privacy)
        store: false,
        messages: [
          {
            role: 'system',
            content: PARSE_TRANSACTION_SYSTEM_PROMPT,
          },
          {
            role: 'user',
            content: PARSE_TRANSACTION_USER_PROMPT(text, context),
          },
        ],
      });

      const content = response.choices[0]?.message?.content;
      if (!content) {
        throw new LlmProviderError('Empty response from OpenAI', this.name);
      }

      const parsed = JSON.parse(content);
      const validated = parsedTransactionSchema.parse(parsed);

      return {
        transaction: validated,
        usage: {
          inputTokens: response.usage?.prompt_tokens ?? 0,
          outputTokens: response.usage?.completion_tokens ?? 0,
          model: this.model,
        },
        cached: false,
      };
    } catch (error) {
      if (error instanceof OpenAI.RateLimitError) {
        throw new RateLimitError(this.name);
      }
      if (error instanceof OpenAI.APIError) {
        throw new LlmProviderError(
          error.message,
          this.name,
          error.status === 429 || error.status === 503,
          error.status
        );
      }
      throw error;
    }
  }

  async categorize(
    merchant: string | null,
    payee: string | null,
    amount: number,
    direction: 'DEBIT' | 'CREDIT'
  ): Promise<CategoryResultWithUsage> {
    try {
      const response = await this.client.chat.completions.create({
        model: this.model,
        max_tokens: 100,
        temperature: 0.1,
        response_format: {
          type: 'json_schema',
          json_schema: openAiCategoryJsonSchema as any,
        },
        store: false,
        messages: [
          {
            role: 'system',
            content: CATEGORIZE_SYSTEM_PROMPT,
          },
          {
            role: 'user',
            content: CATEGORIZE_USER_PROMPT(merchant, payee, amount, direction),
          },
        ],
      });

      const content = response.choices[0]?.message?.content;
      if (!content) {
        throw new LlmProviderError('Empty response from OpenAI', this.name);
      }

      const parsed = JSON.parse(content);
      const validated = categorySchema.parse(parsed);

      return {
        result: validated,
        usage: {
          inputTokens: response.usage?.prompt_tokens ?? 0,
          outputTokens: response.usage?.completion_tokens ?? 0,
          model: this.model,
        },
        cached: false,
      };
    } catch (error) {
      if (error instanceof OpenAI.RateLimitError) {
        throw new RateLimitError(this.name);
      }
      if (error instanceof OpenAI.APIError) {
        throw new LlmProviderError(
          error.message,
          this.name,
          error.status === 429 || error.status === 503,
          error.status
        );
      }
      throw error;
    }
  }

  async healthCheck(): Promise<boolean> {
    try {
      const response = await this.client.chat.completions.create({
        model: this.model,
        max_tokens: 5,
        messages: [{ role: 'user', content: 'ping' }],
      });
      return !!response.choices[0]?.message?.content;
    } catch {
      return false;
    }
  }
}
