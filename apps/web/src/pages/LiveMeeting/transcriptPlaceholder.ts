/**
 * Whether the "begin with a question" placeholder should be on screen.
 *
 * `turns` is only populated once an exchange is persisted, so on the learner's
 * very first message it is still empty while the pending and streaming bubbles
 * are already rendering. Keying off its length alone put the placeholder beside
 * the learner's own question, and told them to begin something they had begun.
 */
export function shouldShowPlaceholder(turnCount: number, pendingMessage: string | null, isStreaming: boolean): boolean {
  return turnCount === 0 && !pendingMessage && !isStreaming
}
