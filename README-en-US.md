# maimaid

maimaid is a score and song-library toolkit for the maimai DX player community. It includes iOS and Android clients, an optional backend API, and a Web Dashboard. The clients use a local-first data model and support catalog browsing, score management, progression tracking, image recognition, and cloud synchronization.

## Features

- Multiple player profiles for JP, INTL, and CN servers
- Song search, filters, favorites, song details, and community aliases
- Scores and play records, B35/B15 (B50), and Rating queries
- Rating recommendations, plate progress, Dan courses, random song selection, and constant-table export
- Score and song recognition from the camera or photo library: Core ML on iOS, ONNX Runtime and PaddleOCR on Android
- Diving Fish / LXNS score import, score synchronization, cloud backup, and restore

## Repository Layout

| Path         | Contents                                                                |
| ------------ | ----------------------------------------------------------------------- |
| `ios/`       | SwiftUI iOS client and Core ML models                                   |
| `android/`   | Kotlin + Jetpack Compose Android client                                 |
| `backend/`   | Hono + Prisma API, PostgreSQL database, and static-data synchronization |
| `dashboard/` | Next.js Web Dashboard                                                   |
| `scripts/`   | Song catalog and Utage chart-statistics build scripts                   |

## Tech Stack

- iOS: SwiftUI, SwiftData, Core ML, Vision
- Android: Kotlin, Jetpack Compose, MIUIX, Room, DataStore, ONNX Runtime
- Backend: Hono, Prisma, PostgreSQL, and S3-compatible object storage
- Dashboard: Next.js, TypeScript, shadcn/ui, and Tailwind CSS

## Development

### Requirements

- Node.js and pnpm 10 (`pnpm@10.33.0` is declared at the repository root)
- iOS: Xcode and Xcode Command Line Tools
- Android: JDK 17 and Android SDK 37
- Podman for the local backend container stack

### Install Dependencies

```bash
pnpm install
```

### Root Commands

```bash
pnpm run dev:web          # Start the Dashboard
pnpm run dev:server      # Start the backend development server
pnpm run build:web       # Build the Dashboard
pnpm run typecheck:web   # Type-check the Dashboard
pnpm run build:server    # Compile the backend
pnpm run test:server     # Run backend tests
pnpm run build:json      # Build root-level static JSON data
pnpm run list:ios        # List iOS schemes
pnpm run build:ios       # Build the iOS Simulator target
```

### Android

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Android defaults to `https://api.rhythmeta.org` and `https://maimaid.rhythmeta.org`. Override them with Gradle properties for a local build:

```bash
./gradlew :app:assembleDebug \
  -PMAIMAID_BACKEND_URL=http://10.0.2.2:8787 \
  -PMAIMAID_BACKEND_AUTH_URL=http://10.0.2.2:3000
```

### Local Backend

```bash
cp backend/.env.docker.example backend/.env.docker
cd backend
pnpm run podman:up
```

After startup, the service is available at `http://localhost:8787/health`, `http://localhost:8787/docs`, and `http://localhost:8787/openapi.json`. See [`backend/README.md`](backend/README.md) for environment variables, migrations, and deployment.

### Dashboard Configuration

Set these values in `dashboard/.env.local`:

```dotenv
NEXT_PUBLIC_BACKEND_URL=http://localhost:8787
NEXT_PUBLIC_LXNS_CLIENT_ID=your-public-client-id
```

Run `pnpm --filter dashboard check:env` to check the backend URL. The LXNS client ID is required for LXNS imports. See [`dashboard/README.md`](dashboard/README.md) for Dashboard builds and Cloudflare Pages deployment.

### iOS Configuration

Create the Git-ignored file `ios/Config/Secrets.xcconfig` with the backend endpoints:

```xcconfig
BACKEND_URL = https://api.example.com
BACKEND_AUTH_URL = https://auth.example.com
```

## Acknowledgements

- Diving Fish and LXNS Coffee House: score, catalog, and community data services
- arcade-songs: song-data reference
- Antigravity, Codex, and Claude Code: development collaboration
- Ultralytics Platform: model-training support
- charaDiana and Keritial: image-annotation support

## Data and Copyright

The application combines the backend static catalog with data from community services such as Diving Fish and LXNS. `maimai`, its game assets, and its trademarks belong to SEGA; maimaid is an independent community tool with no official affiliation with SEGA.
