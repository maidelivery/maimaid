# 社区别名管理系统（TypeScript + MUI + pnpm）

这是基于 `React + TypeScript + MUI + pnpm + Supabase JS` 的社区别名后台。

## 功能

- 管理员邮箱密码登录
- 管理员 Claim 校验
- 候选列表搜索、筛选、分页
- 候选列表曲绘缩略图显示
- 通过 / 驳回 / 恢复投票
- 投票截止时间调整
- 手动结算到期投票
- 曲库联想补录新别名（动态拉取）
- 自动适配系统暗色模式

## 本地开发

推荐在仓库根目录执行（Nx + pnpm workspace）：

```bash
cd /path/to/maimaid
pnpm install
pnpm run dev:web
```

也可以在当前目录单独运行：

```bash
cd web/community-alias-admin
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
cd web/community-alias-admin
pnpm run build
```

曲库检索优先动态拉取：

- `https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json`
- 失败时回退 `https://maimai.lxns.net/api/v0/maimai/song/list`

## 环境变量

必填：

- `VITE_SUPABASE_URL`
- `VITE_SUPABASE_ANON_KEY`

页面不会提供 Supabase URL / Key 手动输入入口，缺失环境变量时会直接阻断访问。

## Cloudflare Pages

已附带：

- `wrangler.toml`
- `pnpm run deploy`
- SPA 回退规则 `public/_redirects`

首次部署：

```bash
cd web/community-alias-admin
pnpm install
pnpm run cf:create
pnpm run deploy
```

默认 Pages 项目名：

```text
maimaid-community-alias-admin
```

部署后可在 Cloudflare Dashboard 给项目设置自定义域名，或在构建环境里注入上面的 `VITE_` 变量。
