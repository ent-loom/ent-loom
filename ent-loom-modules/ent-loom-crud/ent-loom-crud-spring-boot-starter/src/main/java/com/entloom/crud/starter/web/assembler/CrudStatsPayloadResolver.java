package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.starter.web.dto.CrudQueryStats;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import com.entloom.crud.starter.web.dto.CrudTimeFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 负责 stats 请求体的校验、解析和模式推断。
 */
final class CrudStatsPayloadResolver {
    private final ObjectMapper objectMapper;

    CrudStatsPayloadResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验并解析统计载荷。
     */
    StatsQueryPayload resolveStatsPayload(CrudStatsHttpRequest request) {
        CrudQueryStats requestStats = request == null ? null : request.getStats();
        if (requestStats == null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "stats 请求必须提供 stats");
        }
        if (!requestStats.getExtraFields().isEmpty()) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "stats 不支持字段: " + requestStats.getExtraFields().keySet());
        }

        final StatsQueryPayload payload;
        try {
            payload = objectMapper.convertValue(requestStats, StatsQueryPayload.class);
        } catch (IllegalArgumentException ex) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "统计 stats 结构不合法: " + ex.getMessage());
        }

        if (payload.getMetrics() == null || payload.getMetrics().isEmpty()) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "统计 stats.metrics 至少需要 1 项");
        }
        return payload;
    }

    /**
     * 根据请求选项和载荷内容推断统计模式。
     */
    StatsQueryMode resolveStatsMode(CrudStatsHttpRequest request, StatsQueryPayload payload) {
        if (request != null && request.getOptions() != null && request.getOptions().getPage() != null) {
            if (payload.getGroupBy() == null || payload.getGroupBy().isEmpty()) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "无 groupBy 的 stats 不支持分页");
            }
            return StatsQueryMode.PAGE;
        }
        if (payload.getGroupBy() == null || payload.getGroupBy().isEmpty()) {
            return StatsQueryMode.SCALAR;
        }
        return StatsQueryMode.LIST;
    }

    /**
     * 将 HTTP 时间过滤模型转换为 QueryTimeRange。
     */
    QueryTimeRange toQueryTimeRange(CrudTimeFilter timeFilter) {
        if (timeFilter == null) {
            return null;
        }
        QueryTimeRange range = new QueryTimeRange();
        range.setField(timeFilter.getField());
        range.setStart(timeFilter.getStart());
        range.setEnd(timeFilter.getEnd());
        range.setTimezone(timeFilter.getTimezone());
        return range;
    }
}
