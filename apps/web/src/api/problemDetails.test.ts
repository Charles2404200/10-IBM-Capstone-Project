import { describe, expect, it } from 'vitest'
import { getApiProblem, getProblemDetail } from './problemDetails'

function axiosError(status?: number, data?: unknown) {
  return {
    isAxiosError: true,
    response: status === undefined ? undefined : { status, data },
  }
}

describe('RFC 7807 problem parsing', () => {
  it('preserves validation metadata and field violations', () => {
    const problem = getApiProblem(axiosError(400, {
      type: 'https://example.test/problems/validation-error',
      title: 'Bad Request',
      status: 400,
      detail: 'Request validation failed',
      violations: { budget: 'must be greater than or equal to 0' },
    }), 'Fallback')

    expect(problem).toEqual({
      type: 'https://example.test/problems/validation-error',
      title: 'Bad Request',
      status: 400,
      detail: 'Request validation failed',
      violations: { budget: 'must be greater than or equal to 0' },
    })
  })

  it.each([
    [422, 'Domain rule failed'],
    [401, 'Authentication required'],
    [403, 'Access denied'],
    [429, 'Too many requests'],
  ])('preserves a %i API problem', (status, detail) => {
    expect(getApiProblem(axiosError(status, { status, detail }), 'Fallback')).toMatchObject({ status, detail })
  })

  it('uses safe fallbacks for malformed, non-Axios, and network failures', () => {
    expect(getApiProblem(axiosError(500, '<html>failure</html>'), 'Try again')).toEqual({
      type: 'about:blank', title: 'Request failed', status: 500, detail: 'Try again',
    })
    expect(getApiProblem(new Error('internal stack'), 'Try again').detail).toBe('Try again')
    expect(getApiProblem(axiosError(), 'Network unavailable').detail).toBe('Network unavailable')
    expect(getProblemDetail(axiosError(500, { detail: 'An unexpected error occurred' }), 'Fallback'))
      .toBe('An unexpected error occurred')
  })
})
