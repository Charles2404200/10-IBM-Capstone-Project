import { ToastNotification } from '@carbon/react'
import { useEffect } from 'react'
import { useNotification } from '@/api/hooks/useNotification'
import styles from './NotificationPopup.module.scss'

export default function NotificationPopup() {
  const { notification, dismissNotification } = useNotification()

  useEffect(() => {
    if (!notification) return

    const timeoutId = window.setTimeout(dismissNotification, 8000)
    return () => window.clearTimeout(timeoutId)
  }, [dismissNotification, notification])

  if (!notification) return null

  return (
    <div className={styles.container} aria-live="polite">
      <ToastNotification
        key={notification.eventId}
        kind="info"
        role="status"
        title={notification.topicName}
        subtitle={notification.message}
        onClose={dismissNotification}
      />
    </div>
  )
}
