package com.entloom.crud.starter.support;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.governance.permission.AllowAllCrudPermissionService;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.engine.jdbc.command.CrudCommandRegistry;
import com.entloom.crud.engine.jdbc.command.JdbcCrudCommandHandler;
import com.entloom.crud.engine.jdbc.command.RegistryBackedCommandEngine;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.log.SqlLogLevel;
import com.entloom.crud.engine.jdbc.query.JdbcQueryEngine;
import com.entloom.crud.engine.jdbc.query.JdbcQueryExecutor;
import com.entloom.crud.engine.jdbc.query.JdbcQueryCompiler;
import com.entloom.crud.engine.jdbc.query.RootFirstQueryPlanner;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.core.capability.stats.StatsQueryExecutor;
import com.entloom.crud.core.capability.stats.StatsGateway;
import com.entloom.crud.core.capability.stats.StatsGatewayImpl;
import com.entloom.crud.core.capability.stats.StatsQueryEngine;
import com.entloom.crud.core.capability.stats.DefaultStatsQueryEngine;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsQueryExecutor;
import com.entloom.crud.starter.web.registry.CrudViewTypeRegistrar;
import com.entloom.crud.starter.web.registry.ExposedEntityRegistry;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Starter 模块测试用 JDBC 支撑配置。
 */
@Configuration
public class StarterJdbcTestSupportConfiguration {
    @Bean
    public DataSource dataSource() {
        return new DriverManagerDataSource(
            "jdbc:h2:mem:starter_crud;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa",
            ""
        );
    }

    @Bean
    public InitializingBean starterCrudSchemaInitializer(DataSource dataSource) {
        return () -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("drop table if exists test_order");
            jdbcTemplate.execute(
                "create table test_order(" +
                    "id bigint primary key," +
                    "order_no varchar(64)," +
                    "status varchar(32)," +
                    "payment_channel varchar(32)," +
                    "paid boolean," +
                    "deleted int," +
                    "total_amount decimal(18,2)," +
                    "discount_amount decimal(18,2)," +
                    "pay_amount decimal(18,2)," +
                    "item_count int," +
                    "order_date date," +
                    "paid_at timestamp," +
                    "created_at timestamp," +
                    "updated_at timestamp" +
                    ")"
            );

            String insertSql =
                "insert into test_order(" +
                    "id,order_no,status,payment_channel,paid,deleted,total_amount,discount_amount,pay_amount,item_count,order_date,paid_at,created_at,updated_at" +
                    ") values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            TestOrderEntity.OrderStatus[] statuses = TestOrderEntity.OrderStatus.values();
            TestOrderEntity.PaymentChannel[] channels = TestOrderEntity.PaymentChannel.values();
            LocalDate baseDate = LocalDate.of(2026, 1, 1);
            LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 9, 0, 0);
            for (int i = 1; i <= 21; i++) {
                Long id = Long.valueOf(i);
                String orderNo = "ORD-" + i;

                String status = i == 1 ? null : statuses[(i - 1) % statuses.length].name();
                String paymentChannel = i == 1 ? null : channels[(i - 1) % channels.length].name();
                Boolean paid = i == 1 ? null : (i % 2 == 0);
                Integer deleted = 0;

                BigDecimal totalAmount = i == 1 ? null : BigDecimal.valueOf(100L + i * 10L);
                BigDecimal discountAmount = i == 1 ? null : BigDecimal.valueOf((i % 4) * 2L);
                BigDecimal payAmount = totalAmount == null ? null : totalAmount.subtract(discountAmount);
                Integer itemCount = i == 1 ? null : Integer.valueOf((i % 5) + 1);

                LocalDate orderDate = i == 1 ? null : baseDate.plusDays(i - 1);
                LocalDateTime createdAt = i == 1 ? null : baseTime.plusDays(i - 1);
                LocalDateTime updatedAt = createdAt == null ? null : createdAt.plusHours(2);
                LocalDateTime paidAt = (createdAt == null || paid == null || !paid) ? null : createdAt.plusHours(1);

                jdbcTemplate.update(
                    insertSql,
                    id,
                    orderNo,
                    status,
                    paymentChannel,
                    paid,
                    deleted,
                    totalAmount,
                    discountAmount,
                    payAmount,
                    itemCount,
                    orderDate == null ? null : Date.valueOf(orderDate),
                    paidAt == null ? null : Timestamp.valueOf(paidAt),
                    createdAt == null ? null : Timestamp.valueOf(createdAt),
                    updatedAt == null ? null : Timestamp.valueOf(updatedAt)
                );
            }
        };
    }

    @Bean
    public EntityMetaRegistry metaRegistry() {
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser()
                .parse(Collections.<Class<?>>singletonList(TestOrderEntity.class))
        );
        registry.validateOrThrow();
        return registry;
    }

    @Bean
    public CrudPermissionService crudPermissionService() {
        return new AllowAllCrudPermissionService();
    }

    @Bean
    public CrudDataScopeResolver crudDataScopeResolver() {
        return new AllowAllCrudDataScopeResolver();
    }

    @Bean
    public CrudSubjectResolver crudSubjectResolver() {
        return () -> {
            SubjectContext subject = new SubjectContext();
            subject.setSubjectId("starter-test-user");
            subject.setTenantId("starter-test-tenant");
            return subject;
        };
    }

    @Bean
    public QueryEngine defaultQueryEngine(DataSource dataSource, EntityMetaRegistry metaRegistry) {
        SqlSafetyGuard sqlSecurityGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(metaRegistry),
            new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor guardedSqlExecutor = new JdbcGuardedSqlExecutor(
            new JdbcTemplate(dataSource),
            sqlSecurityGuard,
            sqlExecutionLogger()
        );
        return new JdbcQueryEngine(
            metaRegistry,
            new RootFirstQueryPlanner(),
            new JdbcQueryCompiler(),
            new JdbcQueryExecutor(guardedSqlExecutor, metaRegistry),
            sqlSecurityGuard
        );
    }

    @Bean
    public StatsQueryExecutor defaultStatsQueryExecutor(DataSource dataSource, EntityMetaRegistry metaRegistry) {
        SqlSafetyGuard sqlSecurityGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(metaRegistry),
            new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor guardedSqlExecutor = new JdbcGuardedSqlExecutor(
            new JdbcTemplate(dataSource),
            sqlSecurityGuard,
            sqlExecutionLogger()
        );
        return new JdbcStatsQueryExecutor(guardedSqlExecutor, sqlSecurityGuard, null);
    }

    @Bean
    public StatsQueryEngine statsQueryEngine(
        EntityMetaRegistry metaRegistry,
        @org.springframework.beans.factory.annotation.Qualifier("defaultStatsQueryExecutor") StatsQueryExecutor statsQueryExecutor
    ) {
        return new DefaultStatsQueryEngine(metaRegistry, statsQueryExecutor);
    }

    @Bean
    public StatsGateway statsGateway(
        StatsQueryEngine statsQueryEngine,
        ExecutionPipeline executionPipeline
    ) {
        return new StatsGatewayImpl(statsQueryEngine, executionPipeline);
    }

    @Bean
    public CommandEngine defaultCommandEngine(DataSource dataSource, EntityMetaRegistry metaRegistry) {
        SqlSafetyGuard sqlSecurityGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(metaRegistry),
            new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor guardedSqlExecutor = new JdbcGuardedSqlExecutor(
            new JdbcTemplate(dataSource),
            sqlSecurityGuard,
            sqlExecutionLogger()
        );
        CrudCommandRegistry registry = new CrudCommandRegistry();
        registry.setDefaultHandler(new JdbcCrudCommandHandler<>(metaRegistry, guardedSqlExecutor));
        return new RegistryBackedCommandEngine(registry, sqlSecurityGuard);
    }

    @Bean
    public ExposedEntityRegistry exposedEntityRegistry() {
        ExposedEntityRegistry registry = new ExposedEntityRegistry();
        registry.expose(TestOrderEntity.class);
        return registry;
    }

    @Bean
    public CrudViewTypeRegistrar testOrderSummaryViewRegistrar() {
        return registry -> {
            registry.register("orderSummary", TestOrderSummaryView.class);
            registry.register(TestOrderSummaryView.class);
        };
    }

    private SqlExecutionLogger sqlExecutionLogger() {
        SqlExecutionLogger logger = new SqlExecutionLogger();
        logger.setMode(SqlLogLevel.FULL);
        return logger;
    }
}
