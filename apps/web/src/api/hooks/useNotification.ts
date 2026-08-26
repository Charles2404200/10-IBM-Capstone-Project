import { useCallback, useEffect, useRef, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import type { NotificationObject } from '@/api/types'
import { useAuthStore } from '@/store/authStore'

function toWebSocketUrl(baseUrl: string, role: string): string {
  const url = new URL(`/ws/notifications/${encodeURIComponent(role)}`, baseUrl || window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function toRoleSlug(role: string): string {
  return role.toLowerCase().replaceAll('_', '-')
}

export function useNotification() {
  const [notification, setNotification] = useState<NotificationObject | null>(null)
  const clientRef = useRef<Client | null>(null)
  const token = useAuthStore((state) => state.token)
  const role = useAuthStore((state) => state.role)
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

  useEffect(() => {
    if (!token || !role) return

    const roleSlug = toRoleSlug(role)

    const client = new Client({
      brokerURL: toWebSocketUrl(baseUrl, roleSlug),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    })

    client.onConnect = () => {
      client.subscribe(`/topic/notifications/${roleSlug}`, (message: IMessage) => {
        try {
          setNotification(JSON.parse(message.body) as NotificationObject)
        } catch {
          // Ignore malformed frames while keeping the subscription alive.
        }
      })
    }

    client.activate()
    clientRef.current = client

    return () => {
      void client.deactivate()
      clientRef.current = null
    }
  }, [baseUrl, role, token])

  const dismissNotification = useCallback(() => {
    setNotification(null)
  }, [])

  return {
    notification,
    dismissNotification,
  }
}
