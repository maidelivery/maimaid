# Changelog

## 1.2.4

### 中文

#### 新增

- iOS、Android 和 Dashboard 的 Diving Fish 导入改用 OAuth 授权，支持读取成绩并通过 `prober.records.write` 权限将成绩同步回 Diving Fish。
- 后端新增 Diving Fish OAuth 凭证管理、刷新和导入会话支持。

#### 变更

- 静态数据包改由独立静态资源服务提供，客户端根据设备解码能力请求合适的图片格式，并保留原图回退。
- iOS 与 Android 的档案编辑页面移除 B50 容量和凭证设置，已有配置与凭证继续保留。
- 调整 iOS Diving Fish 导入操作的按钮样式，使其与设置页其他更新操作保持一致。
- iOS 与 Android 版本升级至 1.2.4，构建号继续使用 Git 仓库提交数。

#### 修复

- 修复 Diving Fish OAuth 回调可能报告 `invalid code_verifier` 的问题。
- 修复 iOS 新建档案的服务器设置可能回退、档案删除延迟以及操作档案时跳转到其他档案的问题。
- 修复 iOS 新建档案时网络同步阻塞编辑界面的问题；档案现在先在本地提交，再于后台同步。
- 修复 Android 登录和退出图标的自动镜像构建警告。

### English

#### Added

- Replaced Diving Fish imports on iOS, Android, and Dashboard with OAuth authorization, including score reads and score synchronization through the `prober.records.write` scope.
- Added backend support for Diving Fish OAuth credential storage, refresh, and import sessions.

#### Changed

- Moved static bundles to the dedicated static-asset service, with clients requesting image formats supported by each device and retaining original-image fallback.
- Removed B50 capacity and credential settings from profile editors on iOS and Android while preserving existing values.
- Updated the iOS Diving Fish import actions to match other update controls in Settings.
- Updated iOS and Android to version 1.2.4 while continuing to derive build numbers from the Git commit count.

#### Fixed

- Fixed Diving Fish OAuth callbacks reporting `invalid code_verifier`.
- Fixed iOS profile server settings reverting, delayed profile deletion, and operations unexpectedly switching to another profile.
- Fixed network synchronization blocking the iOS editor while creating a profile; profiles now commit locally before background synchronization.
- Fixed Android build warnings for login and logout icons by using auto-mirrored variants.

## 1.2.0

### 中文

#### 新增

- iOS 与 Android 的 Best 50 新增互斥的拟合定数和版本定数模式，导出图片使用当前计算模式对应的定数。
- iOS 与 Android 的歌曲难度卡片新增定数变化历史，按版本从新到旧排列，并通过变化量、方向箭头和颜色标记升降。
- 歌曲新增分谱面类型的加入版本信息；同时包含标准谱面和 DX 谱面的歌曲使用双色版本徽标。

#### 变更

- iOS 与 Android 的 Best 50、推荐、成绩查询、定数表、随机选曲、扫描、段位和牌子进度统一按当前档案所属服务器应用谱面规则和定数。
- 歌曲详情显示当前谱面类型的主难度加入版本；追加难度的加入版本显示在展开后的难度卡片中。
- 静态数据包、歌曲封面和 LXNS 预设头像迁移至 R2，图片优先请求 AVIF，并保留原图回退。
- iOS 与 Android 版本升级至 1.2.0，构建号统一取 Git 仓库提交数。

#### 修复

- 修复谱面定数可能使用非当前档案服务器数据的问题。
- 修复生成静态数据包时宴谱歌曲匹配不准确的问题。
- 修复 Android 歌曲版本徽标超出预期范围的问题。

### English

#### Added

- Added mutually exclusive fitted-constant and version-constant modes to Best 50 on iOS and Android, with image exports using the selected calculation constants.
- Added per-chart constant history to expanded song difficulty cards, ordered from newest to oldest with colored change amounts and direction indicators.
- Added chart-specific release-version details and split-color version badges for songs containing both standard and deluxe charts.

#### Changed

- Applied profile-aware server chart rules and constants across Best 50, recommendations, score queries, constant tables, random song selection, scanning, Dan, and plate progress on iOS and Android.
- Updated song details to show the main release version for the selected chart type and the release version of an appended difficulty inside its expanded card.
- Moved static bundles, song jackets, and LXNS preset avatars to R2-backed delivery with AVIF image requests and original-image fallback.
- Updated iOS and Android to version 1.2.0 and derived their build numbers from the repository commit count.

#### Fixed

- Fixed chart constants being resolved against a server other than the active profile's server.
- Fixed Utage song matching while building static bundles.
- Fixed Android song-version badges stretching beyond their intended bounds.

## 1.1.7

### Added

- Added Simplified Chinese, Traditional Chinese, Japanese variant-character, and compatibility-character matching to song search on iOS and Android.
- Added Utage chart-fit details, including note counts and fitted maximum DX scores, to difficulty details on iOS and Android.
- Added paged Dan chart sections on iOS.

### Changed

- Upgraded Android OCR recognition to the PP-OCRv6 small multilingual model.
- Synchronized Utage chart-stat resolution and score-detail presentation across iOS and Android.

### Fixed

- Fixed Android Utage recognition when multiple charts share the same maximum DX score.
- Fixed Android Utage recognition against stale chart statistics by validating recognized note counts and exact chart data.
- Fixed Android score validation and persistence to honor the recognized Utage maximum DX score.

## 1.1.4

### Added

- Added separate new-song and old-song recommendation pages with incremental loading in groups of 10.
- Added live CN chart-availability validation against the LXNS playable song list during static bundle builds.

### Changed

- Updated Best 50 capacity fields to apply changes after editing finishes.
- Replaced difficulty badges in Best 50 and recommendation rows with compact jacket-side difficulty indicators.
- Limited recommendation candidates to 100 per category, or 50 per category for profiles without scores.
- Updated unsigned IPA builds to use the `MAIMAID_API_URL` repository secret.

### Fixed

- Fixed Japanese and international Best 50 classification from CiRCLE onward to include charts from the current and immediately previous versions in B15.
- Classified CN preview charts in B35 until their version becomes the current CN version.
- Removed the achievement-to-rating table from Utage difficulty cards on iOS and Android.
- Fixed PRiSM and PRiSM PLUS version matching when calculating Best 50 buckets.
- Fixed inflated recommendation gains when a Best 50 bucket has not reached its configured capacity.

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
