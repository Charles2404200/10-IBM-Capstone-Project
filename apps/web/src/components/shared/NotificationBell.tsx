import { HeaderGlobalAction } from '@carbon/react'
import { Notification as BellIcon } from '@carbon/icons-react'
import { useNavigate } from 'react-router-dom'
import { useUnreadNotificationCount } from '@/api/hooks/useNotifications'
import styles from './NotificationBell.module.scss'

export default function NotificationBell() {
  const navigate = useNavigate()
  const unread = useUnreadNotificationCount()
  const count = unread.data?.unreadCount ?? 0
  const badge = count > 99 ? '99+' : String(count)
  const label = unread.isError
    ? 'Notifications. Unread count unavailable'
    : `Notifications, ${count} unread`

  return (
    <HeaderGlobalAction
      aria-label={label}
      tooltipAlignment="end"
      onClick={() => navigate('/dashboard/notifications')}
      className={styles.action}
    >
      <BellIcon size={20} />
      {count > 0 && <span className={styles.badge} aria-hidden="true">{badge}</span>}
    </HeaderGlobalAction>
  )
}

