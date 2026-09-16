package com.entloom.crud.core.repository;

import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.Objects;

/** 默认强类型实体仓储工厂。 */
public final class DefaultEntityRepositoryFactory implements EntityRepositoryFactory {
    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final EntityMetaRegistry entityMetaRegistry;
    private final CrudWriteTransactionExecutor transactionExecutor;

    public DefaultEntityRepositoryFactory(
        QueryGateway queryGateway,
        CommandGateway commandGateway,
        EntityMetaRegistry entityMetaRegistry,
        CrudWriteTransactionExecutor transactionExecutor
    ) {
        this.queryGateway = Objects.requireNonNull(queryGateway, "queryGateway 不能为空");
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway 不能为空");
        this.entityMetaRegistry = Objects.requireNonNull(entityMetaRegistry, "entityMetaRegistry 不能为空");
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor, "transactionExecutor 不能为空");
    }

    @Override
    public <T, ID> EntityRepository<T, ID> repository(Class<T> entityType, Class<ID> idType) {
        return new GatewayEntityRepository<T, ID>(
            entityType,
            idType,
            queryGateway,
            commandGateway,
            entityMetaRegistry,
            transactionExecutor
        );
    }
}
