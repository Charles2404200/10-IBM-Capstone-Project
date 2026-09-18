export class BoundedEventDeduplicator {
  private readonly seen = new Map<string, number>()

  constructor(private readonly capacity: number) {
    if (!Number.isInteger(capacity) || capacity < 1) {
      throw new Error('Deduplication capacity must be a positive integer')
    }
  }

  remember(eventId: string): boolean {
    if (this.seen.has(eventId)) return false
    this.seen.set(eventId, Date.now())
    while (this.seen.size > this.capacity) {
      const oldest = this.seen.keys().next().value as string | undefined
      if (!oldest) break
      this.seen.delete(oldest)
    }
    return true
  }

  get size() {
    return this.seen.size
  }
}

