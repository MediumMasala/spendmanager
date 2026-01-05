import { z } from 'zod';

// Common schemas
export const phoneSchema = z
  .string()
  .regex(/^\+?[1-9]\d{9,14}$/, 'Invalid phone number format');

export const e164Schema = z
  .string()
  .regex(/^\+[1-9]\d{10,14}$/, 'Invalid E.164 phone number');

// Auth schemas
export const requestOtpSchema = z.object({
  phone: phoneSchema,
  countryCode: z.string().length(2).default('IN'),
});

export const verifyOtpSchema = z.object({
  phone: phoneSchema,
  otp: z.string().length(6),
  deviceId: z.string().uuid().optional(),
  deviceInfo: z
    .object({
      platform: z.enum(['android', 'ios']).default('android'),
      appVersion: z.string().optional(),
      osVersion: z.string().optional(),
    })
    .optional(),
});

// Device schemas
export const registerDeviceSchema = z.object({
  deviceToken: z.string().optional(),
  platform: z.enum(['android', 'ios']).default('android'),
  appVersion: z.string().optional(),
  osVersion: z.string().optional(),
});

// Consent schemas
export const updateConsentSchema = z.object({
  cloudAiEnabled: z.boolean().optional(),
  uploadRawEnabled: z.boolean().optional(),
  whatsappEnabled: z.boolean().optional(),
});

// WhatsApp opt-in
export const optInWhatsappSchema = z.object({
  whatsappNumber: e164Schema,
});

// Event schemas
export const eventSchema = z.object({
  eventId: z.string().uuid(),
  deviceId: z.string().uuid(),
  appSource: z.string().min(1).max(50),
  postedAt: z.string().datetime(),
  textRedacted: z.string().max(1000),
  textRaw: z.string().max(1000).optional().nullable(),
  locale: z.string().default('en-IN'),
  timezone: z.string().default('Asia/Kolkata'),
});

export const ingestEventsSchema = z.object({
  events: z.array(eventSchema).min(1).max(100),
});

// Query schemas
export const paginationSchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(20),
  offset: z.coerce.number().int().min(0).default(0),
});

export const dateRangeSchema = z.object({
  from: z.string().datetime().optional(),
  to: z.string().datetime().optional(),
});

// Response types
export type RequestOtpInput = z.infer<typeof requestOtpSchema>;
export type VerifyOtpInput = z.infer<typeof verifyOtpSchema>;
export type RegisterDeviceInput = z.infer<typeof registerDeviceSchema>;
export type UpdateConsentInput = z.infer<typeof updateConsentSchema>;
export type OptInWhatsappInput = z.infer<typeof optInWhatsappSchema>;
export type EventInput = z.infer<typeof eventSchema>;
export type IngestEventsInput = z.infer<typeof ingestEventsSchema>;
