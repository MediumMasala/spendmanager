import { z } from 'zod';

// Parsed transaction schema - strict structure for LLM output
export const parsedTransactionSchema = z.object({
  isTransaction: z.boolean().describe('Whether this is a financial transaction'),
  amount: z.number().nullable().describe('Transaction amount in decimal'),
  currency: z.string().default('INR').describe('Currency code'),
  direction: z
    .enum(['DEBIT', 'CREDIT'])
    .nullable()
    .describe('Transaction direction'),
  occurredAt: z
    .string()
    .nullable()
    .describe('Transaction timestamp in ISO 8601 format'),
  merchant: z.string().nullable().describe('Merchant or business name'),
  payee: z.string().nullable().describe('Payee name for transfers'),
  instrument: z
    .enum(['UPI', 'CARD', 'NEFT', 'IMPS', 'WALLET', 'CASH', 'OTHER'])
    .nullable()
    .describe('Payment instrument used'),
  bankHint: z.string().nullable().describe('Bank name hint from message'),
  appHint: z.string().nullable().describe('App name hint (GPay, PhonePe, etc)'),
  referenceId: z.string().nullable().describe('Transaction reference ID'),
  confidence: z
    .number()
    .min(0)
    .max(1)
    .describe('Confidence score 0-1'),
  flags: z
    .array(z.string())
    .default([])
    .describe('Special flags like "partial", "failed", "pending"'),
  reason: z
    .string()
    .nullable()
    .describe('If not a transaction, reason why'),
});

export type ParsedTransaction = z.infer<typeof parsedTransactionSchema>;

// Category schema
export const categorySchema = z.object({
  category: z.string().describe('Transaction category'),
  subcategory: z.string().nullable().describe('More specific subcategory'),
  confidence: z.number().min(0).max(1).describe('Confidence score'),
});

export type CategoryResult = z.infer<typeof categorySchema>;

// OpenAI JSON Schema format for structured outputs
export const openAiTransactionJsonSchema = {
  name: 'parsed_transaction',
  strict: true,
  schema: {
    type: 'object',
    properties: {
      isTransaction: {
        type: 'boolean',
        description: 'Whether this is a financial transaction',
      },
      amount: {
        type: ['number', 'null'],
        description: 'Transaction amount in decimal',
      },
      currency: {
        type: 'string',
        description: 'Currency code',
      },
      direction: {
        type: ['string', 'null'],
        enum: ['DEBIT', 'CREDIT', null],
        description: 'Transaction direction',
      },
      occurredAt: {
        type: ['string', 'null'],
        description: 'Transaction timestamp in ISO 8601 format',
      },
      merchant: {
        type: ['string', 'null'],
        description: 'Merchant or business name',
      },
      payee: {
        type: ['string', 'null'],
        description: 'Payee name for transfers',
      },
      instrument: {
        type: ['string', 'null'],
        enum: ['UPI', 'CARD', 'NEFT', 'IMPS', 'WALLET', 'CASH', 'OTHER', null],
        description: 'Payment instrument used',
      },
      bankHint: {
        type: ['string', 'null'],
        description: 'Bank name hint from message',
      },
      appHint: {
        type: ['string', 'null'],
        description: 'App name hint',
      },
      referenceId: {
        type: ['string', 'null'],
        description: 'Transaction reference ID',
      },
      confidence: {
        type: 'number',
        description: 'Confidence score 0-1',
      },
      flags: {
        type: 'array',
        items: { type: 'string' },
        description: 'Special flags',
      },
      reason: {
        type: ['string', 'null'],
        description: 'If not a transaction, reason why',
      },
    },
    required: [
      'isTransaction',
      'amount',
      'currency',
      'direction',
      'occurredAt',
      'merchant',
      'payee',
      'instrument',
      'bankHint',
      'appHint',
      'referenceId',
      'confidence',
      'flags',
      'reason',
    ],
    additionalProperties: false,
  },
};

export const openAiCategoryJsonSchema = {
  name: 'category_result',
  strict: true,
  schema: {
    type: 'object',
    properties: {
      category: {
        type: 'string',
        description: 'Transaction category',
      },
      subcategory: {
        type: ['string', 'null'],
        description: 'More specific subcategory',
      },
      confidence: {
        type: 'number',
        description: 'Confidence score 0-1',
      },
    },
    required: ['category', 'subcategory', 'confidence'],
    additionalProperties: false,
  },
};

// Category definitions for India
export const CATEGORIES = {
  FOOD_DINING: 'Food & Dining',
  GROCERIES: 'Groceries',
  TRANSPORT: 'Transport',
  SHOPPING: 'Shopping',
  ENTERTAINMENT: 'Entertainment',
  UTILITIES: 'Utilities',
  HEALTH: 'Health',
  EDUCATION: 'Education',
  TRAVEL: 'Travel',
  TRANSFER: 'Transfer',
  INVESTMENT: 'Investment',
  SUBSCRIPTION: 'Subscription',
  RENT: 'Rent',
  EMI: 'EMI/Loan',
  INSURANCE: 'Insurance',
  SALARY: 'Salary',
  REFUND: 'Refund',
  CASHBACK: 'Cashback',
  OTHER: 'Other',
} as const;

export type Category = keyof typeof CATEGORIES;
