import { describe, expect, it } from 'vitest'
import type { NotificationObject, NotificationPriority } from '@/api/types'
import { addNotificationToPopupState, type NotificationPopupState } from './aggregation'
import { MAX_VISIBLE_NOTIFICATION_POPUPS } from './config'

function notification(index: number, priority: NotificationPriority = 'NORMAL'): NotificationObject {
  return {
    eventId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
    topicName: `Notification ${index}`,
    messagePreview: 'Preview',
    priority,
    role: 'LEARNER',
    createdAt: new Date().toISOString(),
  }
}

describe('notification popup aggregation', () => {
  it('represents a burst of 50 with bounded popups and an exact overflow count', () => {
    let state: NotificationPopupState = { visible: [], overflowCount: 0 }
    for (let index = 1; index <= 50; index += 1) {
      state = addNotificationToPopupState(state, notification(index))
    }

    expect(state.visible).toHaveLength(MAX_VISIBLE_NOTIFICATION_POPUPS)
    expect(state.overflowCount).toBe(50 - MAX_VISIBLE_NOTIFICATION_POPUPS)
  })

  it('allows critical to pre-empt normal while keeping mounted popups bounded', () => {
    let state: NotificationPopupState = { visible: [], overflowCount: 0 }
    for (let index = 1; index <= MAX_VISIBLE_NOTIFICATION_POPUPS; index += 1) {
      state = addNotificationToPopupState(state, notification(index))
    }
    state = addNotificationToPopupState(state, notification(99, 'CRITICAL'))

    expect(state.visible).toHaveLength(MAX_VISIBLE_NOTIFICATION_POPUPS)
    expect(state.visible.some((item) => item.priority === 'CRITICAL')).toBe(true)
    expect(state.overflowCount).toBe(1)
  })
})

