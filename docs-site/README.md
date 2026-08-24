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

当前阶段不启用全文搜索；后续接入搜索插件时，应单独增加构建和发布配置。
