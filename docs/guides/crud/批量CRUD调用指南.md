---
title: 批量 CRUD 调用指南
sidebar_label: 批量 CRUD 调用
description: 使用公开 CRUD HTTP 接口完成批量创建、更新、删除和保存或更新
---

# 批量 CRUD 调用指南

> 状态：Current

批量命令适合一次提交多条同类记录。接口路径相对于默认前缀 `/api/ent-crud`，请求体仍使用 `options` 和 `payload` 两个顶层对象。批量操作不是事务边界的替代品；需要跨实体不变量、库存或外部服务协作时，应使用业务 Handler。

完整入口和响应字段以 [CRUD HTTP 契约](../../architecture/components/crud/HTTP契约.md) 为准。下面示例使用 `customer_profile`，主键和字段应替换为项目实体的实际元数据。

## CREATE_BATCH：批量创建

批量创建使用 `POST /{entity}/createBatch`，每条记录放在 `payload.items[]` 中。每批请求使用一个 `requestId`，响应中的 `data.items[]` 按输入顺序返回每条结果。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/createBatch
Content-Type: application/json

{
  "options": {
    "requestId": "customer-create-batch-1"
  },
  "payload": {
    "items": [
      {
        "id": 1000000000002,
        "displayName": "Grace Hopper",
        "email": "grace@example.com"
      },
      {
        "id": 1000000000003,
        "displayName": "Katherine Johnson",
        "email": "katherine@example.com"
      }
    ]
  }
}
```

成功响应的 `data.rows` 表示处理条数，`data.items[]` 提供每条记录的操作和主键。客户端应保存服务端返回的结果，不要根据提交顺序自行推断数据库主键。

## UPDATE_BATCH：批量局部更新

批量更新使用 `POST /{entity}/updateBatch`。每项必须包含 `id`，`id` 只用于定位记录；需要更新的字段与 `id` 同级放在该项中。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/updateBatch
Content-Type: application/json

{
  "options": {
    "requestId": "customer-update-batch-1"
  },
  "payload": {
    "items": [
      {
        "id": 1000000000002,
        "displayName": "Grace Brewster Hopper"
      },
      {
        "id": 1000000000003,
        "displayName": "Katherine Coleman Johnson"
      }
    ]
  }
}
```

批量更新前应确认主体对所有目标记录都有更新权限和数据范围。目标不存在、版本冲突或单项校验失败时，按项目的错误合同处理，不要把整个批次简单当作成功；需要严格的全有或全无语义时，应使用明确的业务事务入口并补充回滚验收。

## SAVE_OR_UPDATE_BATCH：批量保存或更新

批量保存或更新使用 `POST /{entity}/saveOrUpdateBatch`。每项带有已存在的 `id` 时执行更新，带有不存在的 `id` 或不带 `id` 时执行创建。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/saveOrUpdateBatch
Content-Type: application/json

{
  "options": {
    "requestId": "customer-save-or-update-batch-1"
  },
  "payload": {
    "items": [
      {
        "id": 1000000000002,
        "displayName": "Grace Hopper Updated"
      },
      {
        "id": 1000000000004,
        "displayName": "Dorothy Vaughan",
        "email": "dorothy@example.com"
      }
    ]
  }
}
```

响应的 `data.items[]` 会标明每项实际执行的 `UPDATE` 或 `CREATE`。如果“是否存在”会因并发发生变化，优先使用业务侧幂等键、版本约束或专用 Handler 明确并发语义。

## DELETE_BATCH：批量删除

批量删除使用 `POST /{entity}/deleteBatch`。最简形式使用 `payload.ids[]` 传入主键集合。

```http
POST http://localhost:8080/api/ent-crud/customer_profile/deleteBatch
Content-Type: application/json

{
  "options": {
    "requestId": "customer-delete-batch-1"
  },
  "payload": {
    "ids": [
      1000000000002,
      1000000000003
    ]
  }
}
```

批量删除也支持使用 `payload.items[].id` 表达目标项。实体配置了逻辑删除字段时，执行结果遵循实体治理规则；否则可能执行物理删除。生产环境应先确认保留、审计和恢复要求，再开放批量删除权限。

## 批量请求约束

- 批量写入使用 `payload.items[]`，批量删除使用 `payload.ids[]` 或 `payload.items[].id`；不要把数组放到单条命令的 `payload` 字段中。
- 每项字段必须通过实体元数据校验，未知字段、缺失主键或非法类型会被拒绝。
- `options` 中的主体、权限和数据范围由服务端治理链路提供，不能由请求体覆盖。
- 批量接口的路径仍支持可选 `scene`；只有项目注册了对应的场景 Handler 时才应使用自定义 scene。
- 对可重试请求使用项目约定的幂等键，并确保同一个业务批次不会因网络重试重复写入。
- 大批量数据应按项目限制拆分，并结合数据库连接、锁等待和超时配置验证批次大小。

## 验证与失败处理

批量命令成功后，使用 `PAGE`、`LIST` 或 `DETAIL` 查询核对关键字段；不要只依据 `data.rows` 判断数据已经符合业务预期。失败时优先读取 `error.code`、`error.stage`、`error.reason`、`requestId` 和 `traceId`。

权限、数据范围、实体暴露和 HTTP 合同错误分别属于治理或合同问题；业务校验、唯一键冲突和数据库异常属于执行问题。批量写入涉及部分成功可能性时，应以实际响应和数据库查询为准，并为需要回滚的场景使用业务事务入口，而不是假设通用批量接口自动提供跨项回滚。

## 下一步

- 单条记录的过滤、排序、分页、更新和删除见[常用 CRUD 调用指南](常用CRUD调用指南.md)。
- 需要事务、业务校验和跨实体动作时，查看[业务集成模板](业务集成模板.md)。
- 需要完整路由、请求约束和错误映射时，查看[CRUD HTTP 契约](../../architecture/components/crud/HTTP契约.md)。
