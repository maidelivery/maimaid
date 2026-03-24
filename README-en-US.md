# maimaid

## Project Overview

`maimaid` is a native iOS application built around the maimai DX player ecosystem.  
The project focuses on score management, song database workflows, progression tracking, and community-driven data collaboration, with a local-first architecture and optional cloud backup/restore.

## Feature Modules

- Multi-profile system with JP / INTL / CN server contexts
- Score records, play history, and B35/B15 (B50) calculation
- B50 visualization and export rendering
- Song library search, filtering, favorites, and multi-density grid browsing
- CoreML + Vision-based score-screen and song-select image recognition
- Random picker, rating recommendations, plate progress, and dan references
- Diving Fish / LXNS import and auto-upload integration
- Supabase auth, cloud backup/restore, and scheduled background backup
- Community alias submission, dedupe, voting, and approved-alias sync

## Tech Stack

- SwiftUI
- SwiftData
- CoreML + Vision
- Supabase (Auth / PostgREST / Storage / Functions)
- Yams (YAML parsing for dan data)

## System Components

- Client layer: SwiftUI + SwiftData, local storage and business logic on-device
- Data sync layer: configurable static-data refresh for songs, aliases, icons, dan, and stats
- Cloud layer: Supabase for authentication, backup/restore, and community alias workflows
- ML pipeline: on-device model inference for classification, detection, and OCR-assisted parsing

## Repository Layout

- `maimaid/`: iOS app source (Views / Models / Services / Utils)
- `supabase/migrations/`: database migrations and RPCs for community aliases
- `supabase/functions/community-alias-submit/`: Edge Function for alias submission
- `Config/`: build configuration and Supabase-related keys

## Data Sources

- [Diving Fish](https://www.diving-fish.com/): score and statistics endpoints
- [LXNS Coffee House](https://maimai.lxns.net/): aliases, icons, and account-related endpoints
- [arcade-songs](https://arcade-songs.zetaraku.dev/): song metadata reference

## Acknowledgements

- [Diving Fish](https://www.diving-fish.com/): community data and API support
- [LXNS Coffee House](https://maimai.lxns.net/): alias and API support
- [arcade-songs](https://arcade-songs.zetaraku.dev/): song data reference
- Google Antigravity.
- Ultralytics Platform (model training support).
- charaDiana (image labeling support).

## Copyright

`maimai` is developed by SEGA. All game assets and trademarks belong to their respective owners.  
This project is a community tool and has no official affiliation with SEGA.
