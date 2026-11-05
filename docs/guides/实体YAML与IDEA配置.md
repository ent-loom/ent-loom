# 实体 YAML 与 IDEA 配置

实体文件使用 `src/main/resources/ent/entities/<资源码>.yml`，也支持 `.yaml`。它是框架加载的独立声明文件；Spring Boot 的 `application.yml` 配置提示由 Spring 配置元数据提供，实体文件的提示由 JSON Schema 提供。

实体枚举和字段用途以 Java 大写常量名展示，例如 `mode: INCLUDE`、`UPDATE_BATCH`、`WRITE`；解析兼容大小写及连字符/下划线，Java 属性名仍按实体声明填写，例如 `customerId`。

实体可通过 `crud.field-limits` 为 `RETURN`、`WRITE`、`FILTER`、`SORT`、`STATS`、`EXPAND` 分别声明字段上限。声明此项后，未列出的用途关闭；它与元数据和按次授权共同收窄，不能单独授予权限。超出元数据的字段在启动时拒绝。Java 策略绑定应在绑定中声明上限，不能与实体 YAML 混合声明。

```yaml
crud:
  field-limits:
    RETURN: [id, name, price]
    FILTER: [active]
    SORT: [id]
    WRITE: [price]
```

## 最小示例

仅在 Java 业务中使用实体、关闭通用 HTTP：

```yaml
crud:
  http-exposed: false
```

`http-exposed: false` 关闭通用 HTTP，无需额外填写访问方式；自定义控制器仍由应用管理。内部 Java 调用是否受策略约束取决于使用的授权入口，关闭 HTTP 不授予 Java 调用权限。

选择访问策略，YAML 与 Java 模板使用同一入口：

```yaml
crud:
  access:
    policy: order-read
```

`order-read` 必须是应用已经定义的共享策略或 Java 策略模板码；未知策略、重复策略码或策略未实际编译都拒绝启动。通用 HTTP 还需要全局 `ent.loom.crud.controller.enabled` 开启；有 crud 对象声明的实体默认发布 HTTP，无声明实体不自动发布，`http-exposed: false` 显式关闭，但暴露接口不代表授予权限。

Java 模板通过 `EntityPolicyTemplate` 注册，由 `BusinessPermissionEvaluator` 执行动态授权，与 YAML 策略共用授权内核。接入方式见 [Java 策略与统一绑定](crud/Java策略与统一绑定.md)。

完整字段与业务示例见 [配置参考：实体 YAML](配置参考.md#实体-yaml)。

## 在 IntelliJ IDEA 关联 Schema

先确认 YAML 插件及 JSON Schema 支持可用。IDEA 不读取仓库的 `.vscode/settings.json`，需要单独建立映射。

1. 打开 `Settings / Preferences → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings`。不同版本名称可能略有差异，可在设置中搜索 `JSON Schema`。
2. 新增映射，名称填写 `ent-loom 实体声明`。
3. Schema 文件选择仓库中的 `ent-loom-declarations/src/main/resources/META-INF/ent-entity.schema.json`；如果打开的是工作区根目录，路径前加 `ent-loom/`。
4. Schema 版本选择 `JSON Schema version 2020-12`（或对应的自动识别选项）。
5. 在映射中添加 `File path pattern`：`**/ent/entities/*.yml` 和 `**/ent/entities/*.yaml`。如果当前版本不支持此路径模式，改为选择各示例的 `src/main/resources/ent/entities` 目录进行映射。
6. 应用设置后打开 `merchant.yml`，确认文件关联到此 Schema，且没有多个 Schema 冲突。

映射由 IDEA 保存到项目设置中；切换为单独打开某个示例项目时，需要在该项目重新关联。使用框架依赖的外部项目，可以使用对应版本源码提供的 Schema，或将依赖 JAR 中的 `META-INF/ent-entity.schema.json` 提取到本地后关联；升级框架时同步升级 Schema。

无需向实体 YAML 添加 `$schema` 属性：框架会把它作为未知配置项拒绝。这里也不使用 Spring 的配置文件导入机制改变实体文件的加载方式。

## 编辑体验与验收

补全使用 IDEA 的 Basic Completion（通常为 `Ctrl+Space`；macOS 上可能被输入法快捷键占用），说明使用 Quick Documentation（以 IDE 快捷键映射为准）。建议在临时实体 YAML 中验证，检查结束后删除临时文件，避免它参与框架扫描。

| 操作 | 预期结果 |
|---|---|
| 在根节点触发补全 | 提示 `crud`、`ddl`、`doc` |
| 在 `crud` 下触发补全 | 提示 `access`、`policy`、`http-exposed` 等配置名 |
| 在 `access` 值处触发补全 | 提示 `JAVA` |
| 查看 `policy` 的文档 | 显示 YAML 与 Java 模板的统一选择及启动校验规则 |
| 输入 `http-exposed: abc` | 提示布尔值类型错误 |
| 输入 `acess: JAVA` | 提示未知配置项 |
| 填写未知 `access` 节点 | 提示未知属性并拒绝启动 |
| 填写 `mode: INCLUDE`，省略 `fields` | 提示缺少必填字段 |
| 为动作填写入口码 `bad.entry` | 提示入口码格式不符合规则 |

Schema 中的 `default` 是说明和编辑辅助，不会替应用补写 YAML。运行时仍由框架执行默认行为。

## 能力边界与常见问题

- **没有任何补全**：检查 YAML 插件、文件路径匹配及 Schema 是否成功加载；使用目录映射排除路径模式差异。
- **提示来自错误的 Schema**：检查文件的 Schema 关联，移除重复或冲突映射。
- **希望跳转 Java 配置类**：当前实体声明由独立解析器读取，没有 Spring Properties 的自动导航关联。IDE 支持的声明导航可定位 Schema；具体入口取决于 IDE 版本，不保证提供 Java 导航。
- **希望补全实体字段或策略名**：通用 Schema 只知道这些位置是字符串，不分析项目 Java 实体或其他文件。字段存在性、策略存在性、关系映射、组件启用状态、HTTP 实体访问选择及动作码忽略大小写后的重复等，由框架启动校验。
- **希望修改 YAML 后立即生效**：实体声明随应用发布，无热更新；修改后需要重新启动应用。

Schema 用于编辑阶段的结构提示，不能替代框架校验。运行时错误会携带文件、实体资源码和配置路径。

## 维护约定

实体声明新增配置时，同步修改所属组件解析器、Schema 中的类型/中文说明/约束，以及配置参考。优先用现有示例验证；对互斥、枚举、列表和默认语义检查正反例。Spring Boot 参数仍按 `@ConfigurationProperties` 与配置处理器的维护规则处理。
