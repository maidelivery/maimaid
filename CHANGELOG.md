# Changelog

## 1.1.3

### Added

- Added live score refresh across the home dashboard, song list, score query, dan, and plate views.
- Added compact stacked rating, FC, and FS badges to score-query grid cells and score-enabled constant-table exports.
- Added CN profile-aware score upload gating for Diving Fish and LXNS integrations.

### Changed

- Updated CN version detection to use a 15-month cutoff and stop at partially released generations.
- Moved static-data refresh and cloud backup to explicit user-triggered flows, with one backup on app launch.
- Removed latest-version and profile identifier details from profile editing.
- Improved profile-list loading and score-save responsiveness.
- Normalized FC/AP status values across score storage, imports, badges, and plate calculations.
- Updated the Android application namespace to `org.rhythmeta.maimaid`.

### Fixed

- Fixed score imports from Diving Fish and LXNS requiring a follow-up cloud restore before appearing in the app.
- Fixed scanner photo capture crashes caused by Photos transaction actor isolation.
- Fixed scanner photo saving for Full Access photo-library authorization.
- Improved scanner camera framing and raised the live preview quality preset.

