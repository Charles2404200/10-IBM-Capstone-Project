# Notification Feature — Frontend Plan

| | |
|---|---|
| **Owner** | Vince Tran (frontend) |
| **Backend counterpart** | Anubhav Patra |
| **Status** | Draft for review — **not approved, not implemented** |
| **Prepared** | 20 August 2026 |
| **Reviewed against** | `origin/main` @ `7b63143`, `origin/feature/notification-service` @ `7b63143` |

> This document proposes a plan. Every business requirement below that is not
> already true of the code is marked as an assumption or an open question.
> Nothing here should be read as a decision the team has taken.

---

## Contents

1. [Repository state at time of writing](#1-repository-state-at-time-of-writing)
2. [Objective](#2-objective)
3. [What already exists](#3-what-already-exists)
4. [The security finding that shapes this design](#4-the-security-finding-that-shapes-this-design)
5. [Scope — frontend (Vince)](#5-scope--frontend-vince)
6. [Scope — backend dependencies (Anubhav)](#6-scope--backend-dependencies-anubhav)
7. [Proposed architecture](#7-proposed-architecture)
8. [Draft message contract](#8-draft-message-contract)
9. [Connection lifecycle](#9-connection-lifecycle)
10. [Error cases](#10-error-cases)
11. [Proposed UI integration](#11-proposed-ui-integration)
12. [Proposed file structure](#12-proposed-file-structure)
13. [Test plan](#13-test-plan)
14. [Open questions for Friday](#14-open-questions-for-friday)
15. [Estimate](#15-estimate)
16. [Definition of done — plan stage](#16-definition-of-done--plan-stage)

---

## 1. Repository state at time of writing

Checked against the remote, not assumed:

| Branch | Commit | Ahead of `main` |
|---|---|---|
| `origin/main` | `7b63143` | — |
| `origin/feature/notification-service` | `7b63143` | **0 commits** |

`feature/notification-service` currently points at exactly the same commit as
`main`. **No backend notification work has been pushed yet.** The branch tip was
authored by Charles as part of the PR #19 merge, not by Anubhav.

This matters for planning: there is no backend contract to read, so every part
of the payload and destination below is provisional by necessity, not by
caution.

---

## 2. Objective

Maintain one authenticated, long-lived STOMP-over-WebSocket connection from the
browser so the backend can **push** notification events to the user as they
happen, without the frontend repeatedly asking whether anything has changed.

The distinction matters for review, so stated plainly:

- **Polling** — the client asks "anything new?" on a timer. Cost and latency
  both scale with the polling interval, and most requests return nothing.
- **WebSocket push** — the client connects once and subscribes once. The server
  sends when there is something to send. There is no timer and no empty request.

After `SUBSCRIBE`, the frontend is passive: it waits. It does not request
notifications.

---

## 3. What already exists

This project **already has a working STOMP-over-WebSocket implementation** for
Live Meetings. The notification client should reuse it, not duplicate it.

### Frontend

`apps/web/src/api/hooks/useMeetingSocket.ts` establishes the conventions:

| Concern | Existing approach |
|---|---|
| Library | `@stomp/stompjs` `^7.3.0` — already a dependency |
| Endpoint | `new URL('/ws', VITE_API_BASE_URL)`, protocol swapped to `ws:`/`wss:` |
| Auth | `Authorization: Bearer <jwt>` in the STOMP `CONNECT` frame headers |
| Token source | `useAuthStore.getState().token` — read imperatively, not subscribed |
| Reconnect | `reconnectDelay: 3000` |
| Heartbeat | `heartbeatIncoming` / `heartbeatOutgoing`, both `10000` |
| Errors | `onStompError`, `onWebSocketClose` |
| Payload | `JSON.parse(frame.body)` into a discriminated union on `type` |
| Teardown | `client.deactivate()` in the effect cleanup |

### Backend

`apps/api/.../meeting/infrastructure/realtime/`

| File | Role |
|---|---|
| `WebSocketConfig.java` | Registers `/ws`; simple broker on `/topic`; app prefix `/app` |
| `StompAuthChannelInterceptor.java` | Validates the JWT on `CONNECT`, attaches the `Principal` |
| `MeetingSubscriptionInterceptor.java` | Authorises `SUBSCRIBE` against meeting ownership |
| `MeetingSocketController.java` | `@MessageMapping` handlers |

`WebSocketConfig`'s own Javadoc anticipates this exact situation:

> Deliberately scoped to the `meeting` module rather than `shared`: this is
> currently the only feature needing bidirectional real-time messaging. If a
> second feature needs the same transport later, extract this + the
> auth/ownership interceptors into `shared.config` at that point.

**Notifications are that second feature.** The extraction is a backend decision
for Anubhav and Charles; it is recorded here as a recommendation only.

### Documentation gap

`WebSocketConfig` references `docs/architecture/LIVE_MEETING_REALTIME.md`. That
file does not exist in the repository. Worth raising separately — it is the
document this plan would otherwise have been written against.

---

## 4. The security finding that shapes this design

This is the most important result of the inspection and should be read before
any destination is agreed.

`MeetingSubscriptionInterceptor.preSend` authorises only destinations matching
`^/topic/meetings/([0-9a-fA-F-]{36})$`. Anything else is explicitly allowed
through:

```java
Matcher matcher = destination != null ? MEETING_TOPIC.matcher(destination) : null;
if (matcher == null || !matcher.matches()) {
    // Not a per-meeting topic (or malformed) — nothing for this interceptor to authorize.
    return message;
}
```

**Consequence:** if notifications are published to a destination such as
`/topic/notifications`, then *every authenticated user* can subscribe to it and
receive *every other user's* notifications. Authentication is enforced;
authorisation is not.

There are two ways to avoid this, and the choice belongs to the backend:

**Option A — Spring user destinations (recommended).**
The server sends with `convertAndSendToUser(principal, "/queue/notifications", payload)`
and the client subscribes to `/user/queue/notifications`. Spring rewrites the
destination per session, so one user cannot subscribe to another's.

> Blocker: this does **not** work with the current configuration.
> `enableSimpleBroker("/topic")` does not include `/queue`, so user destinations
> would never be delivered. `WebSocketConfig` would need
> `enableSimpleBroker("/topic", "/queue")` and, if a non-default prefix is
> wanted, `setUserDestinationPrefix`.

**Option B — per-user topic with an authorisation interceptor.**
Destination `/topic/notifications/{userId}`, plus a new interceptor (or an
extension of the existing one) asserting the `{userId}` matches the
authenticated principal.

> Risk: if the interceptor is forgotten or its regex does not match, the
> destination silently falls through to "allowed", exactly as above. Option A
> fails safe; Option B fails open.

Either way, **the frontend cannot make this safe on its own.** A client can
subscribe to whatever it likes; only the server can refuse. This is listed as a
backend dependency in §6 and as open question Q6.

---

## 5. Scope — frontend (Vince)

Proposed. Item 10 is explicitly conditional.

1. Establish one authenticated STOMP connection per signed-in session.
2. Subscribe to the agreed notification destination once connected.
3. Receive notification frames.
4. Parse defensively — a malformed frame must not break the app.
5. Hold received notifications in client state (shape depends on Q9–Q11).
6. Reconnect automatically after a dropped connection, and re-subscribe.
7. Surface connection and STOMP errors without breaking the page.
8. Disconnect cleanly on logout and on unmount.
9. Guarantee exactly one client and one subscription per user session,
   including under React StrictMode's double-invoked effects in development.
10. **Notification UI — only after the UX in §11 is approved.**
11. Frontend tests per §13.

### Explicitly out of frontend scope

- Deciding which events produce notifications.
- Deciding who receives them.
- Persistence, history, or replay of missed notifications.
- Any guarantee of delivery. See §10.

---

## 6. Scope — backend dependencies (Anubhav)

The frontend cannot start integration work until these exist. Listed as
dependencies, not as instructions.

| # | Dependency | Blocks |
|---|---|---|
| B1 | Notification event creation — which domain events publish | Everything |
| B2 | The exact STOMP destination string | Subscription code |
| B3 | Subscription authorisation for that destination (§4) | Safe rollout |
| B4 | Broker configuration if user destinations are chosen (`/queue`) | Option A |
| B5 | Recipient routing — how the server knows who a notification is for | B2, B3 |
| B6 | Final payload contract (§8) | Parsing and tests |
| B7 | Persistence, if notifications must survive a page reload | Q9–Q11 |
| B8 | History endpoint, if missed messages must be recoverable | Q11 |
| B9 | Backend tests for auth and routing | Sign-off |

### Recommendation recorded for Anubhav and Charles, not actioned here

Extract `/ws`, `StompAuthChannelInterceptor` and the broker configuration from
`meeting.infrastructure.realtime` into `shared.config`, as `WebSocketConfig`'s
own Javadoc proposes. Keep the meeting-specific subscription interceptor where
it is. This avoids a second endpoint and a second copy of the auth logic.

**Do not create a second WebSocket endpoint** such as `/notifications/ws`. One
socket can carry many subscriptions; a second endpoint means a second
connection, a second handshake and a second copy of authentication.

---

## 7. Proposed architecture

```text
  Authenticated user (JWT in authStore)
              │
              │  new Client({ brokerURL: wss://…/ws,
              │               connectHeaders: { Authorization: Bearer <jwt> } })
              ▼
  ┌───────────────────────────────────────────┐
  │  WebSocket handshake  →  /ws              │
  └───────────────────────────────────────────┘
              │
              │  STOMP CONNECT  (JWT travels here, not in the URL)
              ▼
  ┌───────────────────────────────────────────┐
  │  StompAuthChannelInterceptor              │  ← validates JWT, attaches Principal
  └───────────────────────────────────────────┘
              │
              │  STOMP SUBSCRIBE  → <destination, TBC — see Q4>
              ▼
  ┌───────────────────────────────────────────┐
  │  Subscription authorisation               │  ← DOES NOT EXIST YET for
  │                                           │    notification destinations (§4)
  └───────────────────────────────────────────┘
              │
              ▼
        (connection now idle — no polling)
              │
   backend domain event occurs
              │
              ▼
  ┌───────────────────────────────────────────┐
  │  server pushes MESSAGE frame              │
  └───────────────────────────────────────────┘
              │
              ▼
   frontend handler → JSON.parse → validate → notification store → UI
```

### Two different things both called "topic"

These are unrelated and must not be conflated. This ambiguity is open question
Q3 and the single most likely source of a wasted sprint.

| Term | Meaning | Example |
|---|---|---|
| **STOMP destination** | The address subscribed to. Transport-level. Chosen by the backend, authorised by the backend. | `/topic/meetings/{uuid}` |
| **Notification `topic` field** | A value *inside* the JSON payload. Application-level. Probably a title or category. | `"Meeting scheduled"` |

Anubhav's message described a JSON object with `topic` and `message`. Because
that `topic` sits inside the payload, reading (B) — a title or subject line —
is the more natural interpretation. **It has not been confirmed and this plan
does not assume it.**

---

## 8. Draft message contract

**PROVISIONAL — REQUIRES TEAM APPROVAL.** This is only what Anubhav has
described so far.

```ts
interface NotificationMessage {
  /** Meaning unconfirmed — see Q3. Assumed here to be a human-readable title. */
  topic: string
  /** Body text. */
  message: string
}
```

### Potential future fields, if the team decides they are required

Listed for the Friday discussion. **None of these is a requirement today.**

| Field | Would be needed if | Related question |
|---|---|---|
| `id` | Duplicates must be suppressed, or read state tracked per item | Q10 |
| `timestamp` | The UI shows when something happened, or orders a list | Q12 |
| `read` | Read/unread is required | Q10 |
| `severity` | The UI distinguishes info from warning | Q14 |
| `recipientId` | The client must filter — **preferably not**; routing belongs server-side | Q5, Q6 |
| `type` | Different notifications render differently | Q12 |
| `actionUrl` | Clicking a notification navigates somewhere | Q13 |

The frontend should treat unknown fields as ignorable, so the backend can add
fields without breaking the client.

---

## 9. Connection lifecycle

```text
authenticated                → activate one client
CONNECT accepted             → subscribe
MESSAGE received             → parse → validate → update store → UI
connection dropped           → stompjs reconnects after reconnectDelay
reconnected                  → onConnect fires again → re-subscribe
                               (must not accumulate subscriptions)
token becomes invalid        → see Q15 — behaviour undecided
logout                       → unsubscribe, deactivate, clear state
app unmount                  → deactivate
```

### Exactly one connection

Two hazards, both real:

1. **React StrictMode** double-invokes effects in development. An effect that
   calls `client.activate()` without a correct cleanup produces two sockets.
   The existing meeting hook is safe because its cleanup calls `deactivate()`;
   the notification client must do the same.
2. **A global connection in a component effect** re-runs whenever that
   component remounts. Because notifications are app-wide rather than
   page-scoped, the client should live at a single mount point — a provider
   near `AppShell`, or a module-level singleton guarded by a reference count —
   rather than in each component that wants to read notifications.

Reading the token via `useAuthStore.getState().token` rather than as a
subscribed hook value keeps the effect from re-running on unrelated store
changes. This is the pattern `useMeetingSocket` already uses.

---

## 10. Error cases

| Case | Proposed behaviour |
|---|---|
| Server unavailable at startup | `stompjs` retries every `reconnectDelay`. No error surfaced to the user; the app works without notifications. |
| Connection timeout | Same as above. |
| STOMP broker error | `onStompError` — log, surface a non-blocking indicator at most. |
| Missing JWT | Do not activate. Notifications require a signed-in user. |
| Invalid or expired JWT at CONNECT | Backend rejects; `onStompError` fires. See Q15. |
| Token expires *during* a live connection | **Undecided — Q15.** The socket stays open because the JWT is only checked at `CONNECT`. |
| Unauthorised subscription | Backend raises; `onStompError` fires. Do not retry blindly. |
| Unexpected close | `onWebSocketClose` — mark disconnected, let reconnect handle it. |
| Reconnect | Automatic. `onConnect` runs again, so re-subscription must be idempotent. |
| Malformed JSON | `try/catch` around `JSON.parse`. Log and drop the frame. **Must not throw** — an uncaught error in a STOMP callback can tear down the app. |
| Missing `topic` or `message` | Validate before use. Drop, or render with a safe fallback. |
| Duplicate event | Cannot be de-duplicated reliably without an `id` (Q10). |
| Unmount / logout | `deactivate()`, drop state. |
| Network offline then online | Handled by reconnect. |
| Backend restart | Handled by reconnect. Anything sent while disconnected is lost — see below. |

### Delivery guarantee — stated plainly

**A WebSocket connection cannot deliver messages generated while the client was
disconnected.** The simple in-memory broker holds nothing for an absent
subscriber. If a user closes the tab, or is offline for thirty seconds, any
notification produced in that window is gone.

If missed notifications must be recoverable, that requires **backend
persistence plus a history endpoint the client fetches on connect** (B7, B8).
This plan does not assume that requirement, and the frontend cannot create it.

---

## 11. Proposed UI integration

**PROPOSED UX — NOT AN APPROVED REQUIREMENT.** Nothing in §11 should be built
before sign-off.

`apps/web/src/components/layout/AppShell.tsx` already renders a Carbon
`<HeaderGlobalBar>` containing a single `<HeaderGlobalAction>` (sign out). That
is where a notification control would naturally sit.

```text
<HeaderGlobalBar>
  <HeaderGlobalAction aria-label="Notifications">   ← proposed
     <Notification icon /> + unread count badge
  </HeaderGlobalAction>
  <HeaderGlobalAction aria-label="Sign out" />      ← exists today
</HeaderGlobalBar>
```

Options, in increasing order of cost:

| Option | Needs | Notes |
|---|---|---|
| Toast only | Nothing persisted | Cheapest. Nothing to click, nothing to miss-manage. |
| Bell + unread badge | Client-side count | Needs a rule for when the count resets. |
| Bell + dropdown panel | A list in state | Needs `timestamp` to order sensibly. |
| Full notification centre | Persistence + history endpoint | Backend work (B7, B8). |

Accessibility, whichever is chosen: a notification that only appears as a
colour or a silent toast is invisible to a screen reader. Carbon's
`ToastNotification` with a live region, or an `aria-live` container, is the
minimum.

---

## 12. Proposed file structure

Following the conventions already in this repository — hooks in
`src/api/hooks/`, stores in `src/store/`, feature components in
`src/components/` — rather than introducing a new layout.

```text
apps/web/src/
├── api/hooks/
│   └── useNotificationSocket.ts      connection + subscription + parsing
├── store/
│   └── notificationStore.ts          zustand, matching authStore's style
└── components/notifications/         ONLY IF UI IS APPROVED
    ├── NotificationBell.tsx
    ├── NotificationPanel.tsx
    └── NotificationItem.tsx
```

Deliberately **not** proposed:

- A separate `api/realtime/` directory. There is only one realtime hook today
  and it lives in `api/hooks/`; a second one does not justify a new layer.
- Any change to `useMeetingSocket.ts`. If the two later share connection code,
  extract it then, with two working call sites to generalise from.

---

## 13. Test plan

Vitest + jsdom + Testing Library, matching the existing setup
(`vite.config.ts` → `test`, `src/test/setup.ts`). No new framework.

`@stomp/stompjs` will need `vi.mock` — there is no precedent for this in the
repo yet, so a small fake `Client` capturing `connectHeaders`, `subscribe` and
`deactivate` is the first thing to build.

| # | Test | Asserts |
|---|---|---|
| 1 | Connects when authenticated | `activate()` called once |
| 2 | Does not connect when signed out | `activate()` not called |
| 3 | Sends the JWT in CONNECT headers | `connectHeaders.Authorization === 'Bearer <token>'` |
| 4 | Never puts the token in the URL | `brokerURL` contains no token substring |
| 5 | Subscribes after connect | `subscribe` called with the agreed destination |
| 6 | Parses a valid notification | Store contains the payload |
| 7 | Survives malformed JSON | No throw; store unchanged |
| 8 | Survives a missing required field | No throw; frame dropped or defaulted |
| 9 | Handles `onStompError` | Error state set, app still renders |
| 10 | Handles `onWebSocketClose` | Marked disconnected |
| 11 | Re-subscribes on reconnect | Exactly one active subscription after a second `onConnect` |
| 12 | No duplicate clients | One `activate()` across a StrictMode double-mount |
| 13 | Deactivates on unmount | `deactivate()` called |
| 14 | Deactivates and clears on logout | `deactivate()` called, store emptied |
| 15 | UI renders an incoming notification | **Only if §11 is approved** |

Tests 5, 6, 8 and 15 cannot be finalised until Q3, Q4 and Q12 are answered.

---

## 14. Open questions for Friday

Grouped by who is blocked by the answer.

### Blocks any frontend code being written

| # | Question |
|---|---|
| Q1 | Is notification traffic server → client only? |
| Q2 | Does the frontend ever *publish* a notification request to the backend? |
| Q3 | Does `topic` mean the notification's title/subject, or the STOMP destination? |
| Q4 | What is the exact STOMP subscription destination string? |
| Q5 | Are notifications user-specific, or broadcast to everyone signed in? |

### Blocks safe rollout

| # | Question |
|---|---|
| Q6 | If user-specific, which routing strategy — Spring user destinations, or a per-user topic with a new authorisation interceptor? (§4) |
| Q15 | What should happen when the token expires while the connection is open? Currently the JWT is validated only at `CONNECT`, so an open socket keeps working. |

### Blocks scope and estimate

| # | Question |
|---|---|
| Q7 | Which application events generate notifications? |
| Q8 | Are notifications ephemeral realtime feedback only? |
| Q9 | Or must they persist across a page reload? |
| Q10 | Are read/unread states required? |
| Q11 | Must a user receive notifications generated while they were offline? (If yes → backend persistence + history endpoint; a WebSocket alone cannot do this.) |
| Q12 | Which UX: toast only, bell, unread badge, dropdown, full centre, or none yet? |
| Q13 | Does clicking a notification navigate somewhere? |
| Q14 | Is priority/severity required? |

### Additional questions raised by the code inspection

| # | Question |
|---|---|
| Q16 | Should `/ws` and the auth interceptor be extracted to `shared.config` now, as `WebSocketConfig`'s own Javadoc proposes? (Backend decision — recorded, not actioned.) |
| Q17 | Where should Vince's frontend work live: a branch off `feature/notification-service`, or a separate `feature/notification-client` branch merged into it? |
| Q18 | `WebSocketConfig` references `docs/architecture/LIVE_MEETING_REALTIME.md`, which does not exist. Should it be written? |

---

## 15. Estimate

Student capstone sprint, realistic rather than padded. Hours are effort, not
elapsed time.

| Task | Hours | Depends on |
|---|---|---|
| Research and architecture — inspect existing STOMP client, backend interceptors, broker config *(done, this document)* | 3 | — |
| Client foundation — connection, subscription, single-instance guard | 4 | Q4 |
| Auth and lifecycle — CONNECT headers, reconnect, re-subscribe, logout teardown | 3 | Q4, Q15 |
| Message handling — parse, validate, notification store | 3 | Q3, Q6 |
| Test harness — `@stomp/stompjs` mock (no precedent in repo) | 2 | — |
| Tests 1–14 | 4 | above |
| UX integration — bell, badge, panel | 6 | **Q12; only if approved** |
| UI tests (15) | 2 | Q12 |
| Documentation and handoff evidence | 2 | — |
| **Total without UI** | **21** | |
| **Total with UI** | **29** | |

Blocked until the Friday answers: everything except the test harness. Writing
subscription code against a guessed destination would be rework, not progress.

---

## 16. Definition of done — plan stage

| Criterion | Status |
|---|---|
| Existing WebSocket architecture inspected | Done — §3 |
| Frontend scope documented | Done — §5 |
| Backend dependencies documented | Done — §6 |
| Authentication approach documented | Done — §3, §7 |
| Error and reconnect strategy documented | Done — §9, §10 |
| Provisional data contract documented | Done — §8, marked provisional |
| Open questions explicit | Done — §14 |
| Test plan exists | Done — §13 |
| Implementation tasks estimated | Done — §15 |
| No unapproved business requirement treated as fact | Done — all such items are in §14 |

**This plan is not approved.** It becomes actionable when Q1–Q6 and Q12 are
answered and Charles signs off.
