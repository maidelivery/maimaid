# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

maimaid is a maimai DX player ecosystem app. It's a monorepo with three main components: a native iOS app, a Hono backend server, and a Next.js web dashboard. The iOS app is the primary product — it handles score tracking, song catalog browsing, B50 calculation, image recognition-based score entry, and cloud sync.

## Monorepo Structure

- **`ios/maimaid/`** — iOS app (SwiftUI + SwiftData), the core product
- **`server/backend/`** — Hono API server (TypeScript, Prisma, PostgreSQL)
- **`web/dashboard/`** — Admin/user dashboard (Next.js 16, shadcn/ui, Tailwind CSS v4)

Orchestrated with **Nx** and **pnpm workspaces** (`pnpm@10.20.0`). The pnpm workspace covers `web/*` and `server/*`.

## Common Commands

### Root-level (from repo root)

```bash
pnpm install                    # Install all JS dependencies
pnpm run dev:server             # Start backend in watch mode (tsx watch)
pnpm run build:server           # Compile backend TypeScript
pnpm run test:server            # Run backend tests (vitest)
pnpm run migrate:server         # Deploy Prisma migrations
pnpm run dev:web                # Start Next.js dashboard dev server
pnpm run build:web              # Build dashboard (static export)
pnpm run typecheck:web          # TypeScript check for dashboard
pnpm run build:ios              # Build iOS via Nx (requires Xcode CLI tools)
```

### Backend-specific (from `server/backend/`)

```bash
pnpm run dev                    # tsx watch src/server.ts
pnpm run test                   # vitest run (test files: test/**/*.spec.ts)
pnpm run test:watch             # vitest in watch mode
pnpm run prisma:migrate:dev     # Create new migration
pnpm run prisma:studio          # Open Prisma Studio
pnpm run podman:up              # Start local stack (Postgres + MinIO + backend)
pnpm run podman:down            # Stop local stack
```

### iOS

Use XcodeBuildMCP (via the xcodebuildmcp-cli skill) for building, testing, and running the iOS app. The Xcode project is at `ios/maimaid.xcodeproj/`. iOS secrets (BACKEND_URL, BACKEND_AUTH_URL) go in `ios/Config/Secrets.xcconfig` (gitignored).

## Architecture

### iOS App (`ios/maimaid/`)

- **Target**: iOS 26.0+, Swift 6.2+, strict concurrency
- **Data layer**: SwiftData with models: `Song`, `Sheet`, `Score`, `PlayRecord`, `SyncConfig`, `MaimaiIcon`, `UserProfile`, `CommunityAliasCache`
- **Entry point**: `maimaidApp.swift` — sets up `ModelContainer`, handles background tasks (`BGAppRefreshTask` for static data sync and cloud backup), and manages app lifecycle sync
- **Views/**: Feature views organized flat; `Settings/` and `Components/` are subdirectories
- **Services/**: Backend API client (`BackendAPIClient`), session management (`BackendSessionManager`), cloud sync (`BackendCloudSyncService`), incremental sync, score sync, data import from Diving Fish / LXNS, image recognition (`MLScoreProcessor`, `MLChooseProcessor`, `MLDistinguishProcessor`), community aliases
- **Localization**: `Localizable.strings` in `en`, `ja`, `zh-Hans`, `zh-Hant`. When adding user-facing strings, translate into all four languages.

### Backend (`server/backend/`)

- **Framework**: Hono on Node.js (port 8787)
- **DI**: tsyringe with token-based injection (`src/di/container.ts`, `src/di/tokens.ts`)
- **ORM**: Prisma 7 with PostgreSQL (includes `pg_cron` extension)
- **Storage**: S3-compatible (MinIO for local dev)
- **Auth**: JWT access/refresh tokens (`jose`), MFA via TOTP (`otpauth`) and WebAuthn/passkeys (`@simplewebauthn/server`), backup codes
- **Validation**: Zod v4 for request validation and env parsing (`src/env.ts`)
- **Routes**: Versioned under `/v1/` — auth, profiles, catalog, scores, import, community, admin, sync, static. Internal jobs at `/internal/jobs/`
- **Key services**: AuthService, ImportService (Diving Fish / LXNS), CatalogService, ScoreService, SyncService, StaticBundleService, CommunityAliasService, MfaService, StorageService
- **Tests**: Vitest, test files in `test/**/*.spec.ts`

### Web Dashboard (`web/dashboard/`)

- **Framework**: Next.js 16 with static export (`output: "export"`)
- **UI**: shadcn/ui + Radix UI + Tailwind CSS v4
- **Deployment**: Cloudflare Pages (`wrangler pages deploy`)
- **Auth**: WebAuthn passkey support (`@simplewebauthn/browser`)
- **Pages**: Auth, Scores, Imports, Aliases, Settings, Admin (Users, Static data)
- **Env**: requires `NEXT_PUBLIC_BACKEND_URL`

### Data Flow

The iOS app can operate fully offline with local SwiftData. When the backend is configured:
1. User authenticates via the backend (JWT-based, with optional MFA)
2. Scores/profiles sync incrementally between the app and backend
3. Data can be imported server-side from Diving Fish / LXNS APIs
4. Static data (song catalog, icons, aliases) is bundled and served by the backend
5. Cloud backup/restore through the sync service
6. Community song alias submissions go through a voting cycle on the backend

### Podman Local Dev Stack

`server/backend/docker-compose.yml` provides: PostgreSQL 18.3 + pg_cron (port 54329), MinIO (API 9000, console 9001), and the backend service (port 8787). Copy `.env.docker.example` to `.env.docker` before starting.

## iOS Coding Guidelines

Detailed Swift/SwiftUI rules are in `AGENTS.md`. Key points:

- Use `@Observable` (not `ObservableObject`), mark with `@MainActor`
- Use modern Swift concurrency (async/await, never GCD)
- Use `FormatStyle` API (never legacy `Formatter` subclasses like `DateFormatter`)
- Use `foregroundStyle()` not `foregroundColor()`, `clipShape(.rect(cornerRadius:))` not `cornerRadius()`
- Use `NavigationStack` not `NavigationView`, `Tab` API not `tabItem()`
- Filter user input with `localizedStandardContains()` not `contains()`
- Break views into separate `View` structs, not computed properties
- No UIKit unless specifically needed
- No third-party frameworks without asking first
