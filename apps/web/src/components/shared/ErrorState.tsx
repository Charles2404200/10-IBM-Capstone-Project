import { InlineNotification } from '@carbon/react'

interface Props {
  title?: string
  message?: string
}

export default function ErrorState({
  title = 'Something went wrong',
  message = 'Please try refreshing the page.',
}: Props) {
  return (
    <InlineNotification
      kind="error"
      title={title}
      subtitle={message}
      hideCloseButton
      style={{ maxWidth: '640px', margin: '2rem auto' }}
    />
  )
}
