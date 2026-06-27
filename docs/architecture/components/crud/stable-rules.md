# 稳定规则清单

这些规则已经固化在当前代码路径中。后续演进如果要改变，需要同时调整代码、测试和本文档。

## Spec 与路由

1. `Spec` 对调用方不可变；任何 setter 都不能作为修改入口。
2. `entityClasses` 首项必须等于 `rootType`。
3. `scene` 统一通过路径或服务端构造提供，HTTP `options.scene` 被拒绝。
4. routeKey 的实体段必须使用 Java 类全名，scene 归一化为小写。
5. `ACTION` 必须有非空 scene，且默认 Command 引擎不处理 `ACTION`。

## Query

1. `findOne` 允许不存在，但不允许命中多条。
2. `detail` 必须存在，也不允许命中多条。
3. `PAGE` 默认 `page=1, limit=10`。
4. `LIST` 默认 `limit=200`。
5. Query 最大 limit 为 1000。
6. 默认 Query 策略只支持 `ROOT_FIRST`。
7. 默认 JDBC Query 不支持关联过滤和关联排序。

## Command

1. 默认 Command payload 只接受 `Map` 或 `CrudRecord`。
2. `UPDATE/DELETE` 必须能解析目标选择器；默认从 id 字段解析。
3. 写入 where 必须包含有效谓词；不允许无条件 update/delete。
4. 有逻辑删除字段时 delete 走逻辑删除。
5. `expectedVersion` 只在实体存在 `version` 字段时拼入写入谓词。
6. dryRun 不触发幂等必填，也不进入幂等执行。

## 治理

1. 无主体必须拒绝。
2. 无权限规则或权限返回 `DENY` 必须拒绝。
3. grantedScope 为空或非全量且无维度必须拒绝。
4. business scope 与治理范围冲突必须拒绝。
5. 调用方不能通过普通 attributes 注入框架保留治理键。

## Stats

1. Stats 必须有 payload。
2. Stats 至少有一个 metric。
3. `PAGE` 模式必须有 `groupBy`。
4. metrics 最多 20 个，groupBy 最多 10 个。
5. alias 只能是字母、数字、下划线，且不能数字开头。
6. 当前时间分桶只支持 `DAY`。
7. Stats 不支持关联过滤。
