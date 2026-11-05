# ent-loom-meta-annotations

通用 Meta 注解模块。

本模块承载 `@EntEntity`、`@EntField`、`@EntRelation` 等轻薄母框架注解，用于表达跨 CRUD、DOC、DDL、UI 等模块都成立的实体和字段语义。

本地关系优先使用 `@EntRelation(Customer.class)`；目标类须声明 `@EntEntity`，实体标识和所属服务复用目标元数据。无本地实体类时使用 `@EntRelation(targetEntity = "customer", targetService = "customer-service")`；类引用与字符串标识互斥，不支持字符串 `value` 简写。关系声明不自动注册实体或加载关联数据。
