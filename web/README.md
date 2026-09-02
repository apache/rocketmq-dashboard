# RocketMQ Studio 前端开发指南

前端基于 React、TypeScript 和 Vite，使用 npm 锁定依赖。请从仓库根目录进入 `web` 目录后再执行下列命令。

## 环境要求

- Node.js 20.19.0 或更高版本；该下限与部署运行时（server/Dockerfile）保持一致（当前 Vite 依赖本身仅要求 Node 18+）。
- npm；提交依赖变更时必须同步更新 `package-lock.json`。

```bash
cd web
node --version
npm ci
```

`npm ci` 会严格按锁文件安装依赖，适用于首次安装和 CI。日常更新依赖时使用 `npm install`，并一并审查清单与锁文件的差异。

## 本地开发

```bash
npm run dev
```

开发服务器默认使用 mock 数据。若要连接本地后端，请在不提交到仓库的 `.env.local` 中配置：

```dotenv
VITE_USE_MOCK=false
VITE_API_PROXY_TARGET=http://localhost:8888
```

`VITE_API_BASE_URL` 可覆盖浏览器请求前缀，默认值为 `/api`。`.env.local` 与 `.env.*.local` 是开发者私有覆盖文件，已从 Git 和 Docker 构建上下文中排除，不应存放到版本库。

## 质量检查

提交前至少运行与改动相关的测试，并执行静态检查和构建：

```bash
npm test
npm run lint
npm run build
```

格式化指定源码可使用：

```bash
npx prettier --check src
```

生产构建会由 Vite 注入构建提交和构建时间，生成产物位于 `dist/`。Docker 构建只复制受版本控制的清单、锁文件和源码；本地依赖、产物及环境覆盖文件不进入镜像上下文。
