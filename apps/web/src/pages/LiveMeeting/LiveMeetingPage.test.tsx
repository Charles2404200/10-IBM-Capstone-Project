import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

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