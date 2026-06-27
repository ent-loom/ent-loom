package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CommandActionSceneResolver;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.starter.web.dto.CrudCommandHttpRequest;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 命令 HTTP DTO 到 CommandSpec 的组装器。
 */
@RequiredArgsConstructor
public class CrudCommandSpecAssembler {
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
        new TypeReference<LinkedHashMap<String, Object>>() {
        };

    /** 请求支持组件。 */
    private final CrudRequestSupport requestSupport;
    /** ACTION 场景契约解析器。 */
    private final CommandActionSceneResolver actionSceneResolver;
    /** 实体元数据注册表。 */
    private final EntityMetaRegistry metaRegistry;
    /** 对象映射器。 */
    private final ObjectMapper objectMapper;

    public CommandSpec<Object> assemble(
        String routeEntity,
        CommandOperation routeOp,
        String scene,
        CrudCommandHttpRequest request,
        SubjectContext subject
    ) {
        CrudCommandHttpRequest actualRequest = request == null ? new CrudCommandHttpRequest() : request;
        RequestContractValidator.validateCommand(actualRequest);
        actualRequest.setEntityCodes(requestSupport.normalizeEntityCodes(routeEntity, actualRequest.getEntityCodes()));
        List<Class<?>> entityClasses = requestSupport.resolveEntityClasses(actualRequest.getEntityCodes(), routeEntity);
        String normalizedScene = requestSupport.normalizeScene(scene);

        if (routeOp == CommandOperation.ACTION) {
            if (normalizedScene == null) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "ACTION 路径必须包含 scene");
            }
            if (entityClasses.size() != 1) {
                throw new CrudException(CrudErrorCode.ENTITY_SCOPE_ILLEGAL, "ACTION 仅支持单实体路由，不支持扩展 entityCodes");
            }
        }

        Object normalizedPayload = routeOp == CommandOperation.ACTION
            ? actualRequest.getPayload()
            : normalizeWritePayload(routeOp, actualRequest.getPayload(), metaRegistry.getEntityMeta(entityClasses.get(0)), actualRequest.getOptions().getTargetFilters());

        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .scene(normalizedScene)
            .rootType(entityClasses.get(0))
            .entityClasses(entityClasses)
            .subject(requestSupport.resolveSubject(subject))
            .payload(normalizedPayload)
            .op(routeOp)
            .idempotencyKey(actualRequest.getOptions().getIdempotencyKey())
            .dryRun(actualRequest.getOptions().isDryRunEnabled())
            .expectedVersion(actualRequest.getOptions().getExpectedVersion())
            .targetFilters(actualRequest.getOptions().getTargetFilters())
            .resultType(CommandResult.class)
            .build();

        if (routeOp == CommandOperation.ACTION) {
            spec = convertExecutePayloadBySceneContract(spec, actualRequest.getPayload());
        }
        return spec;
    }

    public String resolveRequestId(CrudCommandHttpRequest request) {
        return requestSupport.resolveRequestId(request.getOptions().getRequestId());
    }

    private Object normalizeWritePayload(
        CommandOperation routeOp,
        Object rawPayload,
        EntityMeta meta,
        List<QueryFilter> targetFilters
    ) {
        if (rawPayload instanceof WriteCommand<?> || rawPayload instanceof BatchCommand<?>) {
            return rawPayload;
        }
        if (isBatchOperation(routeOp)) {
            if (targetFilters != null && !targetFilters.isEmpty()) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "批量默认接口不支持全局 targetFilters");
            }
            return normalizeBatchPayload(routeOp, rawPayload, meta);
        }
        return normalizeSinglePayload(routeOp, rawPayload, meta, targetFilters);
    }

    private Object normalizeSinglePayload(
        CommandOperation routeOp,
        Object rawPayload,
        EntityMeta meta,
        List<QueryFilter> targetFilters
    ) {
        LinkedHashMap<String, Object> values = convertPayloadToMap(rawPayload);
        Object id = extractId(meta, values);
        if (targetFilters != null && !targetFilters.isEmpty()) {
            if (routeOp == CommandOperation.CREATE) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "CREATE 不支持 targetFilters");
            }
            if (id != null) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "payload.id 与 targetFilters 不能同时使用");
            }
        }
        return rawPayload;
    }

    private BatchCommand<Object> normalizeBatchPayload(CommandOperation routeOp, Object rawPayload, EntityMeta meta) {
        LinkedHashMap<String, Object> payload = convertPayloadToMap(rawPayload);
        List<WriteCommand<Object>> commands = new ArrayList<WriteCommand<Object>>();
        CommandOperation itemOperation = toItemOperation(routeOp);
        if (routeOp == CommandOperation.DELETE_BATCH && payload.containsKey("ids")) {
            for (Object id : convertPayloadToList(payload.get("ids"), "payload.ids")) {
                commands.add(new WriteCommand<Object>(itemOperation, normalizeId(id), Collections.<String, Object>emptyMap()));
            }
            return BatchCommand.of(commands);
        }
        List<Object> items = convertPayloadToList(payload.get("items"), "payload.items");
        for (Object item : items) {
            LinkedHashMap<String, Object> values = convertPayloadToMap(item);
            Object id = extractId(meta, values);
            removeIdFields(meta, values);
            commands.add(new WriteCommand<Object>(itemOperation, id, values));
        }
        return BatchCommand.of(commands);
    }

    private boolean isBatchOperation(CommandOperation routeOp) {
        return routeOp == CommandOperation.CREATE_BATCH
            || routeOp == CommandOperation.UPDATE_BATCH
            || routeOp == CommandOperation.DELETE_BATCH
            || routeOp == CommandOperation.SAVE_OR_UPDATE_BATCH;
    }

    private CommandOperation toItemOperation(CommandOperation routeOp) {
        if (routeOp == CommandOperation.CREATE_BATCH) {
            return CommandOperation.CREATE;
        }
        if (routeOp == CommandOperation.UPDATE_BATCH) {
            return CommandOperation.UPDATE;
        }
        if (routeOp == CommandOperation.DELETE_BATCH) {
            return CommandOperation.DELETE;
        }
        if (routeOp == CommandOperation.SAVE_OR_UPDATE_BATCH) {
            return CommandOperation.SAVE_OR_UPDATE;
        }
        return routeOp;
    }

    private LinkedHashMap<String, Object> convertPayloadToMap(Object rawPayload) {
        if (rawPayload == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (rawPayload instanceof Map<?, ?>) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawPayload).entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        try {
            return objectMapper.convertValue(rawPayload, MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "payload 必须是对象: " + resolveConvertError(ex));
        }
    }

    private List<Object> convertPayloadToList(Object rawPayload, String fieldName) {
        if (rawPayload instanceof List<?>) {
            return new ArrayList<Object>((List<?>) rawPayload);
        }
        if (rawPayload instanceof Iterable<?>) {
            List<Object> result = new ArrayList<Object>();
            for (Object item : (Iterable<?>) rawPayload) {
                result.add(item);
            }
            return result;
        }
        throw new CrudException(CrudErrorCode.VALIDATION_ERROR, fieldName + " 必须是数组");
    }

    private Object extractId(EntityMeta meta, Map<String, Object> values) {
        String idField = meta.getIdField();
        if (idField != null && values.containsKey(idField)) {
            return normalizeId(values.get(idField));
        }
        if ((idField == null || !"id".equals(idField)) && values.containsKey("id")) {
            return normalizeId(values.get("id"));
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

    private Object normalizeId(Object id) {
        if (id instanceof String && ((String) id).trim().isEmpty()) {
            return null;
        }
        return id;
    }

    private CommandSpec<Object> convertExecutePayloadBySceneContract(
        CommandSpec<Object> spec,
        Object rawPayload
    ) {
        String canonicalScene = actionSceneResolver.canonicalizeActionScene(
            spec.getRootType(),
            spec.getEntityClasses(),
            spec.getScene()
        );
        CommandActionContract contract = actionSceneResolver.resolveActionContract(
            spec.getRootType(),
            spec.getEntityClasses(),
            canonicalScene
        );
        Object converted = convertPayload(rawPayload, contract);
        return spec.toBuilder().scene(canonicalScene).payload(converted).build();
    }

    /**
     * 将原始请求载荷转换为目标入参类型。
     */
    private Object convertPayload(Object rawPayload, CommandActionContract contract) {
        Class<?> requestType = contract.getRequestType();
        if (Void.class.equals(requestType)) {
            if (rawPayload != null) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "当前 scene 不允许 payload");
            }
            return null;
        }
        if (rawPayload == null || requestType.isInstance(rawPayload)) {
            return rawPayload;
        }
        try {
            return objectMapper.convertValue(rawPayload, requestType);
        } catch (IllegalArgumentException ex) {
            throw new CrudException(
                CrudErrorCode.VALIDATION_ERROR,
                "payload 类型转换失败: " + resolveConvertError(ex)
            );
        }
    }

    private String resolveConvertError(IllegalArgumentException ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.trim().isEmpty() ? "未知字段或非法字段值" : message;
    }
}
