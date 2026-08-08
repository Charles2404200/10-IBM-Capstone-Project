import { Button, InlineNotification, Stack } from '@carbon/react'

interface Props {
  title?: string
  message?: string
  actionLabel?: string
  onAction?: () => void
}

export default function ErrorState({
  title = 'Something went wrong',
  message = 'Please try refreshing the page.',
  actionLabel,
  onAction,
}: Props) {
  return (
    <Stack gap={3} style={{ maxWidth: '640px', margin: '2rem auto' }}>
      <InlineNotification kind="error" title={title} subtitle={message} hideCloseButton />
      {actionLabel && onAction && <Button onClick={onAction}>{actionLabel}</Button>}
    </Stack>
  )
}
