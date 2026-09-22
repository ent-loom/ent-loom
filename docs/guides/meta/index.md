# Meta：实体建模

Meta 是跨组件的实体描述基础。先在这里声明“实体是什么、字段有什么业务含义、实体之间有什么关系”，CRUD、DDL、DOC 等组件再按各自职责消费这些信息。

第一次使用请先完成[快速开始](../快速开始.md)，用一个学生实体体验 DDL 建表、CRUD 读写和 DOC 文档。

## 三个基础注解

| 注解 | 负责什么 | 示例 |
|---|---|---|
| `@EntEntity` | 实体标识和展示名称 | `student`、学生 |
| `@EntField` | 字段名称、必填等通用业务约束 | 姓名、必填 |
| `@EntRelation` | 实体间的关联事实 | 学生的班级编号关联班级主键 |

这些注解来自 `ent-loom-meta-annotations`，不是 CRUD 专属能力。

## 从字段扩展到关系

```java
import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;

@EntEntity(entity = "student", value = "学生")
public class Student {
    @EntField("编号")
    private Long id;

    @EntField(value = "姓名", required = OptionalBoolean.TRUE)
    private String name;

    @EntField("年龄")
    private Integer age;

    @EntField("班级")
    @EntRelation(targetEntity = "class", targetField = "id")
    private Long classId;
}
```

`class` 是目标实体标识；`sourceField` 省略时取当前字段名 `classId`。此处只声明关系，实际使用时还需定义目标班级实体，并按消费组件要求注册。声明关系本身不会执行 JOIN 或加载班级数据。

## Meta 与组件的分工

- **Meta**：实体、字段、关系等通用业务事实。
- **CRUD**：查询、增删改、关系加载和执行策略。
- **DDL**：数据库结构、方言和可空性等建表策略。
- **DOC**：文档展示、示例等专属配置。

只有组件需要额外策略时，才补充对应组件注解。不要为同一业务事实重复维护多套声明；当前适配范围见 [Meta 优先指南](Meta优先指南.md)。

接下来阅读 [Meta 优先指南](Meta优先指南.md)了解约束和覆盖规则，再进入 [CRUD：数据操作](../crud/index.md)。需要查找架构和设计资料时使用 [Meta 领域总览](../../domains/meta/index.md)。
