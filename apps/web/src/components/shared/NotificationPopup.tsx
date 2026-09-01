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

  const critical = notification.priority === 'CRITICAL'
  const important = notification.priority === 'IMPORTANT'
  const titlePrefix = critical ? 'Critical: ' : important ? 'Important: ' : ''

  return (
    <div
      className={`${styles.container} ${critical ? styles.critical : important ? styles.important : ''}`}
      aria-live={critical ? 'assertive' : 'polite'}
    >
      <ToastNotification
        key={notification.eventId}
        kind={critical ? 'error' : important ? 'warning' : 'info'}
        role={critical ? 'alert' : 'status'}
        title={`${titlePrefix}${notification.topicName}`}
        subtitle={notification.message}
        onClose={dismissNotification}
      />
    </div>
  )
}
