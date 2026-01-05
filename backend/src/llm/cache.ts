import { prisma } from '../db.js';
import { redis, getCache, setCache } from '../redis.js';
import type { ParsedTransaction } from './schemas.js';

const REDIS_CACHE_TTL_SECONDS = 24 * 60 * 60; // 24 hours
const REDIS_PREFIX = 'llm:parse:';

interface CachedParseResult {
  transaction: ParsedTransaction;
  provider: string;
  cachedAt: string;
}

export class ParseCache {
  /**
   * Get cached parse result by text hash.
   * Checks Redis first, then Postgres.
   */
  async get(textHash: string): Promise<CachedParseResult | null> {
    // Try Redis first (fast)
    const redisKey = `${REDIS_PREFIX}${textHash}`;
    const redisCached = await getCache<CachedParseResult>(redisKey);
    if (redisCached) {
      return redisCached;
    }

    // Try Postgres (persistent)
    const dbCached = await prisma.parseCache.findUnique({
      where: { textHash },
    });

    if (dbCached) {
      // Update hit count
      await prisma.parseCache.update({
        where: { id: dbCached.id },
        data: {
          hitCount: { increment: 1 },
          lastHitAt: new Date(),
        },
      });

      const result: CachedParseResult = {
        transaction: dbCached.resultJson as unknown as ParsedTransaction,
        provider: dbCached.provider,
        cachedAt: dbCached.createdAt.toISOString(),
      };

      // Warm Redis cache
      await setCache(redisKey, result, REDIS_CACHE_TTL_SECONDS);

      return result;
    }

    return null;
  }

  /**
   * Store parse result in cache.
   */
  async set(
    textHash: string,
    transaction: ParsedTransaction,
    provider: string
  ): Promise<void> {
    const result: CachedParseResult = {
      transaction,
      provider,
      cachedAt: new Date().toISOString(),
    };

    // Store in Redis (fast access)
    const redisKey = `${REDIS_PREFIX}${textHash}`;
    await setCache(redisKey, result, REDIS_CACHE_TTL_SECONDS);

    // Store in Postgres (persistent)
    await prisma.parseCache.upsert({
      where: { textHash },
      create: {
        textHash,
        resultJson: transaction as any,
        provider,
      },
      update: {
        resultJson: transaction as any,
        provider,
        hitCount: { increment: 1 },
        lastHitAt: new Date(),
      },
    });
  }

  /**
   * Invalidate cache entry.
   */
  async invalidate(textHash: string): Promise<void> {
    const redisKey = `${REDIS_PREFIX}${textHash}`;
    await redis.del(redisKey);
    await prisma.parseCache.deleteMany({
      where: { textHash },
    });
  }

  /**
   * Get cache statistics.
   */
  async getStats(): Promise<{
    totalEntries: number;
    totalHits: number;
    avgHitsPerEntry: number;
  }> {
    const stats = await prisma.parseCache.aggregate({
      _count: { id: true },
      _sum: { hitCount: true },
      _avg: { hitCount: true },
    });

    return {
      totalEntries: stats._count.id,
      totalHits: stats._sum.hitCount ?? 0,
      avgHitsPerEntry: stats._avg.hitCount ?? 0,
    };
  }

  /**
   * Clean up old cache entries (run periodically).
   */
  async cleanup(maxAgeDays: number = 30): Promise<number> {
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - maxAgeDays);

    const result = await prisma.parseCache.deleteMany({
      where: {
        lastHitAt: { lt: cutoff },
      },
    });

    return result.count;
  }
}

export const parseCache = new ParseCache();
