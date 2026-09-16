---
title: 常用 CRUD 调用指南
sidebar_label: 常用 CRUD 调用
description: 使用公开 CRUD HTTP 接口完成分页查询、更新、详情查询和删除
---

# 常用 CRUD 调用指南

> 状态：Current

本页以已经接入 CRUD Starter 的实体 `customer_profile` 为例，覆盖业务开发中最常用的一条路径：`PAGE -> UPDATE -> DETAIL -> DELETE`。接口路径相对于默认前缀 `/api/ent-crud`，实体名和字段名必须与实体元数据一致。

HTTP 入口、完整响应字段和错误阶段以 [CRUD HTTP 契约](../../architecture/components/crud/HTTP契约.md) 为准；本页只提供可复制的任务示例。

## PAGE：过滤、排序和分页

查询使用 `POST /{entity}/page`。过滤条件放在 `options.filterMap`，排序放在 `options.sorts`，页码和每页数量放在 `options.page`、`options.limit`。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/page
Content-Type: application/json

{
  "options": {
    "requestId": "customer-page-1",
    "page": 1,
    "limit": 20,
    "countMode": "EXACT",
    "nullFieldMode": "OMIT",
    "filterMap": {
      "displayName": {
        "op": "LIKE",
        "value": "Ada"
      }
    },
    "sorts": [
      {
        "field": "id",
        "direction": "DESC"
      }
    ]
  }
}
```

常用过滤操作包括 `EQ`、`NE`、`GT`、`GE`、`LT`、`LE`、`IN`、`NOT_IN`、`LIKE`、`IS_NULL` 和 `IS_NOT_NULL`。`countMode` 使用 `EXACT` 返回总数，使用 `NONE` 可以跳过总数计算；未传页码时默认为第 1 页，未传数量时默认为 10，单页最大数量为 1000。

成功响应的重点是 `data` 和 `meta`，分页结果包含记录集合及分页信息。客户端应使用响应中的 `requestId`、`traceId` 定位问题，不要依赖数据库排序的偶然顺序；需要稳定结果时显式传入 `options.sorts`。

不要传入 `options.sortExpression`，该字段已移除；未知字段、未建模字段和非法分页参数会在 HTTP 合同阶段拒绝。

## UPDATE：按主键局部更新

更新使用 `POST /{entity}/update`。`payload.id` 只用于定位记录，更新字段与主键同级放在 `payload` 中；没有变化的字段可以省略。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/update
Content-Type: application/json

{
  "options": {
    "requestId": "customer-update-1"
  },
  "payload": {
    "id": 1000000000001,
    "displayName": "Ada Byron Lovelace"
  }
}
```

更新前应确认调用主体同时具备实体的更新权限和数据范围。若项目启用了版本字段，按项目契约传入并校验 `expectedVersion`，不要通过查询后覆盖全部字段来模拟局部更新。

## DETAIL：确认更新结果

详情使用 `POST /{entity}/detail`。推荐使用主键过滤，并保留唯一的 `requestId`，这样可以确认更新后的值来自数据库，而不是客户端缓存。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/detail
Content-Type: application/json

{
  "options": {
    "requestId": "customer-detail-after-update-1",
    "filterMap": {
      "id": {
        "op": "EQ",
        "value": 1000000000001
      }
    }
  }
}
```

`DETAIL` 要求匹配一条记录：匹配不到返回未找到错误，匹配多条返回 `QUERY_NOT_UNIQUE`。只需要判断是否存在时，可使用 `FIND_ONE`；它在 0 条时返回空值，语义不同于 `DETAIL`。

## DELETE：按主键删除

删除使用 `POST /{entity}/delete`，请求只需要提供目标主键和请求标识。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/delete
Content-Type: application/json

{
  "options": {
    "requestId": "customer-delete-1"
  },
  "payload": {
    "id": 1000000000001
  }
}
```

实体配置了逻辑删除字段时，删除会按实体治理规则执行逻辑删除；否则执行物理删除。删除完成后可以重复上面的 `DETAIL` 请求确认结果。生产项目应根据业务保留、审计和恢复要求选择删除策略，不要把开发环境的物理删除直接当作生产约定。

## 常见失败

| 场景 | HTTP | 处理建议 |
|---|---:|---|
| 请求字段、操作符或分页参数不合法 | 400 | 查看 `error.code`、`error.stage` 和 `requestId`，修正请求合同 |
| 主体没有实体操作权限 | 403 | 检查主体解析和实体动作权限，不在请求体中伪造主体 |
| 数据范围不允许访问目标记录 | 403 | 检查 `CrudDataScopeResolver` 和当前主体范围 |
| 实体未暴露或路由不存在 | 404 | 检查实体白名单、Controller 开关、实体名和 scene |
| DETAIL 无记录或结果不唯一 | 404/409 | 用唯一主键过滤；需要允许空结果时改用 FIND_ONE |
| 更新或删除目标不存在 | 业务错误 | 重新读取目标并处理并发或已删除状态，不盲目重试写入 |

错误响应稳定包含 `error.code`、`error.stage`、`error.reason`、`requestId` 和 `traceId`。排障时先按 `stage` 区分 HTTP 合同、治理、路由和执行问题，再查看服务端日志；不要只依据 HTTP 状态码猜测失败原因。

## 下一步

- 需要完整路径和配置时，返回[CRUD 开发指南](开发指南.md)。
- 需要业务动作、事务和自定义 Handler 时，查看[业务集成模板](业务集成模板.md)。
- 需要确认所有路由、响应字段和错误状态时，查看[CRUD HTTP 契约](../../architecture/components/crud/HTTP契约.md)。
