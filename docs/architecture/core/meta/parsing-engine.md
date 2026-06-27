# 元数据解析引擎 (Meta Parsing Engine)

`ent-loom-meta-core` 提供了将 Java 类及注解解析为归一化元数据描述符（Descriptor）的核心能力。

## 1. 核心接口：`EntMetaParser`

`EntMetaParser` 是解析器的标准契约，定义了将类转换为 `EntEntityDescriptor` 的方法。

```java
public interface EntMetaParser {
    EntEntityDescriptor parse(Class<?> entityClass);
    MetaDiagnosticResult<EntEntityDescriptor> parseWithDiagnostics(Class<?> entityClass);
}
```

## 2. 反射解析器：`ReflectiveEntMetaParser`

这是框架默认提供的解析实现，基于 Java 反射机制。

### 2.1 解析流程
1.  **扫描注解**: 识别类上的 `@EntEntity`，字段上的 `@EntField`、`@EntRelation` 以及实体上的 `@EntIndex`。
2.  **字段推断 (Field Inference)**:
    *   如果没有显式标注 `@EntField`，解析器会根据字段名和 Java 类型推断 `EntFieldKind`。
    *   例如：名为 `id` 的字段推断为 `ID`；以 `Id` 结尾的字段推断为 `REF_ID`；枚举类型推断为 `ENUM`。
3.  **约束提取**: 从 `@EntField` 的属性中提取必填、只读等约束。
4.  **关系处理**: 处理 `@EntRelation`，支持自动推断 `sourceField`（默认为当前字段名）。
5.  **来源追踪 (Source Tracking)**: 使用 `SourcedValue` 记录每个属性的来源（是显式标注、自动推断还是默认值）。
6.  **诊断收集**: 全程收集错误（Error）、警告（Warn）和信息（Info），如关系目标缺失、字段名冲突等。

### 2.2 字段种类 (EntFieldKind) 推断规则
解析器通过 `inferFieldKind` 方法实现自动推断，极大降低了注解配置的负担：
- `id` -> `ID`
- 标注了 `@EntRelation` 或以 `Id` 结尾 -> `REF_ID`
- Java 枚举类型 -> `ENUM`
- `Boolean` / `boolean` -> `FLAG`
- `LocalDate` / `LocalDateTime` / `Instant` / `Date` -> `DATETIME`
- `Number` 子类或数值基本类型 -> `NUMBER`，例如 `Integer` / `int`、`Long` / `long`、`BigDecimal`、`Double` / `double`
- `String` 且字段名以 `json` / `jsonText` 结尾 -> `JSON_DOC`
- `String` 且字段名以 `url` / `uri` / `path` 结尾，并且字段名包含 `image` / `avatar` / `cover` / `media` / `file` -> `MEDIA`
- 其余默认为 `TEXT`

常见 Java 类型与 `EntFieldKind` 的明确对应关系：

| Java 类型 / 字段特征 | 推断结果 |
| --- | --- |
| 字段名正好为 `id` | `EntFieldKind.ID` |
| 标注 `@EntRelation` 或字段名以 `Id` 结尾 | `EntFieldKind.REF_ID` |
| `String` | `EntFieldKind.TEXT`，但命中 JSON / 媒体字段名规则时分别推断为 `JSON_DOC` / `MEDIA` |
| `Integer` / `int` / `Long` / `long` / `BigDecimal` / 其他 `Number` 类型 | `EntFieldKind.NUMBER` |
| Java `enum`，例如 `EnumRequestBodyTruncated` | `EntFieldKind.ENUM` |
| `Boolean` / `boolean` | `EntFieldKind.FLAG` |
| `LocalDate` / `LocalDateTime` / `Instant` / `Date` | `EntFieldKind.DATETIME` |

`RICH_CONTENT` 当前没有自动推断规则；富文本字段需要显式声明 `@EntField(EntFieldKind.RICH_CONTENT)`。

## 3. 诊断体系 (Diagnostics)

解析结果被包装在 `MetaDiagnosticResult` 中，包含：
- **`EntEntityDescriptor`**: 解析成功的元数据模型。
- **`List<MetaDiagnostic>`**: 诊断信息列表。

### 诊断级别：
- **`ERROR`**: 致命错误，如缺少 `@EntEntity`、索引字段不存在。会触发 `fail-fast`。
- **`WARN`**: 潜在风险，如显式值冲突。
- **`INFO`**: 信息记录，如记录了哪些值是推断出来的。

## 4. 来源追踪 (Source Tracking)

为了支持多层适配和覆盖逻辑，解析器记录了每个值的来源：
- `META_EXPLICIT`: 来自 `@EntField` 等元注解。
- `INFERRED`: 框架自动推断。
- `DEFAULT`: 框架默认值。

这些信息在后续的 `Merger`（合并器）中起着关键作用，确保业务显式配置能够正确覆盖框架推断值。
