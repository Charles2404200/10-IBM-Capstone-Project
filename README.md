# IBM AI-Powered Consulting Simulation

RMIT Capstone × IBM — RPG-style consultant training platform.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + TypeScript + Vite + IBM Carbon |
| Backend | Java 21 + Spring Boot 3.x (modular monolith) |
| Database | PostgreSQL 16 + pgvector |
| AI | IBM watsonx.ai Granite |
| Auth | JWT (OIDC-ready) |

## Quick Start (Local Dev)

### Prerequisites
- Docker Desktop
- Node 20+
- (Java 21 + Gradle are only needed if you want to run the API outside
  Docker — the recommended workflow below doesn't require them.)

### 1. Clone and configure
```bash
cp .env.example .env
# Edit .env — set JWT_SECRET (openssl rand -hex 64) and your Supabase
# Postgres credentials (SPRING_DATASOURCE_URL/USERNAME/PASSWORD).
```

### 2. Start the backend (containerized)
```bash
docker compose up -d
# API:     http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
# Health:  http://localhost:8080/actuator/health/liveness
```
This builds a multi-stage, layered Docker image for the Spring Boot API
(see `apps/api/Dockerfile`) and starts it with all required environment
variables loaded automatically from `.env` — no manual `./gradlew bootRun`,
env exporting, or port-conflict juggling needed. The database is remote
Supabase Postgres, so no local Postgres container is started.

`redis` / `kafka` are scaffolded for future phases and are **not** started
by default; run `docker compose --profile extended up -d` if you need them.

### Observability

The API emits structured JSON logs to stdout and Prometheus metrics from a
protected Actuator endpoint. Start the separate local stack with:

```bash
docker compose --profile observability up -d --build
```

| Service | URL |
|---|---|
| Grafana | http://localhost:3001 |
| Prometheus | http://localhost:9090 |
| Loki | http://localhost:3100/ready |

Grafana provisions Prometheus and Loki automatically. Useful Loki queries:

```logql
{service="consulting-simulation-api", event="AUTHENTICATION_REJECTED"}
{service="consulting-simulation-api", event="HTTP_REQUEST_COMPLETED"} | json | latencyMs > 1000
{service="consulting-simulation-api"} | json | requestId="<request-id>"
```

Every HTTP response includes `X-Request-ID`; use it to correlate frontend
failures with API logs. AI metrics use the `consulting_ai_provider_*` prefix
and only low-cardinality provider/task/outcome/fallback labels.

On Railway, stdout JSON appears immediately in the API service Deploy Logs.
Set a strong `OBSERVABILITY_TOKEN` secret on the API before configuring a
separate Prometheus service to scrape `/actuator/prometheus`. Keep Grafana,
Prometheus, and Loki as separate services; the application container has no
vendor-specific log or metric transport.

### Railway Team Observability

The repository includes production deployment roots under
`observability/railway/`. Deploy the four services in the **same Railway
project and environment**:

| Railway service | Repository root | Public? | Persistent volume |
|---|---|---:|---|
| API | `apps/api` | Yes | N/A |
| Loki | `observability/railway/loki` | No | `/loki` |
| Prometheus | `observability/railway/prometheus` | No | `/prometheus` |
| Grafana | `observability/railway/grafana` | Yes | `/var/lib/grafana` |

Give each service the fixed `PORT` shown below. This makes Railway private
network references stable and avoids hard-coded public endpoints:

```dotenv
# API
PORT=8080
OBSERVABILITY_TOKEN=<generate-a-long-random-secret>
LOKI_ENABLED=true
LOKI_PUSH_URL=http://${{Loki.RAILWAY_PRIVATE_DOMAIN}}:${{Loki.PORT}}/loki/api/v1/push

# Loki
PORT=3100
LOKI_RETENTION_PERIOD=336h

# Prometheus
PORT=9090
API_METRICS_TARGET=${{API.RAILWAY_PRIVATE_DOMAIN}}:${{API.PORT}}
OBSERVABILITY_TOKEN=${{API.OBSERVABILITY_TOKEN}}
PROMETHEUS_RETENTION_TIME=15d

# Grafana
PORT=3000
PROMETHEUS_URL=http://${{Prometheus.RAILWAY_PRIVATE_DOMAIN}}:${{Prometheus.PORT}}
LOKI_URL=http://${{Loki.RAILWAY_PRIVATE_DOMAIN}}:${{Loki.PORT}}
GF_SECURITY_ADMIN_USER=team-admin
GF_SECURITY_ADMIN_PASSWORD=<generate-a-strong-unique-password>
GF_USERS_ALLOW_SIGN_UP=false
GF_AUTH_ANONYMOUS_ENABLED=false
```

`OBSERVABILITY_TOKEN` and `GF_SECURITY_ADMIN_PASSWORD` must be Railway secrets.
Do not create public domains for Loki or Prometheus. Generate a domain only for
Grafana, then share its authenticated URL with teammates. Their useful Explore
query is `{service="consulting-simulation-api"}`; add
`| json | requestId="<X-Request-ID>"` to trace one failed browser request.

Swagger is already protected by the normal API authentication scheme and is
available online at `https://<api-public-domain>/swagger-ui.html`. Generate a
public API domain in Railway if one does not already exist, then share that URL
with the team. The OpenAPI JSON is at `/api-docs`.

### 3. Run the frontend (native, hot reload)
```bash
cd apps/web
npm install
npm run dev
# Web: http://localhost:3000 (Vite proxies /api to localhost:8080)
```

### Transactional email and account recovery

Registration creates an inactive-to-login account until its email address is
confirmed. The API issues a high-entropy, single-use verification credential;
only its SHA-256 digest is persisted. Password-reset credentials use the same
pattern, expire after 15 minutes by default, and are invalidated after use.

Configure the following values in local `.env` or as Railway service variables:

```dotenv
RESEND_API_KEY=re_xxxxxxxxx
RESEND_FROM=IBM Consulting Simulation <no-reply@mail.ibm-simulator.com>
RESEND_REPLY_TO=support@mail.ibm-simulator.com
APP_PUBLIC_WEB_URL=https://your-web-domain.com
```

Verify `mail.ibm-simulator.com` in Resend and set `RESEND_FROM` to an address
on that domain before production delivery. Never commit an API key. If a key
is exposed, revoke it in Resend and create a replacement.

The web flow is available at `/verify-email`, `/forgot-password`, and
`/reset-password`. Product-facing copy and HTML are centralized in
`apps/api/src/main/java/com/ibm/consulting/sim/shared/email/template/TransactionalEmailTemplates.java`;
the Resend adapter only transports the rendered message.

The additive `V38` migration marks all existing users as already verified, so
deploying this capability does not interrupt current learner or administrator
accounts.

### Everyday workflow
Once set up, day-to-day development is just:
```bash
docker compose up -d      # backend (idempotent — only rebuilds on change)
cd apps/web && npm run dev
```
To rebuild the API image after a backend code change:
```bash
docker compose up -d --build api
```

### Optional: containerized frontend (prod-build parity check)
```bash
docker compose --profile containerized-web up -d --build web
# Web: http://localhost:3000 (nginx-served production build)
```


## Project Structure

```
consulting-simulation/
├── apps/
│   ├── web/                    # React + TypeScript + Vite
│   └── api/                    # Spring Boot modular monolith
│       └── src/main/java/.../
│           ├── shared/         # Cross-cutting primitives
│           ├── identity/       # Auth, users
│           ├── scenario/       # Scenario catalogue, personas
│           ├── engagement/     # Lifecycle state machine
│           ├── lead/           # Lead pipeline + research
│           ├── outreach/       # Cold outreach workflow
│           ├── meeting/        # Live AI client meeting
│           ├── proposal/       # Proposal studio
│           ├── assessment/     # Competency scoring
│           ├── portfolio/      # Longitudinal progress
│           ├── ai/             # watsonx gateway
│           └── audit/          # Immutable event log
├── docs/adr/                   # Architecture Decision Records
├── infrastructure/docker/
└── .github/workflows/
```

## Module Boundaries

Each module follows `api → application → domain ← infrastructure`.  
Domain code has zero Spring/JPA dependencies.

## Delivery Phases

| Phase | Weeks | Status |
|---|---|---|
| 0 — Setup | Pre W1 | ✅ |
| 1 — Foundation | 1–2 | ✅ |
| 2 — Core Vertical Slice | 3–5 | ✅ |
| 3 — Full Integration | 6–8 | 🔜 |
| 4 — Realism & Balance | 9–10 | 🔜 |
| 5 — Demo Hardening | 11–12 | 🔜 |
