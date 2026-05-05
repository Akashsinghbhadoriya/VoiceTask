import jwt from 'jsonwebtoken';
import { createPublicKey } from 'crypto';
import { getEnv } from '../config/env.js';

export interface SupabaseJwtPayload {
  sub: string;
  email: string;
  aud: string;
  iat: number;
  exp: number;
}

export interface VerifyJwtResult {
  id: string;
  email: string;
}

// Cache JWKS for 1 hour to avoid fetching on every request
let jwksCache: { keys: any[] } | null = null;
let jwksCacheTime = 0;
const JWKS_CACHE_TTL = 3600_000;

async function getRs256PublicKey(kid: string | undefined): Promise<string> {
  const now = Date.now();
  if (!jwksCache || now - jwksCacheTime > JWKS_CACHE_TTL) {
    const env = getEnv();
    const res = await fetch(`${env.SUPABASE_URL}/auth/v1/.well-known/jwks.json`);
    if (!res.ok) throw new Error('Failed to fetch JWKS');
    jwksCache = await res.json() as { keys: any[] };
    jwksCacheTime = now;
  }

  const key = jwksCache!.keys.find((k: any) => !kid || k.kid === kid);
  if (!key) throw new Error('Signing key not found in JWKS');

  return createPublicKey({ key, format: 'jwk' }).export({ type: 'spki', format: 'pem' }) as string;
}

export async function verifySupabaseJwt(token: string): Promise<VerifyJwtResult> {
  try {
    // Decode header without verification to determine the algorithm
    const header = jwt.decode(token, { complete: true })?.header;
    if (!header) throw new Error('Invalid token format');

    let decoded: SupabaseJwtPayload;

    if (header.alg === 'RS256' || header.alg === 'ES256') {
      const publicKey = await getRs256PublicKey(header.kid);
      decoded = jwt.verify(token, publicKey, { algorithms: ['RS256', 'ES256'] }) as SupabaseJwtPayload;
    } else {
      const env = getEnv();
      decoded = jwt.verify(token, env.SUPABASE_JWT_SECRET, { algorithms: ['HS256'] }) as SupabaseJwtPayload;
    }

    if (!decoded.sub || !decoded.email) {
      throw new Error('Missing required JWT claims');
    }

    if (decoded.aud !== 'authenticated') {
      throw new Error('Invalid JWT audience');
    }

    return { id: decoded.sub, email: decoded.email };
  } catch (error) {
    throw new Error(`JWT verification failed: ${error instanceof Error ? error.message : String(error)}`);
  }
}
