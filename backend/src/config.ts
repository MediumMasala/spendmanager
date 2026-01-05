import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config();

const envSchema = z.object({
  // Database
  DATABASE_URL: z.string().url(),
  REDIS_URL: z.string().url(),

  // Server
  PORT: z.coerce.number().default(3000),
  HOST: z.string().default('0.0.0.0'),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),

  // Auth
  JWT_SECRET: z.string().min(32),

  // OpenAI
  OPENAI_API_KEY: z.string().optional(),
  OPENAI_MODEL: z.string().default('gpt-4o-mini'),
  OPENAI_MAX_TOKENS: z.coerce.number().default(500),

  // Anthropic
  ANTHROPIC_API_KEY: z.string().optional(),
  ANTHROPIC_MODEL: z.string().default('claude-3-haiku-20240307'),

  // LLM Cost Controls
  LLM_DAILY_BUDGET_USD: z.coerce.number().default(10),
  LLM_PER_USER_DAILY_BUDGET_USD: z.coerce.number().default(0.5),
  LLM_CIRCUIT_BREAKER_THRESHOLD: z.coerce.number().default(5),
  LLM_CIRCUIT_BREAKER_TIMEOUT_MS: z.coerce.number().default(60000),

  // WhatsApp
  WHATSAPP_PHONE_NUMBER_ID: z.string().optional(),
  WHATSAPP_BUSINESS_ACCOUNT_ID: z.string().optional(),
  WHATSAPP_ACCESS_TOKEN: z.string().optional(),
  WHATSAPP_VERIFY_TOKEN: z.string().optional(),
  WHATSAPP_TEMPLATE_WEEKLY_SUMMARY: z.string().default('weekly_money_summary'),

  // OTP
  OTP_PROVIDER: z.enum(['console', 'twilio', 'msg91']).default('console'),
  OTP_TWILIO_SID: z.string().optional(),
  OTP_TWILIO_AUTH_TOKEN: z.string().optional(),
  OTP_TWILIO_FROM: z.string().optional(),

  // Security
  RATE_LIMIT_MAX: z.coerce.number().default(100),
  RATE_LIMIT_WINDOW_MS: z.coerce.number().default(60000),

  // Logging
  LOG_LEVEL: z.enum(['trace', 'debug', 'info', 'warn', 'error', 'fatal']).default('info'),
});

const parsed = envSchema.safeParse(process.env);

if (!parsed.success) {
  console.error('Invalid environment variables:', parsed.error.format());
  process.exit(1);
}

export const config = parsed.data;

export const isDev = config.NODE_ENV === 'development';
export const isProd = config.NODE_ENV === 'production';
export const isTest = config.NODE_ENV === 'test';
