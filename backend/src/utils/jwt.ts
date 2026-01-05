import crypto from 'crypto';
import { config } from '../config.js';

interface JwtPayload {
  sub: string; // user ID
  deviceId?: string;
  iat: number;
  exp: number;
}

const ALGORITHM = 'HS256';
const TOKEN_EXPIRY_SECONDS = 30 * 24 * 60 * 60; // 30 days

function base64UrlEncode(data: string | Buffer): string {
  const base64 = Buffer.isBuffer(data)
    ? data.toString('base64')
    : Buffer.from(data).toString('base64');
  return base64.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function base64UrlDecode(str: string): string {
  let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const padding = 4 - (base64.length % 4);
  if (padding !== 4) {
    base64 += '='.repeat(padding);
  }
  return Buffer.from(base64, 'base64').toString('utf-8');
}

export function signJwt(userId: string, deviceId?: string): string {
  const now = Math.floor(Date.now() / 1000);

  const header = {
    alg: ALGORITHM,
    typ: 'JWT',
  };

  const payload: JwtPayload = {
    sub: userId,
    deviceId,
    iat: now,
    exp: now + TOKEN_EXPIRY_SECONDS,
  };

  const headerEncoded = base64UrlEncode(JSON.stringify(header));
  const payloadEncoded = base64UrlEncode(JSON.stringify(payload));

  const signature = crypto
    .createHmac('sha256', config.JWT_SECRET)
    .update(`${headerEncoded}.${payloadEncoded}`)
    .digest();

  return `${headerEncoded}.${payloadEncoded}.${base64UrlEncode(signature)}`;
}

export function verifyJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const [headerEncoded, payloadEncoded, signatureEncoded] = parts;

    // Verify signature
    const expectedSignature = crypto
      .createHmac('sha256', config.JWT_SECRET)
      .update(`${headerEncoded}.${payloadEncoded}`)
      .digest();

    const actualSignature = Buffer.from(
      signatureEncoded!.replace(/-/g, '+').replace(/_/g, '/'),
      'base64'
    );

    if (!crypto.timingSafeEqual(expectedSignature, actualSignature)) {
      return null;
    }

    const payload = JSON.parse(base64UrlDecode(payloadEncoded!)) as JwtPayload;

    // Check expiry
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp < now) {
      return null;
    }

    return payload;
  } catch {
    return null;
  }
}
