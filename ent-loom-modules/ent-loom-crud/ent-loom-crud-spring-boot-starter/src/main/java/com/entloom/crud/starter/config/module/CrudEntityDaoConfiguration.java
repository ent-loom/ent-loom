package com.entloom.crud.starter.config.module;

import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 实体 DAO 工厂装配；不依赖 Command 引擎开关，供 Service 和 Gateway 共用。
 */
@Configuration
public class CrudEntityDaoConfiguration {
    /**
     * 在元数据和 JDBC 安全执行器同时就绪时提供 DAO 工厂；业务方可通过 EntityDaoFactory 覆盖。
     */
    @Bean
    @ConditionalOnBean({EntityMetaRegistry.class, GuardedSqlExecutor.class, JdbcInsertScopeDatabaseValidator.class})
    @ConditionalOnMissingBean(EntityDaoFactory.class)
    public JdbcEntityDaoFactory jdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcDialect jdbcDialect,
        JdbcInsertScopeDatabaseValidator insertScopeDatabaseValidator,
        ObjectProvider<CrudWriteTransactionExecutor> transactionExecutorProvider
    ) {
        return new JdbcEntityDaoFactory(
            metaRegistry,
            guardedSqlExecutor,
            jdbcDialect,
            insertScopeDatabaseValidator,
            transactionExecutorProvider.getIfAvailable()
        );
    }
}
