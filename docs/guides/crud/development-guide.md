# CRUD 开发指南 (HandBook)

本指南旨在帮助开发者快速掌握 `ent-loom-crud` 的使用，包括如何定义实体、执行查询、提交命令以及如何通过场景（Scene）进行定制。

## 1. 实体定义

实体是 CRUD 的核心。推荐使用 **Meta-first** 方式定义：

```java
@EntEntity(entity = "student", label = "学生")
public class Student {
    @EntField(EntFieldKind.ID)
    private Long id;

    @EntField(label = "姓名", required = OptionalBoolean.TRUE)
    private String name;

    @EntField(label = "年龄")
    private Integer age;

    @EntRelation(targetEntity = "class", targetField = "id")
    private Long classId; // 关联班级
}
```

## 2. 查询 DSL (Query Spec)

所有的查询请求最终都会转换为 `QuerySpec`。

### 2.1 过滤条件 (Filters)
支持的操作符：
- `EQ` / `NE`: 等于 / 不等于
- `GT` / `GE` / `LT` / `LE`: 大于 / 大于等于 / 小于 / 小于等于
- `IN` / `NOT_IN`: 包含 / 不包含
- `LIKE`: 模糊匹配（自动处理 `%`）
- `IS_NULL` / `IS_NOT_NULL`: 判空

**JSON 示例**:
```json
{
  "filters": [
    { "field": "name", "op": "LIKE", "value": "张三" },
    { "field": "age", "op": "GE", "value": 18 }
  ]
}
```

### 2.2 排序 (Sorts)
支持 `ASC` 和 `DESC`。
```json
{
  "sorts": [
    { "field": "age", "direction": "DESC" }
  ]
}
```

### 2.3 分页与限制 (Paging)
- `page`: 请求页码（从 1 开始）。
- `limit`: 每页大小。
- `countMode`: `EXACT`（计算总数）或 `NONE`（不计总数，提升性能）。

### 2.4 关系展开 (Expand Relations)
使用 `expandRelations` 触发 `ROOT_FIRST` 补数逻辑。
```json
{
  "options": {
    "expandRelations": ["class", "orders"]
  }
}
```

## 3. 命令 DSL (Command Spec)

用于数据的增删改。

### 3.1 创建 (CREATE)
`payload` 为实体对象的 Map 表达或 Java 对象。

### 3.2 更新 (UPDATE)
必须包含主键。支持 `dryRun` 试运行模式。

### 3.3 批量操作 (BATCH)
框架支持显式的批量创建和更新，提升性能。

## 4. 场景定制 (SceneHandler)

当默认的 JDBC 引擎无法满足业务需求时（如：需要调用第三方接口、复杂的业务校验），可以注册 `SceneHandler`。

### 4.1 定义 Handler
```java
@Component
public class StudentCustomSceneHandler implements QueryPageSceneHandler {
    @Override
    public CrudRouteKey getRouteKey() {
        return CrudRouteKey.of("student", QueryOperation.PAGE, "custom_scene");
    }

    @Override
    public PageResult<CrudRecord> page(QueryExecutionSpec spec, CrudExecutionContext context) {
        // 1. 执行自定义逻辑
        // 2. 也可以调用默认 Engine 执行基础查询
        return defaultEngine.page(spec, context);
    }
}
```

## 5. 治理扩展

业务系统通常需要实现以下接口来接入自己的账号和权限体系：

- **`CrudSubjectResolver`**: 从请求中提取当前用户、租户 ID。
- **`CrudPermissionService`**: 实现自己的权限判定逻辑。
- **`CrudDataScopeResolver`**: 定义数据权限（如：只能看自己创建的数据）。

## 6. 响应结构

框架返回统一的 `CrudResponse`：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "items": [...],
    "page": {
      "total": 100,
      "page": 1,
      "limit": 20
    }
  },
  "requestId": "..."
}
```
通过 `CrudRecord` 返回的数据会自动根据 `EntField` 定义进行类型转换和脱敏。
