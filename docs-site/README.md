# ent-loom 文档站

独立的 Docusaurus 展示工程。`../docs` 是唯一权威 Markdown 内容源，本站不复制正文，也不加入 Maven Reactor。Docusaurus 及其构建依赖只用于本地和 CI 构建，生产环境只托管 `build/` 静态产物。

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
