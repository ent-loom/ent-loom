package com.entloom.crud.starter.web.facade;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.api.model.CrudStatsColumns;
import com.entloom.crud.api.model.CrudStatsData;
import com.entloom.crud.api.model.CrudStatsPage;
import com.entloom.crud.api.model.CrudStatsRow;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;
import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.capability.stats.StatsGateway;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsResultColumns;
import com.entloom.crud.core.capability.stats.StatsResultMeta;
import com.entloom.crud.core.capability.stats.StatsResultPage;
import com.entloom.crud.core.capability.stats.StatsResultRow;
import com.entloom.crud.core.capability.stats.StatsSpec;
import com.entloom.crud.starter.web.assembler.CrudSchemaAssembler;
import com.entloom.crud.starter.web.assembler.CrudStatsSpecAssembler;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 统计查询门面。
 */
@RequiredArgsConstructor
public class EntCrudStatsFacade {
    private final StatsGateway statsGateway;
    private final CrudSubjectResolver subjectResolver;
    private final CrudStatsSpecAssembler crudStatsSpecAssembler;
    private final CrudResponseBuilder crudResponseBuilder;
    private final CrudSchemaAssembler crudSchemaAssembler;

    public CrudResponse<CrudStatsData> stats(String entity, String scene, CrudStatsHttpRequest request, CrudInvocationContext context) {
        CrudStatsHttpRequest actualRequest = request == null ? new CrudStatsHttpRequest() : request;
        crudResponseBuilder.bind(crudStatsSpecAssembler.resolveRequestId(actualRequest), CrudOperationKey.of(StatsOperation.QUERY));
        return withContext(context, () -> {
            StatsSpec spec = crudStatsSpecAssembler.assembleStats(
                entity,
                scene,
                actualRequest,
                subjectResolver.resolveOrThrow()
            );
            if (spec.getPayload() == null) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "stats 请求必须提供 stats");
            }
            CrudStatsData data = toStatsData(statsGateway.stats(spec));
            return crudResponseBuilder.success("OK", "OK", data, crudSchemaAssembler.statsMeta(spec, data));
        });
    }

    private <T> T withContext(CrudInvocationContext context, java.util.function.Supplier<T> supplier) {
        CrudInvocationContext actualContext = context == null ? CrudInvocationContext.empty() : context;
        return CrudRequestContextHolder.withAttributes(actualContext.getAttributes(), supplier);
    }

    private CrudStatsData toStatsData(StatsResult source) {
        CrudStatsData data = new CrudStatsData();
        if (source == null) {
            return data;
        }
        StatsQueryMode mode = source.getMode();
        data.setMode(mode == null ? null : mode.name());
        if (source.getMetrics() != null && !source.getMetrics().isEmpty()) {
            data.setMetrics(CrudRecord.copyOf(source.getMetrics()));
        }
        if (source.getColumns() != null) {
            data.setColumns(toStatsColumns(source.getColumns()));
        }
        if (source.getRows() != null && !source.getRows().isEmpty()) {
            data.setRows(toStatsRows(source.getRows()));
        }
        if (source.getSummary() != null && !source.getSummary().isEmpty()) {
            data.setSummary(CrudRecord.copyOf(source.getSummary()));
        }
        if (source.getPage() != null) {
            data.setPage(toStatsPage(source.getPage()));
        }
        if (source.getMeta() != null) {
            CrudRecord metaRecord = toStatsMeta(source.getMeta());
            if (metaRecord != null && !metaRecord.isEmpty()) {
                data.setMeta(metaRecord);
            }
        }
        return data;
    }

    private CrudStatsColumns toStatsColumns(StatsResultColumns source) {
        if (source == null) {
            return null;
        }
        CrudStatsColumns columns = new CrudStatsColumns();
        if (source.getDimensions() != null) {
            for (String item : source.getDimensions()) {
                if (item != null) {
                    columns.getDimensions().add(item);
                }
            }
        }
        if (source.getMetrics() != null) {
            for (String item : source.getMetrics()) {
                if (item != null) {
                    columns.getMetrics().add(item);
                }
            }
        }
        return columns;
    }

    private List<CrudStatsRow> toStatsRows(List<StatsResultRow> source) {
        List<CrudStatsRow> rows = new java.util.ArrayList<CrudStatsRow>();
        for (StatsResultRow item : source) {
            CrudStatsRow row = new CrudStatsRow();
            row.setDimensions(item.getDimensions() == null ? new CrudRecord() : CrudRecord.copyOf(item.getDimensions()));
            row.setMetrics(item.getMetrics() == null ? new CrudRecord() : CrudRecord.copyOf(item.getMetrics()));
            rows.add(row);
        }
        return rows;
    }

    private CrudStatsPage toStatsPage(StatsResultPage source) {
        if (source == null) {
            return null;
        }
        CrudStatsPage page = new CrudStatsPage();
        page.setPage(source.getPage());
        page.setLimit(source.getLimit());
        page.setReturned(source.getReturned());
        page.setNextCursor(source.getNextCursor());
        page.setTotalGroups(source.getTotalGroups());
        return page;
    }

    private CrudRecord toStatsMeta(StatsResultMeta source) {
        if (source == null) {
            return null;
        }
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<String, Object>();
        if (source.getTimezone() != null) {
            values.put("timezone", source.getTimezone());
        }
        if (source.getCostMs() != null) {
            values.put("costMs", source.getCostMs());
        }
        if (values.isEmpty()) {
            return null;
        }
        return CrudRecord.copyOf(values);
    }
}
