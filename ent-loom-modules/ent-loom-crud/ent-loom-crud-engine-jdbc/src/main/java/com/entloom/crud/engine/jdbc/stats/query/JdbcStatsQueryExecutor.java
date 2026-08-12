package com.entloom.crud.engine.jdbc.stats.query;

import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.core.capability.stats.StatsQueryExecutor;
import com.entloom.crud.core.capability.stats.StatsValidator;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.CompiledStatsSql;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsResultMeta;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.List;
import java.util.Map;

/**
 * 单表统计查询执行器。
 *
 * 说明：该类只负责执行流程编排，SQL 编译与结果映射分别下沉到专用组件。
 */
public class JdbcStatsQueryExecutor implements StatsQueryExecutor {
    private final GuardedSqlExecutor guardedSqlExecutor;
    private final SqlSecurityGuard sqlSecurityGuard;
    private final StatsValidator statsValidator;
    private final JdbcStatsSqlCompiler sqlCompiler;
    private final JdbcStatsResultMapper resultMapper;

    public JdbcStatsQueryExecutor(GuardedSqlExecutor guardedSqlExecutor, SqlSecurityGuard sqlSecurityGuard, JdbcDialect dialect) {
        this(guardedSqlExecutor, sqlSecurityGuard, dialect, new StatsValidator());
    }

    JdbcStatsQueryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        SqlSecurityGuard sqlSecurityGuard,
        JdbcDialect dialect,
        StatsValidator statsValidator
    ) {
        JdbcDialect resolvedDialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.sqlSecurityGuard = sqlSecurityGuard;
        this.statsValidator = statsValidator == null ? new StatsValidator() : statsValidator;
        this.sqlCompiler = new JdbcStatsSqlCompiler(resolvedDialect, new JdbcStatsPredicateBuilder());
        this.resultMapper = new JdbcStatsResultMapper();
    }

    /**
     * 执行统计查询并返回结构化结果。
     */
    @Override
    public StatsResult execute(StatsSpec spec, EntityMeta rootMeta) {
        StatsQueryPayload payload = spec.getPayload();
        if (payload == null) {
            throw new ValidationException("统计查询 payload 不能为空");
        }
        StatsQueryMode mode = spec.getMode();
        sqlSecurityGuard.beforeCompile(spec);
        statsValidator.validate(spec, rootMeta, payload);

        long start = System.currentTimeMillis();
        CompiledStatsSql compiled = sqlCompiler.compile(spec, rootMeta, payload);
        List<Map<String, Object>> rawRows = guardedSqlExecutor.queryForList(
            compiled.getRowsSql(),
            compiled.getRowsArgs(),
            buildContext(spec, "stats_main")
        );
        Long totalGroups = resolveTotalGroups(spec, payload, compiled);
        Map<String, Object> summary = resolveSummary(spec, payload, compiled, mode);

        StatsResult result = new StatsResult();
        result.setMode(mode);
        if (mode == StatsQueryMode.SCALAR) {
            result.setMetrics(resultMapper.adaptScalar(rawRows, compiled));
        } else {
            result.setColumns(resultMapper.buildColumns(compiled));
            result.setRows(resultMapper.adaptRows(rawRows, compiled));
            if (summary != null) {
                result.setSummary(summary);
            }
            if (mode == StatsQueryMode.PAGE) {
                result.setPage(resultMapper.buildPage(spec, payload, rawRows.size(), totalGroups));
            }
        }

        if (spec.isIncludeExecutionMeta()) {
            StatsResultMeta meta = new StatsResultMeta();
            if (spec.getTime() != null && spec.getTime().getTimezone() != null) {
                meta.setTimezone(spec.getTime().getTimezone());
            }
            meta.setCostMs(System.currentTimeMillis() - start);
            result.setMeta(meta);
        }
        return result;
    }

    /**
     * 分页模式下按需查询总分组数。
     */
    private Long resolveTotalGroups(StatsSpec spec, StatsQueryPayload payload, CompiledStatsSql compiled) {
        if (spec.getMode() != StatsQueryMode.PAGE) {
            return null;
        }
        Object value = guardedSqlExecutor.queryForObject(
            compiled.getTotalGroupsSql(),
            compiled.getTotalGroupsArgs(),
            buildContext(spec, "stats_total")
        );
        Long groups = value == null ? 0L : ((Number) value).longValue();
        if (Boolean.TRUE.equals(payload.getIncludeTotalGroups())) {
            return groups;
        }
        return null;
    }

    /**
     * 列表/分页模式下按需查询汇总指标。
     */
    private Map<String, Object> resolveSummary(
        StatsSpec spec,
        StatsQueryPayload payload,
        CompiledStatsSql compiled,
        StatsQueryMode mode
    ) {
        if (mode == StatsQueryMode.SCALAR) {
            return null;
        }
        if (!Boolean.TRUE.equals(payload.getIncludeSummary())) {
            return null;
        }
        List<Map<String, Object>> rows = guardedSqlExecutor.queryForList(
            compiled.getSummarySql(),
            compiled.getSummaryArgs(),
            buildContext(spec, "stats_summary")
        );
        return resultMapper.adaptSummary(rows, compiled);
    }

    /**
     * 构建 SQL 执行上下文，供安全与日志组件使用。
     */
    private DefaultExecutionContext buildContext(StatsSpec spec, String phase) {
        String routeKey = RouteKeyFactory.buildStatsRouteKey(spec);
        DefaultExecutionContext context = new DefaultExecutionContext(routeKey, spec.getScene());
        context.getAttributes().put("operationDomain", spec.getOperationKey().getDomain().name());
        context.getAttributes().put("operation", spec.getOperationKey().getOperation());
        context.getAttributes().put("phase", phase);
        return context;
    }
}
