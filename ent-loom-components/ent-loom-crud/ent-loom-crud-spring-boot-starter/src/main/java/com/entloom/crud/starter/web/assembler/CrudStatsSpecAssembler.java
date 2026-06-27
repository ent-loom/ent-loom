package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizer;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizerContext;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizerRegistry;
import com.entloom.crud.core.capability.stats.StatsSpec;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import com.entloom.crud.starter.web.time.CrudTimeFilterResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * stats 请求组装器。
 *
 * 说明：该类只负责流程编排，过滤、排序、载荷解析下沉到专用 resolver。
 */
public class CrudStatsSpecAssembler {
    private final CrudRequestSupport requestSupport;
    private final CrudStatsPayloadResolver payloadResolver;
    private final CrudStatsFilterResolver filterResolver;
    private final CrudStatsSortResolver sortResolver;
    private final StatsPayloadCustomizerRegistry payloadCustomizerRegistry;

    public CrudStatsSpecAssembler(
        CrudRequestSupport requestSupport,
        CrudTimeFilterResolver timeFilterResolver,
        ObjectMapper objectMapper
    ) {
        this(requestSupport, timeFilterResolver, objectMapper, CrudStringFilterPolicy.disabled(), null);
    }

    public CrudStatsSpecAssembler(
        CrudRequestSupport requestSupport,
        CrudTimeFilterResolver timeFilterResolver,
        ObjectMapper objectMapper,
        CrudStringFilterPolicy stringFilterPolicy
    ) {
        this(requestSupport, timeFilterResolver, objectMapper, stringFilterPolicy, null);
    }

    public CrudStatsSpecAssembler(
        CrudRequestSupport requestSupport,
        CrudTimeFilterResolver timeFilterResolver,
        ObjectMapper objectMapper,
        CrudStringFilterPolicy stringFilterPolicy,
        StatsPayloadCustomizerRegistry payloadCustomizerRegistry
    ) {
        this.requestSupport = requestSupport;
        this.payloadResolver = new CrudStatsPayloadResolver(objectMapper);
        this.filterResolver = new CrudStatsFilterResolver(timeFilterResolver, stringFilterPolicy);
        this.sortResolver = new CrudStatsSortResolver();
        this.payloadCustomizerRegistry = payloadCustomizerRegistry;
    }

    /**
     * 将 HTTP 请求组装为统一的 StatsSpec。
     */
    public StatsSpec assembleStats(
        String routeEntity,
        String scene,
        CrudStatsHttpRequest request,
        SubjectContext subject
    ) {
        CrudStatsHttpRequest actualRequest = request == null ? new CrudStatsHttpRequest() : request;
        RequestContractValidator.validateStats(actualRequest);
        actualRequest.setEntityCodes(requestSupport.normalizeEntityCodes(routeEntity, actualRequest.getEntityCodes()));

        List<Class<?>> entityClasses = requestSupport.resolveEntityClasses(actualRequest.getEntityCodes(), routeEntity);
        String normalizedScene = requestSupport.normalizeScene(scene);
        CrudRouteKey routeKey = buildStatsRouteKey(entityClasses, normalizedScene);

        StatsQueryPayload statsPayload = payloadResolver.resolveStatsPayload(actualRequest);
        statsPayload = applyPayloadCustomizer(routeKey, entityClasses, normalizedScene, statsPayload);
        StatsQueryMode statsMode = payloadResolver.resolveStatsMode(actualRequest, statsPayload);

        StatsSpec.Builder builder = StatsSpec.builder();
        builder.scene(normalizedScene);
        builder.rootType(entityClasses.get(0));
        builder.entityClasses(entityClasses);
        builder.subject(subject);
        builder.expandRelations(actualRequest.getOptions().getExpandRelations());
        builder.filters(filterResolver.resolveMergedFilters(actualRequest, entityClasses.get(0)));
        builder.sorts(sortResolver.resolveSorts(actualRequest, statsPayload));
        builder.time(payloadResolver.toQueryTimeRange(actualRequest.getOptions().getTime()));
        builder.payload(statsPayload);
        builder.mode(statsMode);
        builder.includeExecutionMeta(actualRequest.getOptions().includeExecutionMeta());
        applyModePaging(builder, actualRequest, statsMode);
        return builder.build();
    }

    public String resolveRequestId(CrudStatsHttpRequest request) {
        return requestSupport.resolveRequestId(request.getOptions().getRequestId());
    }

    /**
     * 根据 stats 模式补充分页/限制参数。
     */
    private void applyModePaging(StatsSpec.Builder builder, CrudStatsHttpRequest request, StatsQueryMode statsMode) {
        if (statsMode == StatsQueryMode.PAGE) {
            Integer page = request.getOptions().getPage();
            Integer limit = request.getOptions().getLimit();
            builder.page(new PageRequest(page == null ? 1 : page, limit == null ? 10 : limit));
            return;
        }
        if (statsMode == StatsQueryMode.LIST) {
            Integer limit = request.getOptions().getLimit();
            builder.limit(limit == null || limit.intValue() <= 0 ? 200 : limit);
        }
    }

    private CrudRouteKey buildStatsRouteKey(List<Class<?>> entityClasses, String normalizedScene) {
        StatsSpec probe = StatsSpec.builder()
            .rootType(entityClasses.get(0))
            .entityClasses(entityClasses)
            .scene(normalizedScene)
            .build();
        return RouteKeyFactory.buildStatsRoute(probe);
    }

    private StatsQueryPayload applyPayloadCustomizer(
        CrudRouteKey routeKey,
        List<Class<?>> entityClasses,
        String scene,
        StatsQueryPayload payload
    ) {
        if (payloadCustomizerRegistry == null) {
            return payload;
        }
        StatsPayloadCustomizer customizer = payloadCustomizerRegistry.resolveOrNull(routeKey);
        if (customizer == null) {
            return payload;
        }
        StatsQueryPayload customized = customizer.customize(
            new StatsPayloadCustomizerContext(entityClasses.get(0), entityClasses, scene, payload)
        );
        if (customized == null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "stats payload customizer 返回值不能为空");
        }
        return customized;
    }
}
