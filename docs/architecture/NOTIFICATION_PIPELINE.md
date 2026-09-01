# Notification delivery pipeline

The notification service uses a PostgreSQL transactional outbox, Kafka, a
transactional consumer inbox, and WebSocket fan-out. PostgreSQL remains the
source of truth; WebSocket delivery is an after-commit convenience channel and
clients can recover recent messages through `GET /api/v1/notifications?limit=50`.

## Delivery guarantees

- The admin transaction writes one ordered outbox event per target role.
- `FOR UPDATE SKIP LOCKED` claiming allows multiple API replicas to dispatch
  concurrently without sharing an in-memory lock.
- Only the first unpublished event in each ordering key is dispatchable. Kafka
  receives that ordering key as its record key, preserving order per role.
- Priority is applied only after stream-head eligibility is established. An
  older normal event therefore cannot be overtaken by a later critical event
  on the same ordering key.
- A dispatch batch starts Kafka sends concurrently. Durable completion updates
  run on a bounded executor and are guarded by a unique claim token, so a stale
  worker cannot complete a reclaimed lease.
- Producer idempotence reduces broker retry duplicates. Since a crash can still
  occur between Kafka acknowledgement and the outbox update, consumers provide
  the final guarantee with a `(consumer_group, event_id)` inbox primary key.
- Business persistence and the inbox claim commit in one database transaction.
  The effective contract is at-least-once transport with exactly-once business
  effects per consumer group.

## Failure handling

Transient producer failures use capped exponential backoff with jitter. A
processing lease older than the producer delivery timeout plus a safety margin
is reclaimed. Consumer failures retry a bounded number of times and then go to
the configured DLT. The DLT monitor reads opaque bytes through its own listener
factory so malformed JSON cannot recursively produce `.DLT.DLT` records.

Published outbox rows and processed inbox claims are deleted in bounded batches.
Retention must exceed the maximum expected Kafka replay/redelivery window. Do
not reduce inbox retention below that window or an old replay may be processed
again.

## Scaling

Scale API replicas horizontally; database row locks coordinate outbox work.
Increase `KAFKA_NOTIFICATION_PARTITIONS` before increasing
`KAFKA_NOTIFICATION_CONCURRENCY`. In production, use at least three Kafka
brokers and set `KAFKA_NOTIFICATION_REPLICAS=3`; the local single-broker profile
must remain at one replica.

Tune these controls together:

| Variable | Default | Purpose |
|---|---:|---|
| `KAFKA_OUTBOX_BATCH_SIZE` | 100 | Concurrent sends claimed per poll |
| `OUTBOX_COMPLETION_POOL_SIZE` | 8 | Concurrent durable acknowledgement updates |
| `OUTBOX_COMPLETION_QUEUE_CAPACITY` | 500 | Bounded completion burst absorption |
| `KAFKA_OUTBOX_RETRY_MAX_DELAY_MS` | 300000 | Maximum broker-outage retry delay |
| `KAFKA_INBOX_RETENTION_DAYS` | 14 | Duplicate-detection replay window |

Alert on DLT records, repeated stale-lease recovery, old pending outbox rows,
and a sustained outbox backlog. Never log notification payloads from the DLT.

## Priority scheduling

The generic outbox supports `LOW`, `NORMAL`, `HIGH`, and `CRITICAL` scheduling
weights. Application code selects this infrastructure priority through
`OutboxOptions`; legacy callers default to `NORMAL`. Administrator-authored
notifications expose the separate domain choices `NORMAL`, `IMPORTANT`, and
`CRITICAL`. `IMPORTANT` maps to infrastructure `HIGH`, while `CRITICAL` remains
reserved for emergencies. Clients never control raw infrastructure weights.

The claim query ranks eligible rows by priority descending, then creation time
and event id for deterministic FIFO behavior within a priority. Ordered events
remain ineligible while any lower sequence in their ordering stream is not
published, including when that predecessor is waiting to retry.

Prometheus counters expose dispatch success, failure, and retry activity with a
single bounded `priority` label. Event ids, users, and ordering keys are never
metric labels.

Weighted fairness is intentionally deferred. If sustained critical traffic can
starve lower priorities in production, a P2 scheduler can reserve dispatch
capacity using the documented target split: critical 50%, high 30%, normal
15%, and low 5%. That future policy must continue operating only on eligible
stream heads.
