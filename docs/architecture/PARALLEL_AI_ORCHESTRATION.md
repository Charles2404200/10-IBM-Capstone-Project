# Parallel AI Orchestration

## Authority model

The scenario, simulation, safety and assessment engines remain authoritative.
Providers generate language or structured recommendations only. Every provider
response must pass its task parser before the platform can use it.

## Execution policy

- Conversation, classification, client intelligence and evidence extraction run
  all available configured candidates concurrently and accept the first
  schema-valid response within the task deadline.
- Assessment runs candidates concurrently but prefers the first configured
  reasoning provider if it returns before the deadline. This retains assessment
  quality without serial provider latency.
- Invalid JSON, unavailable providers, circuit-open providers and exhausted
  quotas cannot win. The router waits for another valid candidate, then invokes
  the existing repair/fallback policy only when no candidate is acceptable.

## Operational controls

- `AI_PARALLEL_ENABLED`: permits a staged rollback to sequential validated routing.
- `AI_PARALLEL_MAX_CANDIDATES`: bounds per-request fan-out and provider cost.
- Per-provider quotas and circuit breakers remain active for each fan-out leg.
- `aiProviderExecutor` is isolated from WebSocket and meeting workers to avoid
  a blocked client session consuming the pool required to call providers.
- AI traces record the selected provider rather than a static configured model.

## Data safety

Task parsers enforce structured contracts. Persona outputs continue through
the existing fact, persona and simulation guards before state changes are
persisted. The router can never mutate engagement state or select an outcome.
