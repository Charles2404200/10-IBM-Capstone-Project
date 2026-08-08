export function shouldInvalidateSession(
  status: number | undefined,
  authorization: unknown,
  currentToken: string | null
): boolean {
  if (status !== 401 || !currentToken || typeof authorization !== 'string') return false
  if (!authorization.startsWith('Bearer ')) return false
  return authorization.slice(7) === currentToken
}