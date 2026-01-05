import type { FastifyRequest, FastifyReply, FastifyInstance } from 'fastify';
import { verifyJwt } from '../utils/jwt.js';
import { authService } from '../services/auth.js';

declare module 'fastify' {
  interface FastifyRequest {
    userId?: string;
    deviceId?: string;
  }
}

export async function authMiddleware(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  const authHeader = request.headers.authorization;

  if (!authHeader?.startsWith('Bearer ')) {
    reply.status(401).send({ error: 'Missing authorization header' });
    return;
  }

  const token = authHeader.slice(7);
  const payload = verifyJwt(token);

  if (!payload) {
    reply.status(401).send({ error: 'Invalid or expired token' });
    return;
  }

  request.userId = payload.sub;
  request.deviceId = payload.deviceId;

  // Update device last seen (fire and forget)
  if (payload.deviceId) {
    authService.updateDeviceLastSeen(payload.deviceId).catch(() => {});
  }
}

export function registerAuthHook(fastify: FastifyInstance): void {
  fastify.addHook('onRequest', authMiddleware);
}
