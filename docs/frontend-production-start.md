# 前端生产构建运行

`start.bat` / `start-frontend.bat` 默认先执行 `npm run build`，成功后隐藏启动 Node 静态服务，地址仍为 `http://127.0.0.1:5173`。构建失败不启动；查看 `ivd-web/frontend-build.log`，运行日志为 `ivd-web/frontend.log`。

静态服务只绑定本机回环地址，支持 Vue Router history 页面刷新，将 `/api` 请求代理到 `http://127.0.0.1:8080`。HTML 不使用长缓存，带哈希的构建资源长缓存，API 禁止缓存。无须新增 npm 依赖。

修改前端源码后需重新构建并重启前端，不再热更新。开发时仍可在 `ivd-web` 执行 `npm run dev`，但应先停止占用 5173 的生产服务。

手工构建运行：在 `ivd-web` 执行 `npm run build`，然后 `npm start`。可用 `IVD_FRONTEND_PORT` 和 `IVD_BACKEND_URL` 指定端口与后端 HTTP 地址；远程正式部署仍使用现有 nginx 配置及 HTTPS，不直接暴露此本机静态服务。

验证：构建后执行 `node --test scripts/qa/production-server.test.mjs`。首页图表按需异步加载，加载或失败提示保留高度；登录按钮等待导航结束，期间拒绝重复点击。

本项仅部署前端，不构建或部署未完成的机构隔离/检验后端，也不执行数据库迁移。

## 本机验证（2026-10-10）

- 生产静态服务 4 项测试与工作台契约 5 项回归通过。
- 真实浏览器延迟 Dashboard 资源，验证认证返回后按钮仍为加载态；延迟 ECharts 资源，验证菜单不受图表加载阻塞。资源恢复后图表渲染、直接刷新 `/dashboard` 正常。
- 同机新浏览器上下文实测，点击登录至菜单显示约 264–302ms；开发模式此前约 403–640ms。登录后的资源由 133 个约 3.17MB 降至 16 个约 1.10MB（包含异步图表）。这不是生产负载 SLA，也不包含打开登录页之前的首次资源加载。
