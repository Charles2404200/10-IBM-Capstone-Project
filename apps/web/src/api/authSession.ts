import { useAuthStore } from '@/store/authStore'

export function shouldInvalidateSession(
  status: number | undefined,
  authorization: unknown,
  currentToken: string | null
): boolean {
  if (status !== 401 || !currentToken || typeof authorization !== 'string') return false
  if (!authorization.startsWith('Bearer ')) return false
  return authorization.slice(7) === currentToken
}

/**
 * Ends the session only when the rejected connection used the token that is
 * still current. This protects a newly established session from a late REST or
 * realtime authentication failure belonging to an older connection.
 */
export function invalidateCurrentSession(expectedToken: string): boolean {
  const auth = useAuthStore.getState()
  if (auth.token !== expectedToken) return false

  auth.logout()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
  return true
}
