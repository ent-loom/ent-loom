package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.capability.command.handler.CrudCommandHandler;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC 单表默认 CRUD 处理器。
 */
public class JdbcCrudCommandHandler<P, R> implements CrudCommandHandler<P, R> {
    private static final Logger log = LoggerFactory.getLogger(JdbcCrudCommandHandler.class);
    /** 实体元数据注册表。 */
    private final EntityMetaRegistry metaRegistry;
    /** 受保护的 SQL 执行器。 */
    private final GuardedSqlExecutor guardedSqlExecutor;
    /** 载荷映射器。 */
    private final CommandPayloadMapper payloadMapper;
    /** 谓词构建器。 */
    private final JdbcWritePredicateBuilder predicateBuilder;
    /** 写入未命中判定器。 */
    private final JdbcWriteMissClassifier writeMissClassifier;
    /** 命令处理配置。 */
    private final JdbcCrudCommandOptions options;

    public JdbcCrudCommandHandler(EntityMetaRegistry metaRegistry, GuardedSqlExecutor guardedSqlExecutor) {
        this(metaRegistry, guardedSqlExecutor, new CommandPayloadMapper(), new JdbcWritePredicateBuilder());
    }

    public JdbcCrudCommandHandler(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcCrudCommandOptions options
    ) {
        this(metaRegistry, guardedSqlExecutor, new CommandPayloadMapper(), new JdbcWritePredicateBuilder(), options);
    }

    JdbcCrudCommandHandler(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        CommandPayloadMapper payloadMapper,
        JdbcWritePredicateBuilder predicateBuilder
    ) {
        this(metaRegistry, guardedSqlExecutor, payloadMapper, predicateBuilder, null);
    }

    JdbcCrudCommandHandler(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        CommandPayloadMapper payloadMapper,
        JdbcWritePredicateBuilder predicateBuilder,
        JdbcCrudCommandOptions options
    ) {
        this.metaRegistry = metaRegistry;
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.payloadMapper = payloadMapper == null ? new CommandPayloadMapper() : payloadMapper;
        this.predicateBuilder = predicateBuilder == null ? new JdbcWritePredicateBuilder() : predicateBuilder;
        this.writeMissClassifier = new JdbcWriteMissClassifier(this.guardedSqlExecutor, this.predicateBuilder);
        this.options = options == null ? new JdbcCrudCommandOptions() : options;
    }

    @Override
    public boolean supports(CommandSpec<P> spec) {
        return spec != null && spec.getOp() != null && spec.getOp() != CommandOperation.ACTION;
    }

    /**
     * 执行当前命令流程。
     */
    @Override
    public R action(CommandSpec<P> spec) {
        switch (spec.getOp()) {
            case CREATE:
                return create(spec);
            case UPDATE:
                return update(spec);
            case DELETE:
                return delete(spec);
            case SAVE_OR_UPDATE:
                return saveOrUpdate(spec);
            case CREATE_BATCH:
            case UPDATE_BATCH:
            case DELETE_BATCH:
            case SAVE_OR_UPDATE_BATCH:
                return batch(spec);
            default:
                throw new ValidationException("不支持的默认命令操作: " + spec.getOp());
        }
    }

    /**
     * 执行创建命令。
     */
    @Override
    public R create(CommandSpec<P> spec) {
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        WriteCommand<Map<String, Object>> command = resolveWriteCommand(meta, spec, CommandOperation.CREATE);
        Map<String, Object> payload = new LinkedHashMap<String, Object>(command.getValues());
        enforceCreateScope(meta, payload, spec);

        List<String> fields = validateCreateFields(meta, payload, spec);
        IdentityInsertPlan identityPlan = resolveCreateIdentity(meta, command);
        if (fields.isEmpty() && !identityPlan.includeInInsert) {
            throw new ValidationException("创建载荷没有可写字段");
        }
        if (identityPlan.includeInInsert) {
            fields.add(0, meta.getIdField());
            payload.put(meta.getIdField(), identityPlan.id);
        }

        String columns = fields.stream().map(meta::resolveColumn).collect(Collectors.joining(","));
        String placeholders = fields.stream().map(f -> "?").collect(Collectors.joining(","));
        String sql = "insert into " + meta.getTable() + " (" + columns + ") values (" + placeholders + ")";
        List<Object> args = fields.stream().map(payload::get).collect(Collectors.toList());
        Object id = identityPlan.id;
        if (identityPlan.generated) {
            id = guardedSqlExecutor.insertAndReturnGeneratedKey(sql, args, context(spec, "main"));
            if (id == null) {
                throw new ValidationException("数据库生成主键未回填: " + meta.getIdField());
            }
        } else {
            guardedSqlExecutor.update(sql, args, context(spec, "main"));
        }
        return buildCreateResult(spec, id);
    }

    /**
     * 执行更新操作。
     */
    @Override
    public R update(CommandSpec<P> spec) {
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        WriteCommand<Map<String, Object>> command = resolveWriteCommand(meta, spec, CommandOperation.UPDATE);
        Map<String, Object> payload = command.getValues();
        List<QueryFilter> targetFilters = resolveTargetFilters(meta, spec, command);

        int removedNonWritableFields = sanitizeNonWritableUpdateFields(meta, payload, spec, targetFilters);

        List<String> setFields = validateUpdateFields(meta, payload);
        if (setFields.isEmpty()) {
            if (removedNonWritableFields > 0) {
                return buildResult(spec, 0);
            }
            throw new ValidationException("更新载荷没有可更新字段");
        }

        String setClause = setFields.stream().map(f -> meta.resolveColumn(f) + " = ?").collect(Collectors.joining(","));
        List<Object> whereArgs = new ArrayList<Object>();
        String whereClause = predicateBuilder.buildEffectiveWriteWhere(meta, spec, targetFilters, true, whereArgs);
        String sql = "update " + meta.getTable() + " set " + setClause + " where " + whereClause;
        List<Object> args = setFields.stream().map(payload::get).collect(Collectors.toCollection(ArrayList::new));
        args.addAll(whereArgs);
        int rows = guardedSqlExecutor.update(sql, args, context(spec, "main"));
        if (rows == 0) {
            writeMissClassifier.classify(meta, spec, targetFilters, "update", context(spec, "post-check"));
        }
        return buildResult(spec, rows);
    }

    /**
     * 执行删除命令。
     */
    @Override
    public R delete(CommandSpec<P> spec) {
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        WriteCommand<Map<String, Object>> command = resolveWriteCommand(meta, spec, CommandOperation.DELETE);

        List<QueryFilter> targetFilters = resolveTargetFilters(meta, spec, command);
        List<Object> whereArgs = new ArrayList<Object>();
        String whereClause = predicateBuilder.buildEffectiveWriteWhere(meta, spec, targetFilters, true, whereArgs);
        String sql;
        if (meta.getLogicDeleteField() != null && !meta.getLogicDeleteField().trim().isEmpty()) {
            String logicDeleteColumn = meta.resolveColumn(meta.getLogicDeleteField());
            sql = "update " + meta.getTable() + " set " + logicDeleteColumn + " = 1 where " + whereClause;
        } else {
            sql = "delete from " + meta.getTable() + " where " + whereClause;
        }
        int rows = guardedSqlExecutor.update(sql, whereArgs, context(spec, "main"));
        if (rows == 0) {
            writeMissClassifier.classify(meta, spec, targetFilters, "delete", context(spec, "post-check"));
        }
        return buildResult(spec, rows);
    }

    /**
     * 执行按主键保存或更新。
     */
    public R saveOrUpdate(CommandSpec<P> spec) {
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        WriteCommand<Map<String, Object>> command = resolveWriteCommand(meta, spec, CommandOperation.SAVE_OR_UPDATE);
        Object id = normalizeIdValue(command.getId());
        if (id == null || !existsById(meta, spec, id)) {
            CommandSpec<Object> createSpec = newChildSpec(spec, copyWithOperation(command, CommandOperation.CREATE), CommandOperation.CREATE);
            R createResult = action((CommandSpec<P>) createSpec);
            return buildSaveOrUpdateResult(spec, CommandOperation.CREATE, idFromResult(createResult, id), rowsFromResult(createResult));
        }
        CommandSpec<Object> updateSpec = newChildSpec(spec, copyWithOperation(command, CommandOperation.UPDATE), CommandOperation.UPDATE);
        R updateResult = action((CommandSpec<P>) updateSpec);
        return buildSaveOrUpdateResult(spec, CommandOperation.UPDATE, id, rowsFromResult(updateResult));
    }

    /**
     * 执行批量命令。
     */
    @Override
    public R batch(CommandSpec<P> spec) {
        Object payload = spec.getPayload();
        if (!(payload instanceof BatchCommand<?>)) {
            throw new ValidationException("批量载荷必须是 BatchCommand");
        }
        BatchCommand<?> batchCommand = (BatchCommand<?>) payload;
        List<? extends WriteCommand<?>> items = batchCommand.getItems();
        if (items.isEmpty()) {
            throw new ValidationException("批量命令 items 不能为空");
        }

        int totalRows = 0;
        List<Map<String, Object>> itemResults = new ArrayList<Map<String, Object>>();
        int index = 0;
        for (WriteCommand<?> item : items) {
            if (item == null) {
                throw new ValidationException("批量命令 item 不能为空");
            }
            CommandOperation childOp = resolveBatchChildOperation(spec.getOp(), item);
            if (childOp == null) {
                throw new ValidationException("批量命令 item.op 不能为空");
            }
            if (isBatchOperation(childOp) || childOp == CommandOperation.ACTION) {
                throw new ValidationException("批量命令不支持子操作: " + childOp);
            }
            CommandSpec<Object> child = newChildSpec(spec, item, childOp);
            R result = action((CommandSpec<P>) child);
            int affectedRows = rowsFromResult(result);
            if (affectedRows == 0 && childOp == CommandOperation.CREATE) {
                affectedRows = 1;
            }
            totalRows += affectedRows;
            itemResults.add(buildBatchItemResult(index, childOp, item, result, affectedRows));
            index++;
        }
        return buildBatchResult(spec, totalRows, itemResults);
    }

    /**
     * 从命令结果中提取影响行数。
     */
    private int rowsFromResult(Object result) {
        if (result instanceof Map<?, ?>) {
            Object rows = ((Map<?, ?>) result).get("rows");
            return rows instanceof Number ? ((Number) rows).intValue() : 0;
        }
        if (!(result instanceof CommandResult<?>)) {
            return 0;
        }
        CommandResult<?> commandResult = (CommandResult<?>) result;
        if (!(commandResult.getData() instanceof Map<?, ?>)) {
            return 0;
        }
        Map<?, ?> map = (Map<?, ?>) commandResult.getData();
        Object rows = map.get("rows");
        return rows instanceof Number ? ((Number) rows).intValue() : 0;
    }

    /**
     * 基于子载荷构建子命令规格。
     */
    private CommandSpec<Object> newChildSpec(CommandSpec<P> source, WriteCommand<?> item, CommandOperation operation) {
        return CommandSpec.<Object>builder()
            .scene(source.getScene())
            .rootType(source.getRootType())
            .entityClasses(source.getEntityClasses())
            .subject(source.getSubject())
            .attributes(source.getAttributes())
            .grantedScope(source.getGrantedScope())
            .governanceScope(source.getGovernanceScope())
            .accessDecision(source.getAccessDecision())
            .resultType(source.getResultType())
            .op(operation)
            .payload(item)
            .expectedVersion(item.getExpectedVersion() == null ? source.getExpectedVersion() : item.getExpectedVersion())
            .targetFilters(item.getTargetFilters())
            .build();
    }

    /**
     * 构建命令执行结果。
     */
    @SuppressWarnings("unchecked")
    private R buildResult(CommandSpec<P> spec, int rows) {
        if (spec.getResultType() == Void.class) {
            return null;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("rows", rows);
        if (CommandResult.class.isAssignableFrom(spec.getResultType())) {
            return (R) CommandResult.success(data);
        }
        if (Number.class.isAssignableFrom(spec.getResultType())) {
            return (R) Integer.valueOf(rows);
        }
        return (R) data;
    }

    /**
     * 构建创建命令结果。
     */
    @SuppressWarnings("unchecked")
    private R buildCreateResult(CommandSpec<P> spec, Object id) {
        if (spec.getResultType() == Void.class) {
            return null;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("rows", 1);
        if (id != null) {
            data.put("id", id);
        }
        if (CommandResult.class.isAssignableFrom(spec.getResultType())) {
            return (R) CommandResult.success(data);
        }
        if (Number.class.isAssignableFrom(spec.getResultType())) {
            return (R) Integer.valueOf(1);
        }
        return (R) data;
    }

    @SuppressWarnings("unchecked")
    private R buildSaveOrUpdateResult(CommandSpec<P> spec, CommandOperation actualOperation, Object id, int rows) {
        if (spec.getResultType() == Void.class) {
            return null;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("operation", actualOperation.name());
        data.put("rows", rows);
        if (id != null) {
            data.put("id", id);
        }
        if (CommandResult.class.isAssignableFrom(spec.getResultType())) {
            return (R) CommandResult.success(data);
        }
        return (R) data;
    }

    @SuppressWarnings("unchecked")
    private R buildBatchResult(CommandSpec<P> spec, int rows, List<Map<String, Object>> itemResults) {
        if (spec.getResultType() == Void.class) {
            return null;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("rows", rows);
        data.put("items", itemResults);
        if (CommandResult.class.isAssignableFrom(spec.getResultType())) {
            return (R) CommandResult.success(data);
        }
        return (R) data;
    }

    @SuppressWarnings("unchecked")
    private WriteCommand<Map<String, Object>> resolveWriteCommand(
        EntityMeta meta,
        CommandSpec<P> spec,
        CommandOperation defaultOperation
    ) {
        Object payload = spec.getPayload();
        if (payload instanceof WriteCommand<?>) {
            WriteCommand<?> command = (WriteCommand<?>) payload;
            Map<String, Object> values = payloadMapper.toMap(command.getValues());
            Object id = normalizeIdValue(command.getId());
            Object payloadId = extractIdFromValues(meta, values);
            if (id != null && payloadId != null && !identityValueEquals(id, payloadId)) {
                throw new ValidationException("WriteCommand.id 与 payload 主键字段不一致: " + meta.getIdField());
            }
            if (id == null) {
                id = payloadId;
            }
            removeIdFields(meta, values);
            return new WriteCommand<Map<String, Object>>(
                command.getOp() == null ? defaultOperation : command.getOp(),
                id,
                values,
                hasFilters(command.getTargetFilters()) ? command.getTargetFilters() : spec.getTargetFilters(),
                command.getExpectedVersion() == null ? spec.getExpectedVersion() : command.getExpectedVersion()
            );
        }
        Map<String, Object> values = payloadMapper.toMap(payload);
        Object id = extractIdFromValues(meta, values);
        removeIdFields(meta, values);
        return new WriteCommand<Map<String, Object>>(
            defaultOperation,
            id,
            values,
            spec.getTargetFilters(),
            spec.getExpectedVersion()
        );
    }

    private IdentityInsertPlan resolveCreateIdentity(EntityMeta meta, WriteCommand<Map<String, Object>> command) {
        validateSingleColumnIdentity(meta);
        EntityIdPolicy idPolicy = meta.getIdPolicy();
        Object id = normalizeIdValue(command.getId());
        if (idPolicy == EntityIdPolicy.EXPLICIT) {
            if (id == null) {
                throw new ValidationException("EXPLICIT 主键策略要求创建载荷包含主键: " + meta.getIdField());
            }
            return IdentityInsertPlan.explicit(id);
        }
        if (idPolicy == EntityIdPolicy.GENERATED) {
            if (id != null) {
                throw new ValidationException("GENERATED 主键策略不允许创建载荷包含主键: " + meta.getIdField());
            }
            return IdentityInsertPlan.generated();
        }
        if (idPolicy == EntityIdPolicy.APPLICATION) {
            if (id == null) {
                throw new ValidationException("APPLICATION 主键策略缺少默认生成器: " + meta.getIdField());
            }
            return IdentityInsertPlan.explicit(id);
        }
        throw new ValidationException("JDBC 默认写入链暂不支持联合主键: " + meta.getIdField());
    }

    private void validateSingleColumnIdentity(EntityMeta meta) {
        String idField = meta.getIdField();
        if (idField == null || idField.trim().isEmpty()) {
            throw new ValidationException("主键字段不能为空");
        }
        if (idField.indexOf(',') >= 0) {
            throw new ValidationException("JDBC 默认写入链暂不支持联合主键: " + idField);
        }
        if (meta.resolveColumn(idField) == null) {
            throw new ValidationException("未知主键字段: " + idField);
        }
    }

    private List<String> validateCreateFields(EntityMeta meta, Map<String, Object> payload, CommandSpec<P> spec) {
        List<String> fields = new ArrayList<String>();
        for (String field : payload.keySet()) {
            EntityFieldMeta fieldMeta = requireWritableFieldMeta(meta, field, "创建");
            if (field.equals(meta.getIdField()) || fieldMeta.isImmutable()) {
                throw new ValidationException("创建载荷包含不可写字段: " + field);
            }
            if (fieldMeta.isScopeField()) {
                if (!isGovernanceScopeField(spec, field)) {
                    if (isStrictCreateScopeFieldResource(meta)) {
                        throw new ValidationException("创建载荷包含未授权范围字段: " + field);
                    }
                    log.warn(
                        "Create payload scope field accepted by compatibility mode: resource={}, field={}, value={}",
                        resourceCode(meta),
                        field,
                        payload.get(field)
                    );
                }
                fields.add(field);
                continue;
            }
            if (!fieldMeta.isWritable()) {
                throw new ValidationException("创建载荷包含不可写字段: " + field);
            }
            fields.add(field);
        }
        return fields;
    }

    private List<String> validateUpdateFields(EntityMeta meta, Map<String, Object> payload) {
        List<String> fields = new ArrayList<String>();
        for (String field : payload.keySet()) {
            EntityFieldMeta fieldMeta = requireWritableFieldMeta(meta, field, "更新");
            if (field.equals(meta.getIdField()) || fieldMeta.isImmutable() || fieldMeta.isScopeField() || !fieldMeta.isWritable()) {
                throw new ValidationException("更新载荷包含不可写字段: " + field);
            }
            fields.add(field);
        }
        return fields;
    }

    private int sanitizeNonWritableUpdateFields(
        EntityMeta meta,
        Map<String, Object> payload,
        CommandSpec<P> spec,
        List<QueryFilter> targetFilters
    ) {
        if (payload == null || payload.isEmpty()) {
            return 0;
        }

        List<String> nonWritableFields = new ArrayList<String>();
        for (String field : payload.keySet()) {
            EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
            if (fieldMeta != null
                && meta.getAllowedFields().contains(field)
                && (field.equals(meta.getIdField()) || fieldMeta.isImmutable() || fieldMeta.isScopeField() || !fieldMeta.isWritable())) {
                nonWritableFields.add(field);
            }
        }
        if (nonWritableFields.isEmpty()) {
            return 0;
        }
        if (options.isIgnoreNonWritableUpdateFields()) {
            for (String field : nonWritableFields) {
                payload.remove(field);
            }
            return nonWritableFields.size();
        }
        if (!options.isIgnoreUnchangedNonWritableUpdateFields()) {
            return 0;
        }

        List<Map<String, Object>> rows = queryCurrentRows(meta, spec, targetFilters, nonWritableFields);
        for (String field : nonWritableFields) {
            Object requested = payload.get(field);
            for (Map<String, Object> row : rows) {
                Object current = currentRowValue(row, meta.resolveColumn(field));
                if (!writeValueEquals(requested, current)) {
                    throw new ValidationException("更新载荷包含不可写字段且值发生变化: " + field);
                }
            }
        }
        for (String field : nonWritableFields) {
            payload.remove(field);
        }
        return nonWritableFields.size();
    }

    private List<Map<String, Object>> queryCurrentRows(
        EntityMeta meta,
        CommandSpec<P> spec,
        List<QueryFilter> targetFilters,
        List<String> fields
    ) {
        String selectColumns = fields.stream().map(meta::resolveColumn).collect(Collectors.joining(","));
        List<Object> whereArgs = new ArrayList<Object>();
        String whereClause = predicateBuilder.buildEffectiveWriteWhere(meta, spec, targetFilters, true, whereArgs);
        String sql = "select " + selectColumns + " from " + meta.getTable() + " where " + whereClause;
        List<Map<String, Object>> rows = guardedSqlExecutor.queryForList(sql, whereArgs, context(spec, "sanitize"));
        if (rows == null || rows.isEmpty()) {
            writeMissClassifier.classify(meta, spec, targetFilters, "update", context(spec, "sanitize-miss"));
        }
        return rows;
    }

    private Object currentRowValue(Map<String, Object> row, String column) {
        if (row == null || column == null) {
            return null;
        }
        if (row.containsKey(column)) {
            return row.get(column);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean writeValueEquals(Object requested, Object current) {
        if (requested == null || current == null) {
            return requested == current;
        }
        if (requested instanceof Number && current instanceof Number) {
            return numberValueEquals((Number) requested, (Number) current);
        }
        BigDecimal requestedNumber = tryBigDecimal(requested);
        BigDecimal currentNumber = tryBigDecimal(current);
        if (requestedNumber != null && currentNumber != null) {
            return requestedNumber.compareTo(currentNumber) == 0;
        }
        Boolean requestedBoolean = tryBoolean(requested);
        Boolean currentBoolean = tryBoolean(current);
        if (requestedBoolean != null && currentBoolean != null) {
            return requestedBoolean.equals(currentBoolean);
        }
        return String.valueOf(requested).trim().equals(String.valueOf(current).trim());
    }

    private BigDecimal tryBigDecimal(Object value) {
        if (value == null || value instanceof Boolean) {
            return null;
        }
        if (value instanceof Number) {
            Number number = (Number) value;
            if (isNonFinite(number)) {
                return null;
            }
            return toBigDecimal(number);
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean tryBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            int number = ((Number) value).intValue();
            if (number == 0) {
                return Boolean.FALSE;
            }
            if (number == 1) {
                return Boolean.TRUE;
            }
            return null;
        }
        String raw = value == null ? null : String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private EntityFieldMeta requireWritableFieldMeta(EntityMeta meta, String field, String operation) {
        EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
        if (fieldMeta == null || !meta.getAllowedFields().contains(field)) {
            throw new ValidationException(operation + "载荷包含未知字段: " + field);
        }
        return fieldMeta;
    }

    private boolean isGovernanceScopeField(CommandSpec<P> spec, String field) {
        CrudDataScope scope = spec == null ? null : spec.getGovernanceScope();
        return scope != null && !scope.isExplicitAll() && scope.getDimensions().containsKey(field);
    }

    private boolean isStrictCreateScopeFieldResource(EntityMeta meta) {
        return options.isStrictCreateScopeFieldResource(
            resourceCode(meta),
            meta == null ? null : meta.getEntityType()
        );
    }

    private String resourceCode(EntityMeta meta) {
        return meta == null || meta.getResourceDescriptor() == null ? null : meta.getResourceDescriptor().getResourceCode();
    }

    private Object extractIdFromValues(EntityMeta meta, Map<String, Object> values) {
        String idField = meta.getIdField();
        if (idField != null && values.containsKey(idField)) {
            return normalizeIdValue(values.get(idField));
        }
        if ((idField == null || !"id".equals(idField)) && values.containsKey("id")) {
            return normalizeIdValue(values.get("id"));
        }
        return null;
    }

    private void removeIdFields(EntityMeta meta, Map<String, Object> values) {
        String idField = meta.getIdField();
        if (idField != null) {
            values.remove(idField);
        }
        if (idField == null || !"id".equals(idField)) {
            values.remove("id");
        }
    }

    private Object normalizeIdValue(Object idValue) {
        if (idValue instanceof String && ((String) idValue).trim().isEmpty()) {
            return null;
        }
        return idValue;
    }

    private boolean identityValueEquals(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number && right instanceof Number) {
            return numberValueEquals((Number) left, (Number) right);
        }
        return left.equals(right);
    }

    private boolean hasFilters(List<QueryFilter> filters) {
        return filters != null && !filters.isEmpty();
    }

    private boolean existsById(EntityMeta meta, CommandSpec<P> spec, Object id) {
        String idColumn = meta.resolveColumn(meta.getIdField());
        if (idColumn == null) {
            throw new ValidationException("未知主键字段: " + meta.getIdField());
        }
        String sql = "select count(1) from " + meta.getTable() + " where " + idColumn + " = ?";
        Object count = guardedSqlExecutor.queryForObject(sql, Collections.singletonList(id), context(spec, "exists"));
        if (count instanceof Number) {
            return ((Number) count).longValue() > 0L;
        }
        return count != null && Long.parseLong(String.valueOf(count)) > 0L;
    }

    private CommandOperation resolveBatchChildOperation(CommandOperation parentOp, WriteCommand<?> item) {
        if (parentOp == CommandOperation.CREATE_BATCH) {
            return CommandOperation.CREATE;
        }
        if (parentOp == CommandOperation.UPDATE_BATCH) {
            return CommandOperation.UPDATE;
        }
        if (parentOp == CommandOperation.DELETE_BATCH) {
            return CommandOperation.DELETE;
        }
        if (parentOp == CommandOperation.SAVE_OR_UPDATE_BATCH) {
            return CommandOperation.SAVE_OR_UPDATE;
        }
        return item.getOp();
    }

    private boolean isBatchOperation(CommandOperation operation) {
        return operation == CommandOperation.CREATE_BATCH
            || operation == CommandOperation.UPDATE_BATCH
            || operation == CommandOperation.DELETE_BATCH
            || operation == CommandOperation.SAVE_OR_UPDATE_BATCH;
    }

    private WriteCommand<Map<String, Object>> copyWithOperation(
        WriteCommand<Map<String, Object>> source,
        CommandOperation operation
    ) {
        return new WriteCommand<Map<String, Object>>(
            operation,
            source.getId(),
            new LinkedHashMap<String, Object>(source.getValues()),
            source.getTargetFilters(),
            source.getExpectedVersion()
        );
    }

    private Map<String, Object> buildBatchItemResult(
        int index,
        CommandOperation childOp,
        WriteCommand<?> item,
        Object result,
        int rows
    ) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("index", index);
        data.put("operation", operationFromResult(result, childOp).name());
        Object id = idFromResult(result, item.getId());
        if (id != null) {
            data.put("id", id);
        }
        data.put("rows", rows);
        return data;
    }

    private CommandOperation operationFromResult(Object result, CommandOperation fallback) {
        Object value = valueFromResultData(result, "operation");
        if (value == null) {
            return fallback;
        }
        CommandOperation operation = CommandOperation.from(String.valueOf(value));
        return operation == null ? fallback : operation;
    }

    private Object idFromResult(Object result, Object fallback) {
        Object id = valueFromResultData(result, "id");
        return id == null ? fallback : id;
    }

    private Object valueFromResultData(Object result, String key) {
        Object data = result;
        if (result instanceof CommandResult<?>) {
            data = ((CommandResult<?>) result).getData();
        }
        if (!(data instanceof Map<?, ?>)) {
            return null;
        }
        return ((Map<?, ?>) data).get(key);
    }

    private DefaultExecutionContext context(CommandSpec<P> spec, String phase) {
        String routeKey = RouteKeyFactory.buildCommandRouteKey(spec);
        DefaultExecutionContext context = new DefaultExecutionContext(routeKey, spec.getScene());
        context.getAttributes().put("operationDomain", spec.getOperationKey().getDomain().name());
        context.getAttributes().put("operation", spec.getOperationKey().getOperation());
        context.getAttributes().put("phase", phase);
        return context;
    }

    /**
     * 校验并注入创建操作所需的数据范围。
     */
    private void enforceCreateScope(EntityMeta meta, Map<String, Object> payload, CommandSpec<P> spec) {
        CrudDataScope scope = spec.getGovernanceScope();
        if (scope == null || scope.isExplicitAll()) {
            return;
        }
        for (Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            String field = entry.getKey();
            if (!meta.getAllowedFields().contains(field)) {
                continue;
            }
            Object expected = entry.getValue();
            Object current = payload.get(field);
            if (current == null) {
                payload.put(field, injectCreateScopeValue(expected, field));
                continue;
            }
            if (!matchesScopeValue(expected, current)) {
                throw new DataScopeDeniedException("创建载荷超出治理范围: " + field);
            }
        }
    }

    private Object injectCreateScopeValue(Object expected, String field) {
        if (expected instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) expected;
            if (values.size() == 1) {
                return values.iterator().next();
            }
            throw new DataScopeDeniedException("创建载荷必须显式提供范围字段: " + field);
        }
        return expected;
    }

    /**
     * 解析命令目标过滤条件。
     */
    private List<QueryFilter> resolveTargetFilters(
        EntityMeta meta,
        CommandSpec<P> spec,
        WriteCommand<Map<String, Object>> command
    ) {
        if (hasFilters(command.getTargetFilters())) {
            return command.getTargetFilters();
        }
        if (hasFilters(spec.getTargetFilters())) {
            return spec.getTargetFilters();
        }
        Object id = normalizeIdValue(command.getId());
        if (id != null) {
            return Collections.singletonList(new QueryFilter(meta.getIdField(), FilterOperator.EQ, id));
        }
        throw new ValidationException(spec.getOp().name().toLowerCase() + " 需要 payload.id 目标选择器");
    }

    private boolean matchesScopeValue(Object expected, Object actual) {
        if (expected instanceof Collection<?>) {
            Collection<?> values = (Collection<?>) expected;
            for (Object value : values) {
                if (matchesSingleValue(value, actual)) {
                    return true;
                }
            }
            return false;
        }
        return matchesSingleValue(expected, actual);
    }

    private boolean matchesSingleValue(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number && actual instanceof Number) {
            return numberValueEquals((Number) expected, (Number) actual);
        }
        return expected.equals(actual);
    }

    private boolean numberValueEquals(Number expected, Number actual) {
        if (isNonFinite(expected) || isNonFinite(actual)) {
            return Double.compare(expected.doubleValue(), actual.doubleValue()) == 0;
        }
        return toBigDecimal(expected).compareTo(toBigDecimal(actual)) == 0;
    }

    private boolean isNonFinite(Number value) {
        if (value instanceof Double) {
            double d = value.doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d);
        }
        if (value instanceof Float) {
            float f = value.floatValue();
            return Float.isNaN(f) || Float.isInfinite(f);
        }
        return false;
    }

    private BigDecimal toBigDecimal(Number value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        if (value instanceof Float || value instanceof Double) {
            return BigDecimal.valueOf(value.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static final class IdentityInsertPlan {
        private final Object id;
        private final boolean includeInInsert;
        private final boolean generated;

        private IdentityInsertPlan(Object id, boolean includeInInsert, boolean generated) {
            this.id = id;
            this.includeInInsert = includeInInsert;
            this.generated = generated;
        }

        private static IdentityInsertPlan explicit(Object id) {
            return new IdentityInsertPlan(id, true, false);
        }

        private static IdentityInsertPlan generated() {
            return new IdentityInsertPlan(null, false, true);
        }
    }
}
