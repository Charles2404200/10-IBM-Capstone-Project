import { describe, expect, it } from 'vitest'
import { notificationPreview } from './preview'

describe('notificationPreview', () => {
  it('truncates by Unicode code point without splitting emoji', () => {
    const preview = notificationPreview('A😀B😀C', 4)
    expect(preview).toBe('A😀B…')
    expect(preview).not.toContain('�')
  })
})

