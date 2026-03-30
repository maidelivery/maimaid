# maimaid

[English](README-en-US.md)

## 项目简介

maimaid 是一款面向 maimai DX 玩家生态的 App。项目聚焦于成绩记录、曲库检索、进度统计与社区数据协同，采用本地数据优先策略，并结合云端能力完成备份与跨设备恢复。

maimaid 创新了查询歌曲和记录成绩的范式，利用端侧机器视觉模型完成快速、精准的成绩录入和歌曲查询。

## 功能模块

- 多用户档案体系（JP / INTL / CN 服务器）
- 成绩记录、游玩历史与 B35/B15（B50）计算
- B50 结果可视化与导出
- 曲库检索、筛选、收藏与多密度网格浏览
- 基于端侧模型的成绩录入/歌曲查询
- 随机选歌、推分建议、牌子进度、段位信息
- Diving Fish / LXNS 数据导入与自动上传
- 账号、云备份/恢复、后台定时备份
- 社区别名

## 仓库结构

- `ios/maimaid/`：iOS App
- `web/dashboard/`：Dashboard
- `server/backend/`：后端

## Monorepo 工作流（Nx + pnpm Workspace）

- 根目录使用 `pnpm workspace` 管理前端子包（当前包含 `web/*`）
- 使用 Nx 统一编排前端任务
- iOS 任务依赖 Xcode 命令行工具
- 后端构建任务依赖 Podman / Docker

### 项目根目录

```bash
pnpm install                    # 安装所有 JS 依赖
pnpm run dev:server             # 启动后端开发服务器
pnpm run build:server           # 编译后端 TypeScript
pnpm run test:server            # 运行后端测试
pnpm run migrate:server         # 部署数据库迁移
pnpm run dev:web                # 启动 Next.js dashboard 开发服务器
pnpm run build:web              # 构建 dashboard (static export)
pnpm run typecheck:web          # TypeScript check for dashboard
pnpm run build:ios              # Build iOS via Nx (requires Xcode CLI tools)
```

### 后端命令（从 `server/backend/`）

```bash
pnpm run dev                    # tsx watch src/server.ts
pnpm run test                   # vitest run (test files: test/**/*.spec.ts)
pnpm run test:watch             # vitest in watch mode
pnpm run prisma:migrate:dev     # 创建新的迁移
pnpm run prisma:studio          # 打开 Prisma Studio
pnpm run podman:up              # 启动本地栈（Postgres + MinIO + backend）
pnpm run podman:down            # 停止本地栈
```

## 数据来源

- [Diving Fish](https://www.diving-fish.com/)：成绩与谱面拟合数据
- [LXNS Coffee House](https://maimai.lxns.net/)：歌曲别名和图标
- [arcade-songs](https://arcade-songs.zetaraku.dev/)：歌曲数据参考

## 致谢

^数据来源
- Antigravity / Codex / Claude Code
- Ultralytics Platform（模型训练）
- charaDiana, Keritial（图像标注支持）

## 版权说明

`maimai` 为 SEGA 旗下作品。游戏素材与商标归原权利方所有。  
本项目为玩家社区工具，和 SEGA 无官方关联。
