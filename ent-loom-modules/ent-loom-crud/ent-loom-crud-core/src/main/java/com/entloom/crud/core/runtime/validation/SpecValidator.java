package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.idempotency.IdempotencyPolicy;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.stats.StatsSpec;

/**
 * Spec 统一校验器（纯函数）。
 */
public class SpecValidator {
    /** 默认页码。 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认分页大小。 */
    public static final int DEFAULT_PAGE_LIMIT = 10;
    /** 默认列表查询上限。 */
    public static final int DEFAULT_LIST_LIMIT = 200;
    /** 默认最大分页大小。 */
    public static final int DEFAULT_MAX_LIMIT = 1000;
    /** 幂等策略。 */
    private final IdempotencyPolicy idempotencyPolicy;
    /** 查询校验器。 */
    private final QuerySpecValidator querySpecValidator;
    /** 命令校验器。 */
    private final CommandSpecValidator commandSpecValidator;
    /** 导入校验器。 */
    private final ImportSpecValidator importSpecValidator;
    /** 导出校验器。 */
    private final ExportSpecValidator exportSpecValidator;
    /** 统计校验器。 */
    private final StatsSpecValidator statsSpecValidator;

    public SpecValidator() {
        this(new IdempotencyPolicy());
    }

    public SpecValidator(IdempotencyPolicy idempotencyPolicy) {
        this.idempotencyPolicy = idempotencyPolicy == null ? new IdempotencyPolicy() : idempotencyPolicy;
        this.querySpecValidator = new QuerySpecValidator(this);
        this.commandSpecValidator = new CommandSpecValidator(this, this.idempotencyPolicy);
        this.importSpecValidator = new ImportSpecValidator(this);
        this.exportSpecValidator = new ExportSpecValidator(this);
        this.statsSpecValidator = new StatsSpecValidator(this);
    }

    /**
     * 校验查询规格并返回规范化副本。
     */
    public <R> QuerySpec<R> validateQuerySpec(QuerySpec<R> spec) {
        return querySpecValidator.validate(spec);
    }

    /**
     * 校验命令规格并返回规范化副本。
     */
    public <P> CommandSpec<P> validateCommandSpec(CommandSpec<P> spec) {
        return commandSpecValidator.validate(spec);
    }

    /**
     * 校验导入规格并返回规范化副本。
     */
    public ImportSpec validateImportSpec(ImportSpec spec) {
        return importSpecValidator.validate(spec);
    }

    /**
     * 校验导出规格并返回规范化副本。
     */
    public ExportSpec validateExportSpec(ExportSpec spec) {
        return exportSpecValidator.validate(spec);
    }

    /**
     * 校验统计规格并返回规范化副本。
     */
    public StatsSpec validateStatsSpec(StatsSpec spec) {
        return statsSpecValidator.validate(spec);
    }

    /**
     * 校验通用协议字段并返回副本。
     */
    public <S extends BaseSpec> S validateBase(S spec) {
        if (spec == null) {
            throw new ValidationException("请求规范(spec)不能为空");
        }
        if (spec.getRootType() == null) {
            throw new ValidationException("根类型(rootType)不能为空");
        }
        if (spec.getSubject() == null) {
            throw new ValidationException("操作主体(subject)不能为空");
        }
        if (spec.getEntityClasses() != null && !spec.getEntityClasses().isEmpty()) {
            if (!spec.getEntityClasses().get(0).equals(spec.getRootType())) {
                throw new ValidationException("实体类列表(entityClasses)首项必须等于根类型(rootType)");
            }
        }
        return spec;
    }
}
