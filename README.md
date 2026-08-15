# maimaid

maimaid 是面向 maimai DX 玩家生态的成绩与曲库工具，提供 iOS、Android 客户端，以及可选的后端 API 和 Web Dashboard。客户端以本地数据为主，支持曲库查询、成绩管理、进度统计、图像识别和云端同步。

## 功能

- 多玩家档案，覆盖 JP、INTL、CN 服务器
- 曲库搜索、筛选、收藏、歌曲详情和社区别名
- 成绩与游玩记录、B35/B15（B50）、Rating 查询
- 推分建议、牌子进度、段位、随机选歌和定数表导出
- 相机或图片识别成绩与歌曲：iOS 使用 Core ML，Android 使用 ONNX Runtime 与 PaddleOCR
- Diving Fish / LXNS 成绩导入、成绩同步、云端备份与恢复

## 目录

| 路径         | 内容                                               |
| ------------ | -------------------------------------------------- |
| `ios/`       | SwiftUI iOS 客户端和 Core ML 模型                  |
| `android/`   | Kotlin + Jetpack Compose Android 客户端            |
| `backend/`   | Hono + Prisma API、PostgreSQL 数据库和静态数据同步 |
| `dashboard/` | Next.js Web Dashboard                              |
| `scripts/`   | 曲库和 Utage 图表统计数据构建脚本                  |

## 技术栈

- iOS：SwiftUI、SwiftData、Core ML、Vision
- Android：Kotlin、Jetpack Compose、MIUIX、Room、DataStore、ONNX Runtime
- Backend：Hono、Prisma、PostgreSQL、S3 兼容对象存储
- Dashboard：Next.js、TypeScript、shadcn/ui、Tailwind CSS

## 开发

### 环境

- Node.js 和 pnpm 10（根目录声明为 `pnpm@10.33.0`）
- iOS：Xcode 和 Xcode Command Line Tools
- Android：JDK 17、Android SDK 37
- 本地后端容器：Podman

### 安装依赖

```bash
pnpm install
```

### 根目录命令

```bash
pnpm run dev:web          # 启动 Dashboard
pnpm run dev:server      # 启动后端开发服务器
pnpm run build:web       # 构建 Dashboard
pnpm run typecheck:web   # 检查 Dashboard 类型
pnpm run build:server    # 编译后端
pnpm run test:server     # 运行后端测试
pnpm run build:json      # 构建根目录静态数据 JSON
pnpm run list:ios        # 查看 iOS scheme
pnpm run build:ios       # 构建 iOS Simulator 版本
```

### Android

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Android 默认连接 `https://api.rhythmeta.org` 和 `https://maimaid.rhythmeta.org`。本地构建可通过 Gradle 属性覆盖：

```bash
./gradlew :app:assembleDebug \
  -PMAIMAID_BACKEND_URL=http://10.0.2.2:8787 \
  -PMAIMAID_BACKEND_AUTH_URL=http://10.0.2.2:3000
```

### 本地后端

```bash
cp backend/.env.docker.example backend/.env.docker
cd backend
pnpm run podman:up
```

服务启动后可访问 `http://localhost:8787/health`、`http://localhost:8787/docs` 和 `http://localhost:8787/openapi.json`。完整环境变量、数据库迁移和部署说明见 [`backend/README.md`](backend/README.md)。

### Dashboard 配置

在 `dashboard/.env.local` 中配置：

```dotenv
NEXT_PUBLIC_BACKEND_URL=http://localhost:8787
NEXT_PUBLIC_LXNS_CLIENT_ID=your-public-client-id
```

使用 `pnpm --filter dashboard check:env` 检查后端地址；使用 LXNS 导入时还需要配置客户端 ID。Dashboard 的构建和 Cloudflare Pages 部署说明见 [`dashboard/README.md`](dashboard/README.md)。

### iOS 配置

创建不会提交到 Git 的 `ios/Config/Secrets.xcconfig`，填写客户端使用的后端地址：

```xcconfig
BACKEND_URL = https://api.example.com
BACKEND_AUTH_URL = https://auth.example.com
```

## 致谢

- Diving Fish、LXNS Coffee House：成绩、曲库和社区数据服务
- arcade-songs：歌曲数据参考
- Antigravity、Codex、Claude Code：开发协作
- Ultralytics Platform：模型训练支持
- charaDiana、[Keritial](https://krtl.net)：图像标注支持
- charaDiana：资金支持

## 数据与版权

应用整合后端静态曲库，以及 Diving Fish、LXNS 等社区服务的数据。`maimai` 及其游戏素材和商标归 SEGA 所有；maimaid 是独立的社区工具，与 SEGA 无官方关联。
