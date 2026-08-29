package com.entloom.e5.statictest.fixture;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.patch.UpdatePatchWriteCommandFactory;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客户档案强类型适配器；所有执行统一进入 Gateway。 */
public final class CustomerProfileCrudAdapter {
    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final EntityMetaRegistry metaRegistry;

    public CustomerProfileCrudAdapter(
        QueryGateway queryGateway,
        CommandGateway commandGateway,
        EntityMetaRegistry metaRegistry
    ) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
        this.metaRegistry = metaRegistry;
    }

    public PageResult<CustomerProfile> page(CustomerProfileQuery query) {
        List<QueryFilter> filters = new ArrayList<QueryFilter>();
        if (query.getDisplayName() != null && !query.getDisplayName().trim().isEmpty()) {
            filters.add(new QueryFilter("displayName", FilterOperator.LIKE, query.getDisplayName().trim()));
        }
        QuerySpec<CustomerProfile> spec = QuerySpec.<CustomerProfile>builder()
            .rootType(CustomerProfile.class)
            .entityClasses(java.util.Collections.<Class<?>>singletonList(CustomerProfile.class))
            .op(QueryOperation.PAGE)
            .filters(filters)
            .page(new PageRequest(query.getPageNumber(), query.getPageSize()))
            .resultType(CustomerProfile.class)
            .build();
        return queryGateway.page(spec);
    }

    public Object create(CustomerProfileCreateRequest request) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("displayName", request.getDisplayName());
        values.put("creditLimit", request.getCreditLimit());
        values.put("registeredAt", request.getRegisteredAt());
        values.put("avatarUrl", request.getAvatarUrl());
        return commandGateway.action(command(CommandOperation.CREATE, new WriteCommand<Map<String, Object>>(CommandOperation.CREATE, values)));
    }

    public Object update(UpdatePatch<CustomerProfile> patch) {
        WriteCommand<Map<String, Object>> write = UpdatePatchWriteCommandFactory.create(
            patch,
            metaRegistry.getEntityMeta(CustomerProfile.class)
        );
        return commandGateway.action(command(CommandOperation.UPDATE, write));
    }

    private CommandSpec<WriteCommand<Map<String, Object>>> command(CommandOperation operation, WriteCommand<Map<String, Object>> payload) {
        return CommandSpec.<WriteCommand<Map<String, Object>>>builder()
            .rootType(CustomerProfile.class)
            .entityClasses(java.util.Collections.<Class<?>>singletonList(CustomerProfile.class))
            .op(operation)
            .payload(payload)
            .build();
    }
}
