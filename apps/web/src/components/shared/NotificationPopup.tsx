import { ToastNotification } from '@carbon/react'
import { useNotification } from '@/api/hooks/useNotification'
import styles from './NotificationPopup.module.scss'

export default function NotificationPopup() {
  const { notification, dismissNotification } = useNotification()

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
        timeout={8000}
      />
    </div>
  )
}
