# ent-loom 文档站

独立的 Docusaurus 展示工程。`../docs` 是唯一权威 Markdown 内容源，本站不复制正文，也不加入 Maven Reactor。Docusaurus 及其构建依赖只用于本地和 CI 构建，生产环境只托管 `build/` 静态产物。

生产站点：[ent-loom.lizubin.online](https://ent-loom.lizubin.online/)

## 本地运行

使用 `.node-version` 指定的 Node.js LTS 主版本：

```bash
cd docs-site
npm ci
npm run start
```

构建和预览静态产物：

```bash
npm run build
npm run serve
```

本站使用本地构建索引搜索，不依赖外部搜索服务或账号。`npm run start` 每次先构建站点和搜索索引，再启动静态预览服务；修改文档后重启即可更新内容和索引。构建产物只保存在本地，不提交到 Git。

需要热更新时使用 `npm run dev`。搜索插件在开发模式下不执行搜索，即使复制索引也无法启用；验证搜索请使用 `npm run start`。

搜索支持中文和英文，默认显示最多 10 条结果；可使用导航栏搜索框或 `Ctrl/Cmd + K` 聚焦搜索。

## 部署

本机 SSH 配置可访问 `my4g` 后，在本站目录执行：

```bash
npm run deploy
```

脚本依次执行锁定依赖安装、类型检查、生产构建和 `rsync` 增量发布。产物先上传至远端暂存目录，校验通过后再同步到线上目录，避免网络中断直接留下不完整站点。

已确认依赖未变化时，可使用 `npm run deploy -- --skip-install`；使用 `npm run deploy -- --dry-run` 可只预览待上传文件。SSH 主机和远端目录可通过 `DEPLOY_HOST`、`DEPLOY_DIR`、`DEPLOY_STAGING_DIR` 覆盖。
