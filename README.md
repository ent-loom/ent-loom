# ent-loom

模块图与依赖关系见: [MODULE-ARCHITECTURE.md](./MODULE-ARCHITECTURE.md)

## 当前 Maven 模块结构（按目录）

```text
ent-loom
├── ent-loom-base
├── ent-loom-meta
├── ent-loom-components
│   ├── ent-loom-ddl
│   │   ├── ent-loom-ddl-annotations
│   │   └── ent-loom-ddl-core
│   ├── ent-loom-crud
│   │   └── ent-loom-crud-core
│   └── ent-loom-ui
│       └── ent-loom-ui-core
└── ent-loom-meta-components
    ├── ent-loom-meta-ddl
    └── ent-loom-meta-crud
```

## 目标

1. `util` 公共轻包（如 `EnumBoolean` / `OptionalBoolean`）
2. 实体元信息注解（约定 < 规则 < 配置）
3. 实体同步数据库表结构
4. 实体开放统一标准接口（内部/外部）
5. 实体读取 UI 配置内容
6. 实体开放统一标准文档
7. 项目 ER 图总览
8. 变更影响分析（可读变更说明 + 测试建议）
9. 标准化测试 / 高级测试
10. 安全合规（埋点审计）
11. 插件通道

## 注解分层

1. 元信息层:
`@EntEntity` `@EntIndex` `@EntField` `@EntMeta`

2. DDL 层:
`@EntDbEntity` `@EntDbIndex` `@EntDbField`

3. UI 层（规划中）:
`@EntUiEntity` `@EntUiIndex` `@EntUiField` `@EntUiMeta`

## 重构约定

- 重构时不做兼容分支，直接收敛到干净结构。
