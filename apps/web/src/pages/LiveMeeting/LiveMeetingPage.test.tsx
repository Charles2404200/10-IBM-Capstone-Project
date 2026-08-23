import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { shouldShowPlaceholder } from './transcriptPlaceholder'

describe('Live Meeting objective classes', () => {
  it('preserves the original classes when coach-mark classes are added', () => {
    const { container } = render(
      <>
        <section className="conversation-panel objective-meeting-view">Meeting</section>
          <aside className="decision-rail objective-hints">Hints</aside>
        <section className="relationship-panel objective-relationship">Relationship</section>
      </>
    )

    expect(container.querySelector('.objective-meeting-view')).toHaveClass('conversation-panel', 'objective-meeting-view')
    expect(container.querySelector('.objective-hints')).toHaveClass('decision-rail', 'objective-hints')
    expect(container.querySelector('.objective-relationship')).toHaveClass('relationship-panel', 'objective-relationship')
  })
})
describe('shouldShowPlaceholder', () => {
  it('shows the placeholder on an untouched transcript', () => {
    expect(shouldShowPlaceholder(0, null, false)).toBe(true)
  })

  it('hides it once a message is pending, while turns is still empty', () => {
    expect(shouldShowPlaceholder(0, 'What is driving the deadline?', false)).toBe(false)
  })

  it('hides it while the client is replying, while turns is still empty', () => {
    expect(shouldShowPlaceholder(0, null, true)).toBe(false)
  })

  it('hides it once the first exchange is persisted', () => {
    expect(shouldShowPlaceholder(2, null, false)).toBe(false)
  })
})
