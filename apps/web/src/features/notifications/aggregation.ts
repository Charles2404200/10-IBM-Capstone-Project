import type { NotificationObject, NotificationPriority } from '@/api/types'
import { MAX_VISIBLE_NOTIFICATION_POPUPS } from './config'

export interface NotificationPopupState {
  visible: NotificationObject[]
  overflowCount: number
}

function priorityRank(priority: NotificationPriority): number {
  return priority === 'CRITICAL' ? 3 : priority === 'IMPORTANT' ? 2 : 1
}

export function addNotificationToPopupState(
  current: NotificationPopupState,
  notification: NotificationObject,
): NotificationPopupState {
  if (current.visible.length < MAX_VISIBLE_NOTIFICATION_POPUPS) {
    return { ...current, visible: [...current.visible, notification] }
  }

  const lowestRank = Math.min(...current.visible.map((item) => priorityRank(item.priority)))
  if (priorityRank(notification.priority) > lowestRank) {
    let replaceIndex = 0
    for (let index = current.visible.length - 1; index >= 0; index -= 1) {
      if (priorityRank(current.visible[index].priority) === lowestRank) {
        replaceIndex = index
        break
      }
    }
    return {
      visible: current.visible.map((item, index) => index === replaceIndex ? notification : item),
      overflowCount: current.overflowCount + 1,
    }
  }
  return { ...current, overflowCount: current.overflowCount + 1 }
}

