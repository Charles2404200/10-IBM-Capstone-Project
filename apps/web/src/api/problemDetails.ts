import axios from 'axios'
import type { ApiProblem } from '@/api/types'

const FALLBACK_TYPE = 'about:blank'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function nonBlankString(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback
}

function parseViolations(value: unknown): Record<string, string> | undefined {
  if (!isRecord(value)) return undefined
  const violations = Object.entries(value).reduce<Record<string, string>>((result, [field, message]) => {
    if (typeof message === 'string' && message.trim().length > 0) result[field] = message
    return result
  }, {})
  return Object.keys(violations).length > 0 ? violations : undefined
}

/** Parses RFC 7807 safely while retaining field-level validation details. */
export function getApiProblem(error: unknown, fallback: string): ApiProblem {
  const fallbackProblem: ApiProblem = {
    type: FALLBACK_TYPE,
    title: 'Request failed',
    status: 0,
    detail: fallback,
  }
  if (!axios.isAxiosError(error)) return fallbackProblem
  if (!isRecord(error.response?.data)) {
    return { ...fallbackProblem, status: error.response?.status ?? 0 }
  }

  const data = error.response.data
  return {
    type: nonBlankString(data.type, FALLBACK_TYPE),
    title: nonBlankString(data.title, 'Request failed'),
    status: typeof data.status === 'number' ? data.status : error.response?.status ?? 0,
    detail: nonBlankString(data.detail, fallback),
    violations: parseViolations(data.violations),
  }
}

/** Convenience adapter for notification-only call sites. */
export function getProblemDetail(error: unknown, fallback: string): string {
  return getApiProblem(error, fallback).detail
}
