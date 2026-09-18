import { NOTIFICATION_PREVIEW_CODE_POINTS } from './config'

export function notificationPreview(message: string, maxCodePoints = NOTIFICATION_PREVIEW_CODE_POINTS): string {
  const codePoints = Array.from(message)
  if (codePoints.length <= maxCodePoints) return message
  return `${codePoints.slice(0, Math.max(0, maxCodePoints - 1)).join('').trimEnd()}…`
}

