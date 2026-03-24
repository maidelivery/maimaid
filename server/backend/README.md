# maimaid backend (Hono + Prisma + DI)

Self-hosted unified serverless-style backend for:

- maimaid app canonical API (`/v1/*`)
- user-initiated import from Diving Fish / LXNS into canonical data model

Current phase targets **maimai** only.

## Stack

- Runtime: Node.js (works with Bun-compatible APIs)
- Framework: Hono
- DI: tsyringe
- ORM: Prisma
- DB: PostgreSQL (`pg_cron` extension enabled in migration)
- Storage: S3-compatible object storage (pre-signed upload URL)

## Quick start

1. Copy env file:

```bash
cp server/backend/.env.example server/backend/.env
```

2. Install dependencies:

```bash
pnpm install
```

3. Generate Prisma client:

```bash
pnpm --filter backend prisma:generate
```

4. Run migrations:

```bash
pnpm run migrate:server
```

5. Start server:

```bash
pnpm run dev:server
```

## Local test stack (Docker Compose)

This repo now includes a ready-to-run local stack:

- PostgreSQL 18.3 + `pg_cron`
- MinIO (S3-compatible storage)
- backend API service (Node 25.8.1)

1. Prepare docker env:

```bash
cp server/backend/.env.docker.example server/backend/.env.docker
```

2. Start stack:

```bash
cd server/backend
pnpm run docker:up
```

3. Verify:

- API: `http://localhost:8787/health`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`
- Postgres host port: `localhost:54329`

4. Point iOS to local backend:

- Set `BACKEND_URL = http://localhost:8787` in `ios/Config/Secrets.xcconfig`

5. Stop stack:

```bash
cd server/backend
pnpm run docker:down
```

## Monorepo scripts

At repository root:

- `pnpm run dev:server` – start backend in watch mode
- `pnpm run build:server` – compile backend
- `pnpm run test:server` – run backend tests
- `pnpm run migrate:server` – deploy Prisma migrations

## Environment variables

See `server/backend/.env.example`:

- Network: `HOST`, `PORT`
- Auth: `JWT_*`
- Database: `DATABASE_URL`
- Catalog source: `CATALOG_SOURCE_URL` (default points to cloudfront `data.json`)
- S3: `S3_*`
  - For Docker local testing, keep `S3_ENDPOINT=http://minio:9000` and set `S3_PUBLIC_ENDPOINT=http://localhost:9000` so pre-signed upload URLs are reachable from iOS/macOS host.

## API surfaces

- Canonical:
  - `GET /health`
  - `POST /v1/auth/*`
  - `GET/POST/PATCH /v1/profiles/*`
  - `GET/POST /v1/catalog/*`
  - `GET/POST /v1/scores/*`
  - `POST /v1/scores/overwrite`
  - `POST /v1/scores/play-records/overwrite`
  - `POST /v1/import/df`
  - `POST /v1/import/lxns`
  - `GET/POST /v1/community/*`
  - `GET/POST/PATCH /v1/admin/*`
- Internal jobs:
  - `POST /internal/jobs/enqueue`
  - `POST /internal/jobs/dispatch`

## Data model highlights

Prisma schema includes:

- Catalog: `catalog_snapshots`, `songs`, `sheets`, `aliases`, `icons`
- User/profile: `users`, `profiles`, `profile_bindings`
- Score: `best_scores`, `play_records`
- Import: `import_runs`, `import_raw_payloads`
- Community aliases: `community_alias_candidates`, `community_alias_votes`
- Ops jobs: `job_queue`

## Cron & sync

Migration enables `pg_cron` and registers:

- periodic catalog sync job enqueue
- periodic community alias cycle roll job enqueue

Actual execution is handled by `/internal/jobs/dispatch` (or your own worker trigger).

## Tests

Current tests cover:

- LXNS song ID normalization rule (`>10000` modulo, `>100000` passthrough)
- Chart type / difficulty normalization utilities used by import pipeline
