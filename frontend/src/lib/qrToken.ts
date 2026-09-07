/**
 * Where MenuJoin parks the scanned QR token while an unauthenticated visitor goes through login,
 * so navigateForRole can send them straight back to `/menu/join` afterwards.
 */
export const PENDING_QR_TOKEN_KEY = 'emberPendingQrToken'

/**
 * The table QR encodes `${origin}/menu/join?token=<jwt>`. Pull the token out of a scanned value —
 * accepts the full URL, or a bare JWT-shaped string as a fallback. Returns null for anything
 * else (a QR that isn't ours).
 */
export function tokenFromScannedValue(raw: string): string | null {
  const value = raw.trim()
  if (!value) return null
  try {
    const url = new URL(value)
    const token = url.searchParams.get('token')
    if (token && token.split('.').length === 3) return token
  } catch {
    // not a URL — fall through
  }
  if (value.split('.').length === 3 && sessionIdFromQrToken(value)) return value
  return null
}

/**
 * The waiter's session QR encodes a JWT whose `sub` claim is the session id (see the backend's
 * QrTokenService.generateQrToken). `POST /sessions/{id}/join` ignores the path id and trusts the
 * token, but we still pass the real id in the path for a clean URL and clear server logs.
 *
 * Returns null for anything that isn't a well-formed three-part JWT with a string `sub`.
 */
export function sessionIdFromQrToken(token: string): string | null {
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4)
    const claims = JSON.parse(atob(padded)) as { sub?: unknown }
    return typeof claims.sub === 'string' && claims.sub.length > 0 ? claims.sub : null
  } catch {
    return null
  }
}
