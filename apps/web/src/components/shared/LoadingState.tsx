import { Loading } from '@carbon/react'

interface Props {
  description?: string
}

export default function LoadingState({ description = 'Loading…' }: Props) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: '4rem' }}>
      <Loading description={description} withOverlay={false} />
    </div>
  )
}
