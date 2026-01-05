import crypto from 'crypto';

export function hashPhone(phone: string): string {
  // Normalize phone to E.164 without +
  const normalized = phone.replace(/[^\d]/g, '');
  return crypto
    .createHash('sha256')
    .update(`phone:${normalized}`)
    .digest('hex');
}

export function hashText(text: string): string {
  // Normalize: lowercase, remove extra whitespace
  const normalized = text.toLowerCase().replace(/\s+/g, ' ').trim();
  return crypto.createHash('sha256').update(normalized).digest('hex');
}

export function generateOtp(): string {
  return crypto.randomInt(100000, 999999).toString();
}

export function hashOtp(otp: string): string {
  return crypto.createHash('sha256').update(`otp:${otp}`).digest('hex');
}

export function generateSecureToken(bytes: number = 32): string {
  return crypto.randomBytes(bytes).toString('hex');
}

export function generateDeviceId(): string {
  return crypto.randomUUID();
}
