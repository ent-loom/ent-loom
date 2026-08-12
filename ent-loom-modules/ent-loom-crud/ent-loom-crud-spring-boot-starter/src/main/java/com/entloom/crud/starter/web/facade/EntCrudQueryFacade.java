package com.entloom.crud.starter.web.facade;

import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudItemData;
import com.entloom.crud.api.model.CrudListData;
import com.entloom.crud.api.model.CrudPageData;
import com.entloom.crud.api.model.CrudPageInfo;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.runtime.context.CrudInvocationContext;
import com.entloom.crud.core.runtime.context.CrudRequestContextHolder;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.starter.web.assembler.CrudQuerySpecAssembler;
import com.entloom.crud.starter.web.assembler.CrudSchemaAssembler;
import com.entloom.crud.starter.web.dto.CrudReadHttpRequest;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 供业务层 Controller 复用的查询门面。
 */
@RequiredArgsConstructor
public class EntCrudQueryFacade {
    private final QueryGateway queryGateway;
    private final CrudSubjectResolver subjectResolver;
    private final CrudQuerySpecAssembler querySpecAssembler;
    private final CrudResponseBuilder crudResponseBuilder;
    private final CrudSchemaAssembler crudSchemaAssembler;

    public CrudResponse<?> page(String entity, String scene, CrudReadHttpRequest request, CrudInvocationContext context) {
        CrudReadHttpRequest actualRequest = request == null ? new CrudReadHttpRequest() : request;
        crudResponseBuilder.bind(querySpecAssembler.resolveRequestId(actualRequest), CrudOperationKey.of(QueryOperation.PAGE));
        return withContext(context, () -> {
            QuerySpec<Object> spec = assemble(QueryOperation.PAGE, entity, scene, actualRequest);
            PageResult<Object> result = queryGateway.page(spec);
            return crudResponseBuilder.success("OK", "OK", toPageData(result, spec), crudSchemaAssembler.queryMeta(spec));
        });
    }

    public CrudResponse<?> list(String entity, String scene, CrudReadHttpRequest request, CrudInvocationContext context) {
        CrudReadHttpRequest actualRequest = request == null ? new CrudReadHttpRequest() : request;
        crudResponseBuilder.bind(querySpecAssembler.resolveRequestId(actualRequest), CrudOperationKey.of(QueryOperation.LIST));
        return withContext(context, () -> {
            QuerySpec<Object> spec = assemble(QueryOperation.LIST, entity, scene, actualRequest);
            List<Object> result = queryGateway.list(spec);
            CrudListData<Object> data = new CrudListData<Object>();
            data.setItems(adaptItems(result, spec));
            return crudResponseBuilder.success("OK", "OK", data, crudSchemaAssembler.queryMeta(spec));
        });
    }

    public CrudResponse<?> findOne(String entity, String scene, CrudReadHttpRequest request, CrudInvocationContext context) {
        CrudReadHttpRequest actualRequest = request == null ? new CrudReadHttpRequest() : request;
        crudResponseBuilder.bind(querySpecAssembler.resolveRequestId(actualRequest), CrudOperationKey.of(QueryOperation.FIND_ONE));
        return withContext(context, () -> {
            QuerySpec<Object> spec = assemble(QueryOperation.FIND_ONE, entity, scene, actualRequest);
            CrudItemData<Object> data = new CrudItemData<Object>();
            data.setItem(adaptItem(queryGateway.findOne(spec), spec));
            return crudResponseBuilder.success("OK", "OK", data, crudSchemaAssembler.queryMeta(spec));
        });
    }

    public CrudResponse<?> detail(String entity, String scene, CrudReadHttpRequest request, CrudInvocationContext context) {
        CrudReadHttpRequest actualRequest = request == null ? new CrudReadHttpRequest() : request;
        crudResponseBuilder.bind(querySpecAssembler.resolveRequestId(actualRequest), CrudOperationKey.of(QueryOperation.DETAIL));
        return withContext(context, () -> {
            QuerySpec<Object> spec = assemble(QueryOperation.DETAIL, entity, scene, actualRequest);
            CrudItemData<Object> data = new CrudItemData<Object>();
            data.setItem(adaptItem(queryGateway.detail(spec), spec));
            return crudResponseBuilder.success("OK", "OK", data, crudSchemaAssembler.queryMeta(spec));
        });
    }

    private <T> T withContext(CrudInvocationContext context, java.util.function.Supplier<T> supplier) {
        CrudInvocationContext actualContext = context == null ? CrudInvocationContext.empty() : context;
        return CrudRequestContextHolder.withAttributes(actualContext.getAttributes(), supplier);
    }

    private QuerySpec<Object> assemble(
        QueryOperation op,
        String entity,
        String scene,
        CrudReadHttpRequest request
    ) {
        return querySpecAssembler.assembleRead(
            op,
            entity,
            scene,
            request,
            subjectResolver.resolveOrThrow()
        );
    }

    private CrudPageData<Object> toPageData(PageResult<Object> source, QuerySpec<Object> spec) {
        CrudPageData<Object> result = new CrudPageData<Object>();
        result.setItems(adaptItems(source.getItems(), spec));
        CrudPageInfo page = new CrudPageInfo();
        page.setPage(source.getPage());
        page.setLimit(source.getLimit());
        page.setTotal(source.getTotal());
        page.setTotalKnown(source.isTotalKnown());
        page.setHasNext(source.getHasNext());
        page.setTotalPages(source.getTotalPages());
        page.setReturned(source.getReturned());
        result.setPage(page);
        return result;
    }

    private List<Object> adaptItems(List<?> source, QuerySpec<Object> spec) {
        List<Object> items = new java.util.ArrayList<Object>();
        if (source == null) {
            return items;
        }
        for (Object item : source) {
            items.add(adaptItem(item, spec));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private Object adaptItem(Object source, QuerySpec<Object> spec) {
        if (source == null) {
            return null;
        }
        Class<?> resultType = spec == null ? null : spec.getResultType();
        if (resultType == null || CrudRecord.class.isAssignableFrom(resultType) || java.util.Map.class.isAssignableFrom(resultType)) {
            if (source instanceof CrudRecord) {
                return source;
            }
            if (source instanceof java.util.Map<?, ?>) {
                return CrudRecord.copyOf((java.util.Map<String, Object>) source);
            }
        }
        return source;
    }
}
