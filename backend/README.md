# maimaid backend (Hono + Prisma + DI)

Self-hosted unified serverless-style backend for:

- maimaid app canonical API (`/v1/*`)
- user-initiated import from Diving Fish / LXNS into canonical data model

## Stack

- Runtime: Bun (Docker runtime) + Node.js (local dev scripts)
- Framework: Hono
- DI: tsyringe
- ORM: Prisma
- DB: PostgreSQL (`pg_cron` extension enabled in migration)
- Storage: S3-compatible object storage (pre-signed upload URL)

## Quick start

1. Copy env file:

```bash
cp backend/.env.example backend/.env
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

## Local test stack (Podman Compose)

This repo now includes a ready-to-run local stack:

- PostgreSQL 18.3 + `pg_cron`
- MinIO (S3-compatible storage)
- backend API service (Bun 1.3.2 runtime)

1. Prepare local env:

```bash
cp backend/.env.docker.example backend/.env.docker
```

Optional (recommended for local secrets): create `backend/.env.docker.local` for overrides such as `RESEND_API_KEY`.
If you change MinIO credentials, update both `MINIO_ROOT_*` and `S3_ACCESS_*` to the same values.

2. Start stack:

```bash
cd backend
pnpm run podman:up
```

3. Verify:

- API: `http://localhost:8787/health`
- OpenAPI JSON: `http://localhost:8787/openapi.json`
- API docs (Scalar): `http://localhost:8787/docs`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`
- Postgres host port: `localhost:54329`

4. Point iOS to local backend:

- Set `BACKEND_URL = http://localhost:8787` in `ios/Config/Secrets.xcconfig`

5. Stop stack:

```bash
cd backend
pnpm run podman:down
```

## Monorepo scripts

At repository root:

- `pnpm run dev:server` – start backend in watch mode
- `pnpm run build:server` – compile backend
- `pnpm run test:server` – run backend tests
- `pnpm run migrate:server` – deploy Prisma migrations

## Environment variables

See `backend/.env.example`:

- Network: `HOST`, `PORT`
- Public URL: `APP_PUBLIC_URL` (used to build email verification links)
- CORS: `CORS_ALLOWED_ORIGINS` (comma-separated exact web origins)
- WebAuthn: `WEBAUTHN_ORIGIN`, `WEBAUTHN_RP_ID` (local dashboard with Next.js defaults to `http://localhost:3000`)
- Auth: `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_ACCESS_SECRET`, `JWT_ACCESS_TTL_SECONDS`, `JWT_REFRESH_TTL_SECONDS`
- Database: `DATABASE_URL`
- Catalog source override: `CATALOG_SOURCE_URL` (optional; used only for manual `/v1/catalog/sync` flow)
- MinIO root credentials (local compose): `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`
- S3: `S3_*`
  - For Podman local testing, keep `S3_ENDPOINT=http://minio:9000` and set `S3_PUBLIC_ENDPOINT=http://localhost:9000` so pre-signed upload URLs are reachable from iOS/macOS host.
  - In local compose, keep `S3_ACCESS_KEY_ID/S3_SECRET_ACCESS_KEY` aligned with `MINIO_ROOT_USER/MINIO_ROOT_PASSWORD`.

## API surfaces

- Canonical:
  - `GET /health`
  - `POST /v1/auth/*`
  - `GET /v1/auth/verify-email?token=...`
  - `GET /v1/auth/password-reset?token=...`
  - `GET/POST/PUT/PATCH /v1/profiles/*`
  - `GET/POST /v1/catalog/*`
  - `GET/PATCH/DELETE /v1/scores/*`
  - `POST /v1/scores:batchUpsert`
  - `POST /v1/scores:replace`
  - `GET/DELETE /v1/play-records/*`
  - `POST /v1/play-records:batchUpsert`
  - `POST /v1/play-records:replace`
  - `POST /v1/imports:*`
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
