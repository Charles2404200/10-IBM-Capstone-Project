import { describe, expect, it } from 'vitest'
import { createMeetingMessage, meetingSocketContract, toWebSocketUrl } from './useMeetingSocket'

describe('live meeting transport contract', () => {
  it('matches the backend STOMP endpoint and destination prefixes', () => {
    expect(toWebSocketUrl('https://api.example.test/base')).toBe('wss://api.example.test/ws')
    expect(toWebSocketUrl('', 'http://localhost:8080')).toBe('ws://localhost:8080/ws')
    expect(meetingSocketContract.topic('meeting-1')).toBe('/topic/meetings/meeting-1')
    expect(meetingSocketContract.sendDestination('meeting-1')).toBe('/app/meetings/meeting-1/send')
  })

  it('preserves the backend message and idempotency-key payload', () => {
    expect(createMeetingMessage('What outcome matters?', 'message-1')).toEqual({
      message: 'What outcome matters?',
      messageId: 'message-1',
    })
  })
})
