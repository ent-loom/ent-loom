package com.entloom.crud.spring.config;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudReadResultMode;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.engine.jdbc.command.JdbcCrudCommandOptions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ent-loom-crud 配置项。
 */
@ConfigurationProperties(prefix = "entloom.crud")
@Getter
@Setter
public class CrudProperties {
    private static final long DEFAULT_RETENTION_HOURS = 48L;

    /** SQL 日志配置。 */
    private SqlLog sqlLog = new SqlLog();
    /** 控制器配置。 */
    private Controller controller = new Controller();
    /** Query 能力开关。 */
    private Query query = new Query();
    /** Command 能力开关。 */
    private Command command = new Command();
    /** Import 能力开关。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Import importConfig = new Import();
    /** Export 能力开关。 */
    private Export export = new Export();
    /** 关系查询配置。 */
    private Relation relation = new Relation();
    /** 幂等配置。 */
    private Idempotency idempotency = new Idempotency();
    /** 治理配置。 */
    private Governance governance = new Governance();
    /** Import / Export 配置。 */
    private ImportExport importExport = new ImportExport();

    public void setRelation(Relation relation) {
        this.relation = getOrDefault(relation, Relation::new);
    }

    public void setQuery(Query query) {
        this.query = getOrDefault(query, Query::new);
    }

    public void setCommand(Command command) {
        this.command = getOrDefault(command, Command::new);
    }

    public Import getImport() {
        return importConfig;
    }

    public void setImport(Import importConfig) {
        this.importConfig = getOrDefault(importConfig, Import::new);
    }

    public void setExport(Export export) {
        this.export = getOrDefault(export, Export::new);
    }

    public void setIdempotency(Idempotency idempotency) {
        this.idempotency = getOrDefault(idempotency, Idempotency::new);
    }

    public void setGovernance(Governance governance) {
        this.governance = getOrDefault(governance, Governance::new);
    }

    public void setImportExport(ImportExport importExport) {
        this.importExport = getOrDefault(importExport, ImportExport::new);
    }

    private static <T> T getOrDefault(T value, Supplier<T> defaultSupplier) {
        return value != null ? value : defaultSupplier.get();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, E defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : Enum.valueOf(enumType, normalized.toUpperCase(Locale.ENGLISH));
    }

    private static <T> Set<T> copySet(Set<T> values) {
        return values == null ? new HashSet<>() : new HashSet<>(values);
    }

    private static <T> List<T> copyList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static List<String> copyNonBlankList(List<String> values) {
        List<String> result = new ArrayList<String>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static <E extends Enum<E>> Set<E> copyEnumSet(Set<E> values, Class<E> enumType) {
        return values == null || values.isEmpty() ? EnumSet.noneOf(enumType) : EnumSet.copyOf(values);
    }

    /**
     * SQL 日志配置。
     */
    @Getter
    @Setter
    public static class SqlLog {
        /** 模式配置。 */
        private Mode mode = Mode.SAFE;
        /** 采样率。 */
        private double sampleRate = 0.1d;
        /** 输出模式。 */
        private Output output = Output.TEMPLATE;
        /** 是否格式化输出。 */
        private boolean pretty = false;

        public void setMode(Mode mode) {
            this.mode = getOrDefault(mode, () -> Mode.SAFE);
        }

        public void setMode(String mode) {
            this.mode = parseEnum(mode, Mode.class, Mode.SAFE);
        }

        /**
         * 设置采样率并限制取值范围。
         */
        public void setSampleRate(double sampleRate) {
            this.sampleRate = clamp(sampleRate, 0d, 1d);
        }

        public void setOutput(Output output) {
            this.output = getOrDefault(output, () -> Output.TEMPLATE);
        }

        public void setOutput(String output) {
            this.output = parseEnum(output, Output.class, Output.TEMPLATE);
        }

        /**
         * SQL 日志模式。
         */
        public enum Mode {
            /** 安全模式，仅输出模板与结构信息。 */
            SAFE,
            /** 全量模式，包含参数明细。 */
            FULL
        }

        /**
         * SQL 日志输出内容。
         */
        public enum Output {
            /** 只打印 SQL 模板。 */
            TEMPLATE,
            /** 只打印可执行 SQL。 */
            EXEC,
            /** 同时打印模板 SQL 与可执行 SQL。 */
            BOTH
        }
    }

    /**
     * Controller 子配置。
     */
    @Getter
    @Setter
    public static class Controller {
        /** 默认时区常量。 */
        public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
        /** 默认读结果模式。 */
        public static final CrudReadResultMode DEFAULT_READ_RESULT_MODE = CrudReadResultMode.MAP;
        /** 是否启用框架默认 HTTP 控制器。 */
        private boolean enabled;
        /** 控制器基础路径。 */
        private String basePath = "/api/ent-crud";  // EntCrudQueryController / EntCrudCommandController
        /** time 参数默认时区。 */
        private String defaultTimezone = DEFAULT_TIMEZONE;
        /** time 参数默认时间字段（可选）。 */
        private String defaultTimeField;
        /** 默认读结果模式（MAP/ENTITY）。 */
        private CrudReadResultMode defaultReadResultMode = DEFAULT_READ_RESULT_MODE;
        /** 字符串简写过滤默认策略。 */
        private StringFilter stringFilter = new StringFilter();
        /** 允许暴露的实体集合。 */
        private Set<String> includeEntities = new HashSet<>();

        public void setDefaultTimeField(String defaultTimeField) {
            this.defaultTimeField = trimToNull(defaultTimeField);
        }

        public void setDefaultReadResultMode(String defaultReadResultMode) {
            String normalized = trimToNull(defaultReadResultMode);
            if (normalized == null) {
                this.defaultReadResultMode = DEFAULT_READ_RESULT_MODE;
                return;
            }
            CrudReadResultMode resolved = CrudReadResultMode.from(normalized);
            if (resolved == null) {
                throw new IllegalArgumentException(
                    "entloom.crud.controller.default-read-result-mode 仅支持 MAP 或 ENTITY，当前值: " + defaultReadResultMode
                );
            }
            this.defaultReadResultMode = resolved;
        }

        public void setIncludeEntities(Set<String> includeEntities) {
            this.includeEntities = copySet(includeEntities);
        }

        public void setStringFilter(StringFilter stringFilter) {
            this.stringFilter = getOrDefault(stringFilter, StringFilter::new);
        }
    }

    /**
     * 字符串简写过滤配置。
     */
    @Getter
    @Setter
    public static class StringFilter {
        /** 是否启用 options.filter 的字符串默认 LIKE。 */
        private boolean defaultLikeEnabled = false;
        /** 默认 LIKE 模式。 */
        private LikeMode defaultLikeMode = LikeMode.CONTAINS;
        /** 默认 LIKE 排除字段（大小写不敏感）。 */
        private Set<String> defaultLikeExcludeFields = defaultExcludeFields();

        public void setDefaultLikeMode(LikeMode defaultLikeMode) {
            this.defaultLikeMode = getOrDefault(defaultLikeMode, () -> LikeMode.CONTAINS);
        }

        public void setDefaultLikeMode(String defaultLikeMode) {
            this.defaultLikeMode = parseEnum(defaultLikeMode, LikeMode.class, LikeMode.CONTAINS);
        }

        public void setDefaultLikeExcludeFields(Set<String> defaultLikeExcludeFields) {
            this.defaultLikeExcludeFields = copySet(defaultLikeExcludeFields);
        }

        private static Set<String> defaultExcludeFields() {
            Set<String> fields = new HashSet<String>();
            fields.add("path");
            fields.add("fullPath");
            fields.add("parentPath");
            fields.add("url");
            fields.add("uri");
            return fields;
        }
    }

    /**
     * 默认 LIKE 值补全模式。
     */
    public enum LikeMode {
        CONTAINS,
        PREFIX,
        SUFFIX,
        RAW
    }

    /**
     * Query 子配置。
     */
    @Getter
    @Setter
    public static class Query {
        /** 是否启用 Query 主链。 */
        private boolean enabled = true;
        /** 默认排序配置。 */
        private DefaultSort defaultSort = new DefaultSort();

        public void setDefaultSort(DefaultSort defaultSort) {
            this.defaultSort = getOrDefault(defaultSort, DefaultSort::new);
        }

        /**
         * 查询默认排序配置。
         */
        @Getter
        @Setter
        public static class DefaultSort {
            /** 是否启用默认排序。 */
            private boolean enabled = true;
            /** 生效入口。 */
            private Set<QueryOperation> applyTo = defaultApplyTo();
            /** 候选创建时间字段。 */
            private List<String> timeFields = defaultTimeFields();
            /** 时间字段排序方向。 */
            private SortDirection timeDirection = SortDirection.DESC;
            /** 是否追加主键排序。 */
            private boolean appendId = true;
            /** 主键排序方向。 */
            private SortDirection idDirection = SortDirection.DESC;
            /** 无时间字段时是否回退主键。 */
            private boolean fallbackToId = true;

            public void setApplyTo(Set<QueryOperation> applyTo) {
                this.applyTo = copyEnumSet(applyTo, QueryOperation.class);
            }

            public void setTimeFields(List<String> timeFields) {
                this.timeFields = copyNonBlankList(timeFields);
            }

            public void setTimeDirection(SortDirection timeDirection) {
                this.timeDirection = getOrDefault(timeDirection, () -> SortDirection.DESC);
            }

            public void setTimeDirection(String timeDirection) {
                this.timeDirection = parseEnum(timeDirection, SortDirection.class, SortDirection.DESC);
            }

            public void setIdDirection(SortDirection idDirection) {
                this.idDirection = getOrDefault(idDirection, () -> SortDirection.DESC);
            }

            public void setIdDirection(String idDirection) {
                this.idDirection = parseEnum(idDirection, SortDirection.class, SortDirection.DESC);
            }

            private static Set<QueryOperation> defaultApplyTo() {
                return EnumSet.of(QueryOperation.PAGE, QueryOperation.LIST);
            }

            private static List<String> defaultTimeFields() {
                List<String> fields = new ArrayList<String>();
                fields.add("createTime");
                fields.add("createdAt");
                fields.add("createDate");
                fields.add("createdTime");
                fields.add("gmtCreate");
                fields.add("insertTime");
                return fields;
            }
        }
    }

    /**
     * Command 子配置。
     */
    @Getter
    @Setter
    public static class Command {
        /** 是否启用 Command 主链。 */
        private boolean enabled = true;
        /** 更新时是否忽略值未变化的不可写字段。 */
        private boolean ignoreUnchangedNonWritableUpdateFields = false;
        /** 更新时是否直接忽略不可写字段；开启后值变化也不报错，但字段不会写入数据库。 */
        private boolean ignoreNonWritableUpdateFields = false;
        /** CREATE scope 字段校验模式。 */
        private JdbcCrudCommandOptions.CreateScopeFieldValidationMode createScopeFieldValidationMode =
            JdbcCrudCommandOptions.CreateScopeFieldValidationMode.STRICT_ALL;
        /** STRICT_RESOURCES 模式下仍保持严格 CREATE scope 字段校验的资源。 */
        private Set<String> strictCreateScopeFieldResources = new HashSet<String>();

        public void setCreateScopeFieldValidationMode(
            JdbcCrudCommandOptions.CreateScopeFieldValidationMode createScopeFieldValidationMode
        ) {
            this.createScopeFieldValidationMode = createScopeFieldValidationMode == null
                ? JdbcCrudCommandOptions.CreateScopeFieldValidationMode.STRICT_ALL
                : createScopeFieldValidationMode;
        }

        public void setCreateScopeFieldValidationMode(String createScopeFieldValidationMode) {
            this.createScopeFieldValidationMode = parseEnum(
                createScopeFieldValidationMode,
                JdbcCrudCommandOptions.CreateScopeFieldValidationMode.class,
                JdbcCrudCommandOptions.CreateScopeFieldValidationMode.STRICT_ALL
            );
        }

        public Set<String> getStrictCreateScopeFieldResources() {
            return copySet(strictCreateScopeFieldResources);
        }

        public void setStrictCreateScopeFieldResources(Set<String> strictCreateScopeFieldResources) {
            this.strictCreateScopeFieldResources = copySet(strictCreateScopeFieldResources);
        }
    }

    /**
     * Import 子配置。
     */
    @Getter
    @Setter
    public static class Import {
        /** 是否启用 Import 主链。 */
        private boolean enabled = true;
    }

    /**
     * Export 子配置。
     */
    @Getter
    @Setter
    public static class Export {
        /** 是否启用 Export 主链。 */
        private boolean enabled = true;
    }

    /**
     * Import / Export 子配置。
     */
    @Getter
    @Setter
    public static class ImportExport {
        /** 本地持久化根目录。 */
        private String storageDirectory = System.getProperty("java.io.tmpdir") + "/entloom-crud";
        /** 默认文件保留小时数。 */
        private long retentionHours = 48;
        /** 默认单文件大小上限。 */
        private long maxFileBytes = 50L * 1024L * 1024L;
        public void setStorageDirectory(String storageDirectory) {
            String normalized = trimToNull(storageDirectory);
            this.storageDirectory = normalized == null ? System.getProperty("java.io.tmpdir") + "/entloom-crud" : normalized;
        }

        public void setRetentionHours(long retentionHours) {
            this.retentionHours = positiveOrDefault(retentionHours, DEFAULT_RETENTION_HOURS);
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = positiveOrDefault(maxFileBytes, 50L * 1024L * 1024L);
        }
    }

    /**
     * 关系查询配置。
     */
    @Getter
    @Setter
    public static class Relation {
        /** 是否严格只接受 xxxList 命名，不再兼容旧字段名回退。 */
        private boolean strictCollectionFieldName = true;
        /** 默认关系展开最大深度。 */
        private int maxDepth = 1;
        /** 单次查询最多允许展开的关系边数量。 */
        private int maxExpandEdges = 32;
        /** 是否允许关系展开出现循环。 */
        private boolean allowCycles = false;
        /** 是否允许通过 RelationLoader 执行非 LOCAL_DB 关系。 */
        private boolean allowExternalLoaders = false;

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = Math.max(1, maxDepth);
        }

        public void setMaxExpandEdges(int maxExpandEdges) {
            this.maxExpandEdges = Math.max(1, maxExpandEdges);
        }
    }

    /**
     * 幂等策略配置。
     */
    @Getter
    @Setter
    public static class Idempotency {
        /** 模式配置。 */
        private IdempotencyPolicy.Mode mode = IdempotencyPolicy.Mode.OPTIONAL;
        /** 必填幂等操作集合。 */
        private Set<CommandOperation> requiredOps = EnumSet.noneOf(CommandOperation.class);
        /** 必填幂等场景集合。 */
        private Set<String> requiredScenes = new HashSet<>();
        /** 保留时长（小时）。 */
        private long retentionHours = 48;
        /** 表名。 */
        private String tableName = "entloom_idempotency_record";
        /** 是否自动初始化表结构。 */
        private boolean autoInitializeSchema = true;

        public void setMode(IdempotencyPolicy.Mode mode) {
            this.mode = getOrDefault(mode, () -> IdempotencyPolicy.Mode.OPTIONAL);
        }

        public void setRequiredOps(Set<CommandOperation> requiredOps) {
            this.requiredOps = copyEnumSet(requiredOps, CommandOperation.class);
        }

        public void setRequiredScenes(Set<String> requiredScenes) {
            this.requiredScenes = copySet(requiredScenes);
        }

        public void setRetentionHours(long retentionHours) {
            this.retentionHours = positiveOrDefault(retentionHours, DEFAULT_RETENTION_HOURS);
        }

    }

    /**
     * 治理配置。
     */
    @Getter
    public static class Governance {
        /** 权限规则列表。 */
        private List<PermissionRule> permissionRules = new ArrayList<>();
        /** 审计配置。 */
        private Audit audit = new Audit();

        public void setPermissionRules(List<PermissionRule> permissionRules) {
            this.permissionRules = copyList(permissionRules);
        }

        public void setAudit(Audit audit) {
            this.audit = getOrDefault(audit, Audit::new);
        }
    }

    /**
     * 规则型权限配置。
     */
    @Getter
    @Setter
    public static class PermissionRule {
        /** 资源标识。 */
        private String resource = "*";
        /** 动作标识。 */
        private String action = "*";
        /** 场景标识。 */
        private String scene = "*";
        /** 访问决策。 */
        private AccessDecision decision = AccessDecision.DENY;
        /** 主体标识集合。 */
        private Set<String> subjectIds = new HashSet<>();
        /** 租户标识集合。 */
        private Set<String> tenantIds = new HashSet<>();
        /** 组织标识集合。 */
        private Set<String> orgIds = new HashSet<>();

        public void setDecision(AccessDecision decision) {
            this.decision = getOrDefault(decision, () -> AccessDecision.DENY);
        }

        public void setSubjectIds(Set<String> subjectIds) {
            this.subjectIds = copySet(subjectIds);
        }

        public void setTenantIds(Set<String> tenantIds) {
            this.tenantIds = copySet(tenantIds);
        }

        public void setOrgIds(Set<String> orgIds) {
            this.orgIds = copySet(orgIds);
        }
    }

    /**
     * 治理审计配置。
     */
    @Getter
    @Setter
    public static class Audit {
        /** 是否持久化到 JDBC。 */
        private boolean persistToJdbc = true;
        /** 是否自动初始化表结构。 */
        private boolean autoInitializeSchema = true;
        /** 表名。 */
        private String tableName = "entloom_crud_governance_audit";
    }
}
