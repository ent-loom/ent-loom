package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.spec.FilterableSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * SQL 标识符白名单校验器。
 */
@RequiredArgsConstructor
public class SqlIdentifierAllowlistValidator {
    /** 实体元数据注册表。 */
    private final EntityMetaRegistry metaRegistry;

    /**
     * 校验查询字段与排序字段。
     *
     * @param spec 查询 spec
     */
    /**
     * 校验查询规格。
     */
    public void validateQuerySpec(QuerySpec<?> spec) {
        validateFilterableSpec(spec, spec, false);
    }

    /**
     * 校验带过滤/排序视图的规格。
     */
    public void validateFilterableSpec(BaseSpec spec, FilterableSpec filterableSpec, boolean statsSortMode) {
        EntityMeta rootMeta = metaRegistry.getEntityMeta(spec.getRootType());
        RelationGraph relationGraph = metaRegistry.getRelationGraph(spec.getRootType());

        for (QueryFilter filter : filterableSpec.getFilters()) {
            if (filter.getOperator() == null) {
                throw new ValidationException("过滤操作符不能为空");
            }
            validateFieldPath(rootMeta, relationGraph, filter.getField());
        }

        for (QuerySort sort : filterableSpec.getSorts()) {
            if (sort == null) {
                throw new ValidationException("排序条目不能为空");
            }
            if (sort.getDirection() == null) {
                throw new ValidationException("排序方向不能为空");
            }
            SortTarget target = sort.getTarget() == null ? SortTarget.AUTO : sort.getTarget();
            if (!statsSortMode) {
                if (target == SortTarget.METRIC || target == SortTarget.DIMENSION) {
                    throw new ValidationException("非统计查询不支持排序目标: " + target);
                }
                validateFieldPath(rootMeta, relationGraph, sort.getField());
                continue;
            }
            if (target == SortTarget.METRIC || target == SortTarget.DIMENSION) {
                // stats 下指标/维度排序由统计引擎按 payload 定义进行二次解析。
                continue;
            }
            validateFieldPath(rootMeta, relationGraph, sort.getField());
        }
    }

    /**
     * 校验命令载荷字段。
     *
     * @param spec 命令 spec
     */
    /**
     * 校验命令规格。
     */
    public void validateCommandSpec(CommandSpec<?> spec) {
        EntityMeta rootMeta = metaRegistry.getEntityMeta(spec.getRootType());
        RelationGraph relationGraph = metaRegistry.getRelationGraph(spec.getRootType());
        if (isExplicitBatchOperation(spec.getOp())) {
            validateBatchPayload(rootMeta, relationGraph, spec.getPayload());
        } else if (spec.getOp() != CommandOperation.ACTION) {
            validateWritePayload(rootMeta, relationGraph, spec.getPayload());
        }
        for (QueryFilter filter : spec.getTargetFilters()) {
            if (filter.getOperator() == null) {
                throw new ValidationException("目标选择器操作符不能为空");
            }
            validateFieldPath(rootMeta, relationGraph, filter.getField());
        }
    }

    private void validateBatchPayload(EntityMeta rootMeta, RelationGraph relationGraph, Object payload) {
        if (!(payload instanceof BatchCommand<?>)) {
            throw new ValidationException("批量命令载荷必须是 BatchCommand");
        }
        BatchCommand<?> batchCommand = (BatchCommand<?>) payload;
        if (batchCommand.getItems().isEmpty()) {
            throw new ValidationException("批量命令 items 不能为空");
        }
        for (WriteCommand<?> item : batchCommand.getItems()) {
            if (item == null) {
                throw new ValidationException("批量命令 item 不能为空");
            }
            if (item.getOp() != null && (isExplicitBatchOperation(item.getOp()) || item.getOp() == CommandOperation.ACTION)) {
                throw new ValidationException("批量命令不支持子操作: " + item.getOp());
            }
            validateWritePayload(rootMeta, relationGraph, item);
            for (QueryFilter filter : item.getTargetFilters()) {
                if (filter.getOperator() == null) {
                    throw new ValidationException("目标选择器操作符不能为空");
                }
                validateFieldPath(rootMeta, relationGraph, filter.getField());
            }
        }
    }

    private void validateWritePayload(EntityMeta rootMeta, RelationGraph relationGraph, Object payload) {
        if (payload instanceof WriteCommand<?>) {
            WriteCommand<?> command = (WriteCommand<?>) payload;
            Map<String, Object> mapPayload = convertPayloadToMap(command.getValues());
            if (command.getValues() != null && mapPayload == null) {
                throw new ValidationException("写入字段值必须是 Map 或 CrudRecord");
            }
            if (mapPayload != null) {
                validatePayloadFields(rootMeta, mapPayload, false);
            }
            for (QueryFilter filter : command.getTargetFilters()) {
                if (filter.getOperator() == null) {
                    throw new ValidationException("目标选择器操作符不能为空");
                }
                validateFieldPath(rootMeta, relationGraph, filter.getField());
            }
            return;
        }
        Map<String, Object> mapPayload = convertPayloadToMap(payload);
        if (payload != null && mapPayload == null) {
            throw new ValidationException("命令载荷必须是 Map、CrudRecord 或 WriteCommand");
        }
        if (mapPayload != null) {
            validatePayloadFields(rootMeta, mapPayload, false);
        }
    }

    private boolean isExplicitBatchOperation(CommandOperation operation) {
        return operation == CommandOperation.CREATE_BATCH
            || operation == CommandOperation.UPDATE_BATCH
            || operation == CommandOperation.DELETE_BATCH
            || operation == CommandOperation.SAVE_OR_UPDATE_BATCH;
    }

    private void validatePayloadFields(EntityMeta rootMeta, Map<String, Object> payload, boolean allowBatchOp) {
        for (String field : payload.keySet()) {
            if (!rootMeta.getAllowedFields().contains(field) && !(allowBatchOp && "op".equals(field))) {
                throw new ValidationException("未知载荷字段: " + field);
            }
        }
    }

    private Map<String, Object> convertPayloadToMap(Object payload) {
        if (payload instanceof CrudRecord) {
            return ((CrudRecord) payload).asMap();
        }
        if (!(payload instanceof Map<?, ?>)) {
            return null;
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) payload).entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * 校验字段路径是否在白名单内。
     */
    private void validateFieldPath(EntityMeta rootMeta, RelationGraph relationGraph, String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new ValidationException("字段路径不能为空");
        }
        if (!path.contains(".")) {
            if (!rootMeta.getAllowedFields().contains(path)) {
                throw new ValidationException("未知字段: " + path);
            }
            return;
        }

        String[] tokens = path.split("\\.");
        if (tokens.length != 2) {
            throw new ValidationException("仅支持单跳关联字段: " + path);
        }
        String relationName = tokens[0];
        String childField = tokens[1];

        RelationEdge matched = null;
        for (RelationEdge edge : relationGraph.getEdges()) {
            if (edge.getToEntity().getSimpleName().equalsIgnoreCase(relationName)
                || edge.getToEntity().getName().equalsIgnoreCase(relationName)
                || edge.getToEntity().getSimpleName().replace("Entity", "").equalsIgnoreCase(relationName)) {
                matched = edge;
                break;
            }
        }
        if (matched == null) {
            throw new ValidationException("未找到关联关系: " + relationName);
        }
        EntityMeta childMeta = metaRegistry.getEntityMeta(matched.getToEntity());
        if (!childMeta.getAllowedFields().contains(childField)) {
            throw new ValidationException("未知关联字段: " + path);
        }
    }
}
