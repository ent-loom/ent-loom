package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.adapter.AccessEntryResolver;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.starter.web.dto.CrudCommandHttpRequest;
import com.entloom.crud.starter.web.dto.CrudReadHttpRequest;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * HTTP 请求合同校验器。
 */
final class RequestContractValidator {
    private static final Set<String> FORBIDDEN_SERVER_CONTEXT_OPTION_KEYS = forbiddenServerContextOptionKeys();

    private RequestContractValidator() {
    }

    static void validateRead(CrudReadHttpRequest request) {
        if (request == null) {
            return;
        }
        rejectUnsupportedTopLevelFields(request.getExtraFields(), "page/list/findOne/detail 路由");
        rejectUnsupportedOptions(request.getOptions() == null ? null : request.getOptions().getExtraOptions());
    }

    static void validateStats(CrudStatsHttpRequest request) {
        if (request == null) {
            return;
        }
        rejectUnsupportedTopLevelFields(request.getExtraFields(), "stats 路由");
        rejectUnsupportedOptions(request.getOptions() == null ? null : request.getOptions().getExtraOptions());
    }

    static void validateCommand(CrudCommandHttpRequest request) {
        if (request == null) {
            return;
        }
        rejectUnsupportedTopLevelFields(request.getExtraFields(), "command 路由");
        rejectUnsupportedOptions(request.getOptions() == null ? null : request.getOptions().getExtraOptions());
        if (request.getOptions() != null
            && request.getOptions().getTargetFilters() != null
            && !request.getOptions().getTargetFilters().isEmpty()) {
            throw validationError(
                "默认 command HTTP 合同不支持 options.targetFilters，请使用 payload.id 或 payload.items[].id",
                "OPTIONS_TARGET_FILTERS_FORBIDDEN"
            );
        }
    }

    private static void rejectUnsupportedTopLevelFields(Map<String, Object> extraFields, String routeLabel) {
        if (extraFields == null || extraFields.isEmpty()) {
            return;
        }
        throw validationError(routeLabel + " 不支持顶层字段: " + extraFields.keySet(), "UNSUPPORTED_TOP_LEVEL_FIELDS");
    }

    private static void rejectUnsupportedOptions(Map<String, Object> extraOptions) {
        if (extraOptions == null || extraOptions.isEmpty()) {
            return;
        }
        for (String key : extraOptions.keySet()) {
            rejectUnsupportedOption(key);
        }
    }

    private static void rejectUnsupportedOption(String key) {
        String normalized = normalizeOptionKey(key);
        if ("scene".equals(normalized)) {
            throw validationError("options.scene 不允许出现，scene 只能通过路径提供", "OPTIONS_SCENE_FORBIDDEN");
        }
        if ("sortexpression".equals(normalized)) {
            throw validationError("options.sortExpression 已移除，请改用 options.sorts 或 options.sort", "OPTIONS_SORT_EXPRESSION_REMOVED");
        }
        if (FORBIDDEN_SERVER_CONTEXT_OPTION_KEYS.contains(normalized)) {
            throw validationError(
                "options." + key + " 不允许出现，服务端上下文只能由 CrudSpecAttributeContributor 注入",
                "SERVER_CONTEXT_OPTION_FORBIDDEN"
            );
        }
        throw validationError("options." + key + " 不支持，请使用已建模的 options 字段", "UNSUPPORTED_OPTION");
    }

    private static Set<String> forbiddenServerContextOptionKeys() {
        Set<String> keys = new LinkedHashSet<String>();
        addForbiddenOptionKey(keys, "context");
        addForbiddenOptionKey(keys, "ctx");
        addForbiddenOptionKey(keys, "attrs");
        addForbiddenOptionKey(keys, "attributes");
        addForbiddenOptionKey(keys, "attribute");
        addForbiddenOptionKey(keys, AccessEntryResolver.ATTRIBUTE_KEY);
        addForbiddenOptionKey(keys, "crudDataScope");
        addForbiddenOptionKey(keys, "crudDataScopeDimensions");
        addForbiddenOptionKey(keys, "crudExplicitAll");
        return Collections.unmodifiableSet(keys);
    }

    private static void addForbiddenOptionKey(Set<String> keys, String key) {
        keys.add(normalizeOptionKey(key));
    }

    private static String normalizeOptionKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static CrudException validationError(String message, String reason) {
        return new CrudException(CrudErrorCode.VALIDATION_ERROR, message)
            .withStage(CrudErrorStage.HTTP_CONTRACT)
            .withReason(reason);
    }
}
