# 访问控制与错误响应

启动和请求示例见 [README](../README.md)。本页说明本示例的权限配置和业务边界。

## 配置与身份

访问链路：`Bearer Token → 可信主体与业务身份 → 实体动作授权 → 数据范围与字段限制`。

- [application.yml](../src/main/resources/application.yml)：定义身份结构、归属关系、共享策略和管理员缺省授权。
- [demo.yml](../src/main/resources/demo.yml)：核验演示 Token，绑定服务端身份与归属，配置入口路由；入口本身不授予实体权限。
- [ent/entities](../src/main/resources/ent/entities)：选择策略，声明实体的动作、授权、字段和输入契约差异。

例如 `demo-merchant-a` 绑定 `merchant / 1001`，授权使用实体策略的 `merchant` 分支。请求体和 Header 不能切换归属；身份结构定义本身不证明账号归属。正式接入时替换演示认证与归属来源，复用策略和实体声明。

## 实体权限

| 实体 | 策略与业务边界 |
| --- | --- |
| 商家文章 | 继承 `maintainable`，商家维护本人数据 |
| 客户地址 | 继承 `maintainable`，撤销商家权限，客户只查询本人地址 |
| 商家商品 | 继承 `maintainable`，另授予商家 `batch-update`；匿名只查询启用商品 |
| 客户主数据 | 收窄 `maintainable`，由平台管理员创建、更新和读取，禁止删除 |
| 订单 | 使用 `workflow`；客户与商家读取所属订单，客户下单，双方取消；管理员不代替客户下单 |
| 商家、订单明细 | `http-exposed: false`，不开放独立通用接口 |

只有声明 CRUD 且未关闭的实体才发布通用 HTTP。最终开放能力由有效策略的 `actions` 决定；增加动作仍须授予对应身份，范围不产生操作授权。

## 查询与商品维护

查询条件与可信归属范围取交集，数据、计数和关联投影遵守同一范围；商品分页只允许 `active` 作为普通筛选，`merchantId` 等归属字段由授权范围注入，身份或必要范围缺失时拒绝，不退化为全量查询。

公共商品只返回 `id/merchantId/name/price`，关闭关联展开。公共浏览可不带凭证；无效凭证拒绝，登录身份不借用公共权限。商家可读取本人全部商品，客户身份未获商品读取授权。

订单默认只查值字段；显式请求 `expandRelations` 才展开客户与明细。当前客户、商家未配置 `EXPAND`，按声明式策略缺省开放候选关联普通字段，包含客户邮箱；需要固定投影时启用 `order.yml` 中的 INCLUDE 白名单。关联范围归父订单，不授予目标实体独立访问权限。每个关系单次展开累计最多 1000 条，超限失败。框架规则见 [受授权关联读取](../../../docs/guides/crud/受授权关联读取.md)。

商品可修改 `name/price/active`，主键、归属、版本等字段受保护。单条更新与批量更新独立授权；`POST /api/ent-crud/merchantProduct/updateBatch` 逐项校验归属、字段和版本，整批成功或回滚。

商品实体的静态字段上限在 `ent/entities/merchantProduct.yml` 中声明：返回允许商品摘要字段，筛选只允许 `active`，稳定排序只允许主键 `id`，写入只允许 `name/price/active`；身份策略只能继续收窄。

## 下单与取消

| 动作 | 请求与约束 |
| --- | --- |
| 下单 | `POST /api/ent-crud/order/action/create`；客户 Token，载荷只提供 `items`；`customerId` 来自可信身份，`merchantId` 来自商品；跨商家拆单在同一事务中完成 |
| 取消 | `POST /api/ent-crud/order/action/cancel`；载荷 `{"payload":{"orderId":1}}`；客户与商家只能取消所属且状态为 `CREATED` 的订单，归属和原状态同时进入 UPDATE，重复取消拒绝 |

订单关闭通用创建映射，未开放通用更新和删除，不能绕过业务动作。Handler 复用统一授权、事务和审计；根实体自动叠加授权范围，关联 DAO 仅允许 `relatedAccess()` 声明的实体、操作和范围字段。下单据此读取客户、商品并新增明细。

动作审计等待最终事务提交或回滚，只记录一次结果；审计存储异常不改变已提交业务结果。

## 错误响应

| 状态码 | 含义 |
| --- | --- |
| 400 | 参数校验或业务错误；订单不可访问为 `ORDER_NOT_FOUND`，不可取消为 `ORDER_NOT_CANCELLABLE` |
| 403 | 授权拒绝 |
| 404 | 通用 `detail` 未找到记录或记录不在授权范围内；`findOne` 对应返回空 |
| 409 | 范围写入或版本未命中，返回 `WRITE_CONFLICT` |
| 500 | 未知异常，服务端记录日志并返回通用错误 |

服务端可通过 `ConfiguredAccessPolicies.snapshot()` 查看最终授权及配置来源，不提供公共诊断接口。完整配置语义见 [配置参考](../../../docs/guides/配置参考.md)。
