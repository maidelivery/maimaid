# 统一 Dashboard（Next.js + shadcn/ui + pnpm）

这是基于 `Next.js App Router + TypeScript + shadcn/ui + Tailwind CSS v4` 的统一管理面板（普通用户 + 管理员）。

## 功能

- 普通用户登录、歌曲检索、成绩管理、DF/LXNS 导入、社区别名
- 管理员用户管理、社区别名管理增强、静态源与 bundle 管理
- Web MFA（TOTP / Passkey）登录挑战
- 信息密度优先的专业 dashboard 视觉

## 本地开发

推荐在仓库根目录执行（Nx + pnpm workspace）：

```bash
cd /path/to/maimaid
pnpm install
pnpm run dev:web
```

也可以在当前目录单独运行：

```bash
cd dashboard
pnpm run dev
```

## 构建

根目录：

```bash
cd /path/to/maimaid
pnpm run build:web
```

当前目录：

```bash
cd dashboard
pnpm run build
```

## 环境变量

必填：

- `NEXT_PUBLIC_BACKEND_URL`（例如 `http://localhost:8787`）
- `NEXT_PUBLIC_LXNS_CLIENT_ID`（LXNS OAuth public client_id）

可使用以下命令校验：

```bash
pnpm run check:env
```

页面不会提供后端地址手动输入入口；缺失环境变量时会直接报错并阻断关键请求。

## 安全响应头（CSP）

- 构建时会自动执行 `pnpm run generate:headers`，生成 `/public/_headers`
- `connect-src` 会基于 `NEXT_PUBLIC_BACKEND_URL` 自动收敛到白名单
- 为兼容 Next.js 静态导出，当前 `script-src` / `style-src` 保留 `'unsafe-inline'`

## Cloudflare Pages

已附带：

- `wrangler.toml`
- `pnpm run deploy`

首次部署：

```bash
cd dashboard
pnpm install
pnpm run cf:create
pnpm run deploy
```

默认 Pages 项目名：

```text
maimaid-dashboard
```
