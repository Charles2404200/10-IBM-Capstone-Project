import { Button, ToastNotification } from '@carbon/react'
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import type { NotificationObject } from '@/api/types'
import { useNotification } from '@/api/hooks/useNotification'
import { NOTIFICATION_POPUP_DURATION_MS } from '@/features/notifications/config'
import styles from './NotificationPopup.module.scss'

function Popup({ notification, dismiss }: {
  notification: NotificationObject
  dismiss: (eventId: string) => void
}) {
  useEffect(() => {
    const timeoutId = window.setTimeout(
      () => dismiss(notification.eventId),
      NOTIFICATION_POPUP_DURATION_MS,
    )
    return () => window.clearTimeout(timeoutId)
  }, [dismiss, notification.eventId])

  const critical = notification.priority === 'CRITICAL'
  const important = notification.priority === 'IMPORTANT'
  const titlePrefix = critical ? 'Critical: ' : important ? 'Important: ' : ''

  return (
    <div className={critical ? styles.critical : important ? styles.important : undefined}>
      <ToastNotification
        kind={critical ? 'error' : important ? 'warning' : 'info'}
        role={critical ? 'alert' : 'status'}
        title={`${titlePrefix}${notification.topicName}`}
        subtitle={notification.messagePreview}
        onClose={() => dismiss(notification.eventId)}
      />
    </div>
  )
}

export default function NotificationPopup() {
  const { visible, overflowCount, dismissNotification, clearPopups } = useNotification()
  const navigate = useNavigate()

  useEffect(() => {
    if (overflowCount === 0) return
    const timeoutId = window.setTimeout(clearPopups, NOTIFICATION_POPUP_DURATION_MS)
    return () => window.clearTimeout(timeoutId)
  }, [clearPopups, overflowCount])

  if (visible.length === 0 && overflowCount === 0) return null

  const openNotificationCentre = () => {
    clearPopups()
    navigate('/dashboard/notifications')
  }

  return (
    <section className={styles.container} aria-label="Recent notifications">
      {visible.map((notification) => (
        <Popup
          key={notification.eventId}
          notification={notification}
          dismiss={dismissNotification}
        />
      ))}
      {overflowCount > 0 && (
        <div role="status" aria-live="polite">
          <Button
            kind="tertiary"
            size="sm"
            className={styles.overflow}
            onClick={openNotificationCentre}
            aria-label={`${overflowCount} additional notifications. Open Notification Centre`}
          >
            +{overflowCount} notifications
          </Button>
        </div>
      )}
    </section>
  )
}
