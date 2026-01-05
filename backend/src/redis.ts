import { Redis } from 'ioredis';
import { config } from './config.js';

export const redis = new Redis(config.REDIS_URL, {
  maxRetriesPerRequest: 3,
  lazyConnect: true,
});

redis.on('error', (err: Error) => {
  console.error('Redis connection error:', err);
});

redis.on('connect', () => {
  console.log('Redis connected');
});

export async function connectRedis(): Promise<void> {
  try {
    await redis.connect();
  } catch (error) {
    // ioredis may auto-connect, ignore "already connected" errors
    if ((error as Error).message?.includes('already')) {
      return;
    }
    console.error('Failed to connect to Redis:', error);
    throw error;
  }
}

export async function disconnectRedis(): Promise<void> {
  await redis.quit();
  console.log('Redis disconnected');
}

// Cache helpers
export async function getCache<T>(key: string): Promise<T | null> {
  const value = await redis.get(key);
  if (!value) return null;
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}

export async function setCache<T>(
  key: string,
  value: T,
  ttlSeconds?: number
): Promise<void> {
  const serialized = JSON.stringify(value);
  if (ttlSeconds) {
    await redis.setex(key, ttlSeconds, serialized);
  } else {
    await redis.set(key, serialized);
  }
}

export async function deleteCache(key: string): Promise<void> {
  await redis.del(key);
}

export async function incrementCounter(
  key: string,
  ttlSeconds?: number
): Promise<number> {
  const value = await redis.incr(key);
  if (ttlSeconds && value === 1) {
    await redis.expire(key, ttlSeconds);
  }
  return value;
}
