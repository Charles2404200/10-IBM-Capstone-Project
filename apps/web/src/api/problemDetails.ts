import axios from 'axios'

interface ProblemDetails {
  detail?: unknown
}

/** Maps RFC 7807 API errors to safe, actionable learner-facing copy. */
export function getProblemDetail(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<ProblemDetails>(error)) return fallback
  const detail = error.response?.data?.detail
  return typeof detail === 'string' && detail.trim().length > 0 ? detail : fallback
}
