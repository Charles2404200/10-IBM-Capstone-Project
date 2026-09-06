import { Button, InlineLoading, InlineNotification, Modal, Tag } from '@carbon/react'
import { CheckmarkOutline, NotificationFilled, Time } from '@carbon/icons-react'
import { useEffect, useMemo, useState } from 'react'
import type { NotificationPriority, NotificationSummary } from '@/api/types'
import {
  useMarkNotificationRead,
  useNotificationDetail,
  useNotifications,
} from '@/api/hooks/useNotifications'
import { relativeNotificationTime } from '@/features/notifications/time'
import styles from './NotificationCentrePage.module.scss'

function useMobileLayout() {
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 671px)').matches)
  useEffect(() => {
    const media = window.matchMedia('(max-width: 671px)')
    const update = () => setMobile(media.matches)
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])
  return mobile
}

function priorityTag(priority: NotificationPriority) {
  const type = priority === 'CRITICAL' ? 'red' : priority === 'IMPORTANT' ? 'warm-gray' : 'cool-gray'
  const symbol = priority === 'CRITICAL' ? '●' : priority === 'IMPORTANT' ? '!' : '–'
  return (
    <Tag type={type} size="sm">
      <span className={styles.priorityIcon} aria-hidden="true">{symbol}</span>
      {priority}
    </Tag>
  )
}

function NotificationListItem({ item, selected, onSelect }: {
  item: NotificationSummary
  selected: boolean
  onSelect: (item: NotificationSummary) => void
}) {
  return (
    <li>
      <button
        type="button"
        className={`${styles.listItem} ${selected ? styles.selected : ''} ${!item.isRead ? styles.unread : ''}`}
        data-priority={item.priority}
        onClick={() => onSelect(item)}
        aria-current={selected ? 'true' : undefined}
      >
        <span className={styles.itemHeading}>
          <strong>{item.topicName}</strong>
          {!item.isRead && <span className={styles.unreadLabel}>Unread</span>}
        </span>
        <span className={styles.preview}>{item.messagePreview}</span>
        <span className={styles.metadata}>
          {priorityTag(item.priority)}
          <time dateTime={item.createdAt}>{relativeNotificationTime(item.createdAt)}</time>
        </span>
      </button>
    </li>
  )
}

function NotificationDetail({ eventId }: { eventId: string }) {
  const detail = useNotificationDetail(eventId)
  if (detail.isPending) return <InlineLoading description="Loading notification details" />
  if (detail.isError || !detail.data) {
    return <InlineNotification kind="error" lowContrast hideCloseButton title="Unable to load notification" />
  }
  return (
    <article className={styles.detailArticle} data-priority={detail.data.priority}>
      <div className={styles.detailHeader}>
        <div className={styles.detailTitleGroup}>
          <span className={styles.detailIcon} aria-hidden="true">
            <NotificationFilled size={24} />
          </span>
          <div>
            <span className={styles.detailEyebrow}>Notification</span>
            <h2>{detail.data.topicName}</h2>
          </div>
        </div>
        {priorityTag(detail.data.priority)}
      </div>

      <section className={styles.messageCard} aria-label="Notification message">
        <h3>Message</h3>
        <p className={styles.fullMessage}>{detail.data.message}</p>
      </section>

      <dl className={styles.detailMetadata}>
        <div className={styles.metadataCard}>
          <dt><Time size={18} aria-hidden="true" />Received</dt>
          <dd><time dateTime={detail.data.createdAt}>{new Date(detail.data.createdAt).toLocaleString()}</time></dd>
        </div>
        <div className={styles.metadataCard}>
          <dt><CheckmarkOutline size={18} aria-hidden="true" />Status</dt>
          <dd>{detail.data.read ? 'Read' : 'Unread'}</dd>
        </div>
      </dl>
    </article>
  )
}

export default function NotificationCentrePage() {
  const notifications = useNotifications()
  const markRead = useMarkNotificationRead()
  const mobile = useMobileLayout()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const items = useMemo(
    () => notifications.data?.pages.flatMap((page) => page.items) ?? [],
    [notifications.data],
  )

  const select = (item: NotificationSummary) => {
    setSelectedId(item.eventId)
    if (!item.isRead) markRead.mutate(item.eventId)
  }

  return (
    <main className={styles.page}>
      <header className={styles.pageHeader}>
        <div>
          <h1>Notifications</h1>
          <p>Your durable notification history and important updates.</p>
        </div>
      </header>

      {notifications.isPending && <InlineLoading description="Loading notifications" />}
      {notifications.isError && (
        <div>
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Unable to load notifications"
            subtitle="Please retry. Realtime availability does not affect notification history."
          />
          <Button kind="tertiary" size="sm" onClick={() => void notifications.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {!notifications.isPending && !notifications.isError && items.length === 0 && (
        <section className={styles.empty} aria-labelledby="empty-notifications-title">
          <div className={styles.emptyStateContent}>
            <span className={styles.emptyStateIcon} aria-hidden="true">
              <NotificationFilled size={32} />
            </span>
            <span className={styles.emptyStateEyebrow}>Notification centre</span>
            <h2 id="empty-notifications-title">You have no notifications.</h2>
            <p>Important updates and announcements will appear here when they arrive.</p>
          </div>
        </section>
      )}

      {items.length > 0 && (
        <div className={styles.layout}>
          <section className={styles.listPanel} aria-label="Notification list">
            <ul className={styles.list}>
              {items.map((item) => (
                <NotificationListItem
                  key={item.eventId}
                  item={item}
                  selected={selectedId === item.eventId}
                  onSelect={select}
                />
              ))}
            </ul>
            {notifications.hasNextPage && (
              <Button
                kind="tertiary"
                disabled={notifications.isFetchingNextPage}
                onClick={() => void notifications.fetchNextPage()}
              >
                {notifications.isFetchingNextPage ? 'Loading\u2026' : 'Load more notifications'}
              </Button>
            )}
          </section>

          {!mobile && (
            <section className={styles.detailPanel} aria-label="Notification details">
              {selectedId
                ? <NotificationDetail eventId={selectedId} />
                : (
                  <div className={styles.detailPlaceholder}>
                    <div className={styles.emptyStateContent}>
                      <span className={styles.emptyStateIcon} aria-hidden="true">
                        <NotificationFilled size={32} />
                      </span>
                      <span className={styles.emptyStateEyebrow}>Message preview</span>
                      <h2>No notification selected</h2>
                      <p>Select a notification from the list to read the complete message and delivery details.</p>
                    </div>
                  </div>
                )}
            </section>
          )}
        </div>
      )}

      {mobile && (
        <Modal
          open={Boolean(selectedId)}
          modalHeading="Notification details"
          passiveModal
          onRequestClose={() => setSelectedId(null)}
          size="md"
        >
          {selectedId && <NotificationDetail eventId={selectedId} />}
        </Modal>
      )}
    </main>
  )
}
