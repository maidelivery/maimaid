# maimaid

[English](README-en-US.md)

## 项目简介

`maimaid` 是一款面向 maimai DX 玩家生态的原生 iOS 应用。项目聚焦于成绩记录、曲库检索、进度统计与社区数据协同，采用本地数据优先策略，并结合云端能力完成备份与跨设备恢复。

## 功能模块

- 多用户档案体系（JP / INTL / CN 服务器上下文）
- 成绩记录、游玩历史与 B35/B15（B50）计算
- B50 结果可视化与导出
- 曲库检索、筛选、收藏与多密度网格浏览
- 基于 CoreML + Vision 的分数图/选曲图识别录入
- 随机选歌、推分建议、牌子进度、段位信息
- Diving Fish / LXNS 数据导入与自动上传
- Supabase 账号、云备份/恢复、后台定时备份
- 社区别名投稿、去重、投票与已通过别名同步

## 技术栈

- SwiftUI
- SwiftData
- CoreML + Vision
- Supabase (Auth / PostgREST / Storage / Functions)
- Yams（段位 YAML 解析）

## 系统组成

- 客户端：SwiftUI + SwiftData，本地存储与业务计算在端侧完成
- 数据同步：静态曲库、别名、图标、段位、统计数据按配置增量刷新
- 云端能力：Supabase 用于认证、备份恢复与社区别名服务
- 识别链路：本地 CoreML 模型完成图片分类、目标检测与 OCR 辅助解析

## 仓库结构

- `ios/maimaid/`：iOS 主工程（Views / Models / Services / Utils）
- `ios/maimaid.xcodeproj/`：iOS Xcode 工程
- `ios/Config/`：iOS 构建配置（含 Supabase 相关配置键）
- `supabase/migrations/`：社区别名相关数据库迁移与 RPC
- `supabase/functions/community-alias-submit/`：别名投稿 Edge Function
- `web/dashboard/`：统一管理面板（React + MUI）

## Monorepo 工作流（Nx + pnpm Workspace）

- 根目录使用 `pnpm workspace` 管理前端子包（当前包含 `web/*`）
- 使用 Nx 统一编排前端任务
- iOS 任务依赖 Xcode 命令行工具
- Supabase 任务依赖 Supabase CLI（本地容器相关命令依赖 Podman）

```bash
pnpm install
pnpm run dev:web
pnpm run build:web
pnpm run typecheck:web
pnpm run check-env:web
pnpm run list:ios
pnpm run build:ios
pnpm run doctor:db
pnpm run migrate:db
```

## GitHub Actions（每次 Push 产出 IPA）

- 已配置工作流：`.github/workflows/build-ipa.yml`
- 触发时机：每次 `push`（也支持手动 `workflow_dispatch`）
- 产物：`maimaid-ipa-<commit_sha>`，内含 `maimaid.ipa`
- 当前为无签名归档（`CODE_SIGNING_ALLOWED=NO`），用于持续集成构建产物校验
- 需要在仓库 `Settings -> Secrets and variables -> Actions` 配置：
  - `SUPABASE_URL`
  - `SUPABASE_PUBLISHABLE_KEY`

## 数据来源

- [Diving Fish](https://www.diving-fish.com/)：成绩与统计接口
- [LXNS Coffee House](https://maimai.lxns.net/)：歌曲别名、图标与账号相关接口
- [arcade-songs](https://arcade-songs.zetaraku.dev/)：歌曲数据参考

## 致谢

- [Diving Fish](https://www.diving-fish.com/)：成绩与统计相关 API
- [LXNS Coffee House](https://maimai.lxns.net/)：歌曲别名、图标与账号体系相关 API
- [arcade-songs](https://arcade-songs.zetaraku.dev/)：歌曲数据参考
- Google Antigravity（模型训练）
- Ultralytics Platform（模型训练）
- charaDiana（图像标注支持）

## 版权说明

`maimai` 为 SEGA 旗下作品。游戏素材与商标归原权利方所有。  
本项目为玩家社区工具，和 SEGA 无官方关联。
