package com.entloom.crud.engine.jdbc.test.support;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.gateway.CommandGatewayImpl;
import com.entloom.crud.core.capability.query.gateway.QueryGatewayImpl;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.governance.audit.LoggingCrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.governance.service.DefaultCrudGovernanceService;
import com.entloom.crud.core.governance.permission.AllowAllCrudPermissionService;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.idempotency.IdempotencyStore;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.runtime.router.DefaultCommandRouter;
import com.entloom.crud.core.runtime.router.DefaultQueryRouter;
import com.entloom.crud.core.runtime.router.CommandRouter;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.engine.jdbc.command.CrudCommandRegistry;
import com.entloom.crud.engine.jdbc.command.JdbcCrudCommandHandler;
import com.entloom.crud.engine.jdbc.command.RegistryBackedCommandEngine;
import com.entloom.crud.engine.jdbc.idempotency.JdbcIdempotencyStore;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.query.JdbcQueryEngine;
import com.entloom.crud.engine.jdbc.query.JdbcQueryExecutor;
import com.entloom.crud.engine.jdbc.query.JdbcQueryCompiler;
import com.entloom.crud.engine.jdbc.query.RootFirstQueryPlanner;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.test.entity.OrderItemTestEntity;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * JDBC 测试基类。
 */
public abstract class EngineJdbcTestSupport {
    protected DataSource dataSource;
    protected JdbcTemplate jdbcTemplate;
    protected EntityMetaRegistry metaRegistry;
    protected QueryGateway queryGateway;
    protected CommandGateway commandGateway;
    protected IdempotencyManager idempotencyManager;
    protected QueryRouter queryRouter;
    protected CommandRouter commandRouter;
    protected SpecValidator specValidator;

    @BeforeEach
    void setUpEngineFixture() {
        this.dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:ent_loom_crud;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa",
            ""
        );
        this.jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("drop table if exists t_order_item");
        jdbcTemplate.execute("drop table if exists t_order");
        jdbcTemplate.execute("create table t_order(id bigint primary key, order_no varchar(64), school_id bigint, is_deleted int)");
        jdbcTemplate.execute("create table t_order_item(id bigint primary key, order_id bigint, student_id bigint, sku_code varchar(64), quantity int, is_deleted int)");

        this.metaRegistry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser()
                .parse(Arrays.<Class<?>>asList(OrderTestEntity.class, OrderItemTestEntity.class))
        );
        metaRegistry.validateOrThrow();

        SqlIdentifierAllowlistValidator whitelist = new SqlIdentifierAllowlistValidator(metaRegistry);
        SqlParameterLimiter paramLimiter = new SqlParameterLimiter();
        SqlSafetyGuard sqlSecurityGuard = new SqlSafetyGuard(whitelist, paramLimiter);
        SqlExecutionLogger sqlExecutionLogger = new SqlExecutionLogger();
        JdbcGuardedSqlExecutor guardedSqlExecutor = new JdbcGuardedSqlExecutor(jdbcTemplate, sqlSecurityGuard, sqlExecutionLogger);

        JdbcQueryEngine defaultQueryEngine = new JdbcQueryEngine(
            metaRegistry,
            new RootFirstQueryPlanner(),
            new JdbcQueryCompiler(),
            new JdbcQueryExecutor(guardedSqlExecutor, metaRegistry),
            sqlSecurityGuard
        );

        CrudCommandRegistry commandRegistry = new CrudCommandRegistry();
        commandRegistry.setDefaultHandler(new JdbcCrudCommandHandler<>(metaRegistry, guardedSqlExecutor));
        RegistryBackedCommandEngine defaultCommandEngine = new RegistryBackedCommandEngine(commandRegistry, sqlSecurityGuard);

        this.queryRouter = new DefaultQueryRouter(defaultQueryEngine);
        this.commandRouter = new DefaultCommandRouter(defaultCommandEngine);

        this.specValidator = new SpecValidator();
        IdempotencyStore idempotencyStore = new JdbcIdempotencyStore(
            jdbcTemplate,
            "entloom_idempotency_record",
            Clock.systemUTC(),
            Duration.ofHours(48)
        );
        ((JdbcIdempotencyStore) idempotencyStore).initializeSchema();
        this.idempotencyManager = new IdempotencyManager(idempotencyStore);
        CrudGovernanceService governanceService = new DefaultCrudGovernanceService(
            metaRegistry,
            specValidator,
            null,
            new AllowAllCrudPermissionService(),
            new AllowAllCrudDataScopeResolver(),
            Collections.emptyList(),
            new LoggingCrudGovernanceAuditRecorder()
        );
        ExecutionPipeline executionPipeline = new ExecutionPipeline(governanceService);
        this.queryGateway = new QueryGatewayImpl(queryRouter, executionPipeline);
        this.commandGateway = new CommandGatewayImpl(
            commandRouter,
            idempotencyManager,
            null,
            executionPipeline
        );
    }

    protected SubjectContext testSubject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        subject.setTenantId("tenant-a");
        return subject;
    }
}
