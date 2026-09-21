package com.entloom.e5.statictest;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CountMode;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.PageQuery;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.gateway.CommandGatewayImpl;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.exception.EntityDaoConstraintException;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.permission.AllowAllCrudPermissionService;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.governance.service.DefaultCrudGovernanceService;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.runtime.router.DefaultCommandRouter;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.runtime.spec.DefaultCrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.engine.jdbc.command.CrudCommandRegistry;
import com.entloom.crud.engine.jdbc.command.JdbcCrudCommandHandler;
import com.entloom.crud.engine.jdbc.command.JdbcEntityDaoCommandHandler;
import com.entloom.crud.engine.jdbc.command.RegistryBackedCommandEngine;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import com.entloom.crud.engine.jdbc.idempotency.JdbcIdempotencyStore;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.JdbcMatchedRowsStartupValidator;
import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.e5.statictest.fixture.DaoOrder;
import com.entloom.e5.statictest.fixture.OrderTestEntity;
import com.entloom.testsupport.mysql.MysqlIntegrationSettings;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.entloom.crud.core.governance.policy.DefaultScenePolicyService;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.crud.core.adapter.AttributeAccessEntryResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D4.1：在实际 MySQL 8 上验收 DAO 的主键、范围、逻辑删除和 matched-rows 语义。
 *
 * <p>默认跳过；通过 mysql-integration profile 并提供隔离连接配置后执行。</p>
 */
@EnabledIfSystemProperty(named = MysqlIntegrationSettings.INTEGRATION_ENABLED_PROPERTY, matches = "true")
class DaoMysqlIntegrationTest {
    private static final EntityType<DaoOrder, Long> ORDER_TYPE = EntityType.of(DaoOrder.class, Long.class);

    @Test
    @DisplayName("MySQL 8 应验证 DAO matched-rows、范围、逻辑删除和 schema 清理")
    void shouldValidateDaoContractOnMysql8() throws Exception {
        MysqlIntegrationSettings settings = MysqlIntegrationSettings.load();
        String schema = "entloom_dao_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource(settings.url(), settings.username(), settings.password()));
        Throwable primaryFailure = null;
        try {
            admin.execute("create database `" + schema + "` character set utf8mb4 collate utf8mb4_0900_ai_ci");
            assertThrows(
                ValidationException.class,
                () -> new JdbcMatchedRowsStartupValidator(
                    dataSource(withSchema(settings.url(), schema, true), settings.username(), settings.password())
                ).validateOrThrow()
            );
            JdbcMatchedRowsStartupValidator validator = new JdbcMatchedRowsStartupValidator(
                dataSource(withSchema(settings.url(), schema, false), settings.username(), settings.password())
            );
            validator.validateOrThrow();
            JdbcTemplate jdbcTemplate = new JdbcTemplate(
                dataSource(withSchema(settings.url(), schema, false), settings.username(), settings.password())
            );
            jdbcTemplate.execute(
                "create table dao_order ("
                    + "id bigint not null,"
                    + "order_no varchar(64) not null,"
                    + "school_id bigint not null default 999,"
                    + "tenant_id varchar(64) collate utf8mb4_0900_ai_ci not null default 'DEFAULT-TENANT',"
                    + "is_deleted int not null,"
                    + "primary key (id),"
                    + "unique key uk_dao_order_order_no (order_no)"
                    + ") engine=InnoDB default character set=utf8mb4 collate=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "create table t_order ("
                    + "id bigint not null,"
                    + "order_no varchar(64),"
                    + "school_id bigint,"
                    + "tenant_id varchar(64) collate utf8mb4_bin,"
                    + "is_deleted int,"
                    + "primary key (id)"
                    + ") engine=InnoDB default character set=utf8mb4 collate=utf8mb4_0900_ai_ci"
            );

            assertTrue(jdbcTemplate.queryForObject("select version()", String.class).startsWith("8."));
            assertEquals(
                "utf8mb4",
                jdbcTemplate.queryForObject("select @@character_set_connection", String.class)
            );
            assertEquals(
                "varchar(64)",
                jdbcTemplate.queryForObject(
                    "select column_type from information_schema.columns"
                        + " where table_schema = database() and table_name = 'dao_order' and column_name = 'order_no'",
                    String.class
                )
            );

            EntityMetaRegistry metaRegistry = new CrudRuntimeModelBackedEntityMetaRegistry(
                new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(DaoOrder.class))
            );
            metaRegistry.validateOrThrow();
            JdbcInsertScopeDatabaseValidator scopeDatabaseValidator = new JdbcInsertScopeDatabaseValidator(
                dataSource(withSchema(settings.url(), schema, false), settings.username(), settings.password()),
                metaRegistry
            );
            assertEquals(1, jdbcTemplate.queryForObject(
                "select 'tenant-a' collate utf8mb4_0900_ai_ci = 'TENANT-A'", Integer.class
            ));
            ValidationException collationError = assertThrows(
                ValidationException.class,
                scopeDatabaseValidator::validateOrThrow
            );
            assertTrue(collationError.getMessage().contains("二进制排序规则"));
            jdbcTemplate.execute(
                "alter table dao_order modify tenant_id varchar(64)"
                    + " character set utf8mb4 collate utf8mb4_bin not null default 'DEFAULT-TENANT'"
            );
            scopeDatabaseValidator.validateOrThrow();
            SqlSafetyGuard securityGuard = new SqlSafetyGuard(
                new SqlIdentifierAllowlistValidator(metaRegistry),
                new SqlParameterLimiter()
            );
            JdbcEntityDaoFactory daoFactory = new JdbcEntityDaoFactory(
                metaRegistry,
                new JdbcGuardedSqlExecutor(jdbcTemplate, securityGuard, new SqlExecutionLogger())
            );
            EntityDao<DaoOrder, Long> dao = daoFactory.scoped(ORDER_TYPE, scoped("tenant-a", "007"));

            DaoOrder order = new DaoOrder();
            order.id = 1L;
            order.orderNo = "DAO-MYSQL-1";
            assertEquals(Long.valueOf(1L), dao.insert(order));
            Map<String, Object> stored = jdbcTemplate.queryForMap(
                "select school_id, tenant_id, is_deleted from dao_order where id = ?", 1L
            );
            assertEquals(Long.valueOf(7L), ((Number) stored.get("school_id")).longValue());
            assertEquals("tenant-a", stored.get("tenant_id"));
            assertFalse("DEFAULT-TENANT".equals(stored.get("tenant_id")));
            assertEquals(Integer.valueOf(0), ((Number) stored.get("is_deleted")).intValue());
            assertEquals("DAO-MYSQL-1", dao.findById(1L).get().orderNo);

            jdbcTemplate.update(
                "insert into dao_order(id, order_no, school_id, tenant_id, is_deleted) values (?, ?, ?, ?, ?)",
                101L, "DAO-MYSQL-PAGE-1", 7L, "tenant-a", 0
            );
            jdbcTemplate.update(
                "insert into dao_order(id, order_no, school_id, tenant_id, is_deleted) values (?, ?, ?, ?, ?)",
                102L, "DAO-MYSQL-PAGE-2", 7L, "tenant-a", 0
            );
            jdbcTemplate.update(
                "insert into dao_order(id, order_no, school_id, tenant_id, is_deleted) values (?, ?, ?, ?, ?)",
                103L, "DAO-MYSQL-PAGE-3", 7L, "tenant-a", 0
            );
            Method pageMethod = MysqlPageDao.class.getMethod("findPage", String.class, PageQuery.class);
            daoFactory.validateCustomMethod(ORDER_TYPE, pageMethod);
            @SuppressWarnings("unchecked")
            PageResult<DaoOrder> page = (PageResult<DaoOrder>) daoFactory.invokeCustom(
                ORDER_TYPE,
                scoped("tenant-a", "007"),
                pageMethod,
                new Object[] {
                    "DAO-MYSQL-PAGE-%",
                    new PageQuery(
                        1,
                        2,
                        Collections.singletonList(new QuerySort("id", SortDirection.DESC)),
                        CountMode.ALWAYS
                    )
                }
            );
            assertEquals(Long.valueOf(3L), page.getTotal());
            assertEquals(2, page.getItems().size());
            assertEquals(Long.valueOf(103L), page.getItems().get(0).id);
            assertTrue(page.getHasNext());

            UpdatePatch<DaoOrder> unchanged = patch(metaRegistry, 1L, "DAO-MYSQL-1");
            assertEquals(1, dao.updateById(1L, unchanged));
            assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from dao_order where id = ? and order_no = ?", Integer.class, 1L, "DAO-MYSQL-1"
            ));

            DaoOrder duplicate = new DaoOrder();
            duplicate.id = 2L;
            duplicate.orderNo = "DAO-MYSQL-1";
            assertThrows(EntityDaoConstraintException.class, () -> dao.insert(duplicate));

            assertThrows(
                EntityDaoWriteMissException.class,
                () -> new JdbcEntityDaoFactory(
                    metaRegistry,
                    new JdbcGuardedSqlExecutor(jdbcTemplate, securityGuard, new SqlExecutionLogger())
                ).scoped(ORDER_TYPE, scoped("tenant-a", 8L)).updateById(1L, unchanged)
            );
            assertEquals(1, dao.deleteById(1L));
            assertFalse(dao.findById(1L).isPresent());
            assertEquals(0, dao.deleteById(1L));

            EntityMetaRegistry orderMetaRegistry = new CrudRuntimeModelBackedEntityMetaRegistry(
                new CrudNativeRuntimeModelParser().parse(
                    Collections.<Class<?>>singletonList(OrderTestEntity.class)
                )
            );
            orderMetaRegistry.validateOrThrow();
            new JdbcInsertScopeDatabaseValidator(
                dataSource(withSchema(settings.url(), schema, false), settings.username(), settings.password()),
                orderMetaRegistry
            ).validateOrThrow();
            SqlSafetyGuard orderSecurityGuard = new SqlSafetyGuard(
                new SqlIdentifierAllowlistValidator(orderMetaRegistry),
                new SqlParameterLimiter()
            );
            GatewayFixture gatewayFixture = gatewayFixture(orderMetaRegistry, jdbcTemplate, orderSecurityGuard);
            EntityType<OrderTestEntity, Long> orderType = EntityType.of(OrderTestEntity.class, Long.class);
            EntityDao<OrderTestEntity, Long> legalDao = gatewayFixture.daoFactory.scoped(orderType, scoped("tenant-a", 7L));
            EntityDao<OrderTestEntity, Long> otherScopeDao = gatewayFixture.daoFactory.scoped(orderType, scoped("tenant-b", 8L));

            Map<String, Object> gatewayCreate = new LinkedHashMap<String, Object>();
            gatewayCreate.put("id", 10L);
            gatewayCreate.put("orderNo", "DAO-GATEWAY-10");
            CommandResult created = gatewayFixture.gateway.action(
                gatewayCommand(CommandOperation.CREATE, "gateway-create-10", gatewayCreate)
            );
            assertTrue(created.isSuccess());
            assertEquals("DAO-GATEWAY-10", legalDao.findById(10L).get().orderNo);
            assertFalse(otherScopeDao.findById(10L).isPresent());

            Map<String, Object> gatewayUpdate = new LinkedHashMap<String, Object>();
            gatewayUpdate.put("id", 10L);
            gatewayUpdate.put("orderNo", "DAO-GATEWAY-10-UPDATED");
            CommandResult updated = gatewayFixture.gateway.action(
                gatewayCommand(CommandOperation.UPDATE, "gateway-update-10", gatewayUpdate)
            );
            assertTrue(updated.isSuccess());
            assertEquals("DAO-GATEWAY-10-UPDATED", legalDao.findById(10L).get().orderNo);

            OrderTestEntity otherOrder = new OrderTestEntity();
            otherOrder.id = 11L;
            otherOrder.orderNo = "DAO-OTHER-11";
            otherScopeDao.insert(otherOrder);
            Map<String, Object> unauthorizedUpdate = new LinkedHashMap<String, Object>();
            unauthorizedUpdate.put("id", 11L);
            unauthorizedUpdate.put("orderNo", "DAO-UNAUTHORIZED");
            assertThrows(
                EntityDaoWriteMissException.class,
                () -> gatewayFixture.gateway.action(
                    gatewayCommand(CommandOperation.UPDATE, "gateway-update-11", unauthorizedUpdate)
                )
            );
            assertFalse(legalDao.findById(11L).isPresent());
            assertEquals("DAO-OTHER-11", otherScopeDao.findById(11L).get().orderNo);

            Map<String, Object> invalidPatch = new LinkedHashMap<String, Object>();
            invalidPatch.put("id", 10L);
            invalidPatch.put("schoolId", 8L);
            assertThrows(
                ValidationException.class,
                () -> gatewayFixture.gateway.action(
                    gatewayCommand(CommandOperation.UPDATE, "gateway-invalid-patch", invalidPatch)
                )
            );

            Map<String, Object> concurrentCreate = new LinkedHashMap<String, Object>();
            concurrentCreate.put("id", 12L);
            concurrentCreate.put("orderNo", "DAO-CONCURRENT-ORIGINAL");
            gatewayFixture.gateway.action(
                gatewayCommand(CommandOperation.CREATE, "gateway-create-12", concurrentCreate)
            );
            assertConcurrentGatewayUpdates(gatewayFixture.gateway);

            CommandResult deleted = gatewayFixture.gateway.action(
                gatewayCommand(CommandOperation.DELETE, "gateway-delete-10", singletonPayload(10L))
            );
            assertTrue(deleted.isSuccess());
            assertFalse(legalDao.findById(10L).isPresent());
            assertThrows(
                EntityDaoWriteMissException.class,
                () -> gatewayFixture.gateway.action(
                    gatewayCommand(CommandOperation.DELETE, "gateway-delete-10-again", singletonPayload(10L))
                )
            );
            assertTrue(gatewayFixture.auditRecorder.getEvents().size() >= 6);
        } catch (RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                admin.execute("drop database if exists `" + schema + "`");
                Integer remaining = admin.queryForObject(
                    "select count(*) from information_schema.schemata where schema_name = ?",
                    Integer.class,
                    schema
                );
                if (remaining != null && remaining.intValue() != 0) {
                    throw new IllegalStateException("MySQL DAO 验收 schema 清理失败: " + schema);
                }
            } catch (RuntimeException cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private EntityAccessScope scoped(String tenantId, Object schoolId) {
        return EntityAccessScope.of(RowConstraint.and(
            RowConstraint.eq("schoolId", schoolId),
            RowConstraint.eq("tenantId", tenantId)
        ));
    }

    private CrudDataScope dataScoped(String tenantId, Long schoolId) {
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        dimensions.put("schoolId", schoolId);
        dimensions.put("tenantId", tenantId);
        return CrudDataScope.scoped(dimensions);
    }

    private UpdatePatch<DaoOrder> patch(EntityMetaRegistry metaRegistry, Long id, String orderNo) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("id", id);
        values.put("orderNo", orderNo);
        EntityMeta meta = metaRegistry.getEntityMeta(DaoOrder.class);
        return new DefaultCommandPayloadBinder().bindUpdatePatch(values, DaoOrder.class, meta);
    }

    private void assertConcurrentGatewayUpdates(CommandGateway gateway) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CommandResult> first = executor.submit(() -> gatewayUpdate(gateway, ready, start, "A"));
            Future<CommandResult> second = executor.submit(() -> gatewayUpdate(gateway, ready, start, "B"));
            ready.await();
            start.countDown();
            assertTrue(first.get().isSuccess());
            assertTrue(second.get().isSuccess());
        } finally {
            executor.shutdownNow();
        }
    }

    private CommandResult gatewayUpdate(
        CommandGateway gateway,
        CountDownLatch ready,
        CountDownLatch start,
        String suffix
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 12L);
        payload.put("orderNo", "DAO-CONCURRENT-" + suffix);
        return gateway.action(gatewayCommand(CommandOperation.UPDATE, "gateway-concurrent-" + suffix, payload));
    }

    private Map<String, Object> singletonPayload(Long id) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", id);
        return payload;
    }

    private CommandSpec<Object> gatewayCommand(
        CommandOperation operation,
        String idempotencyKey,
        Map<String, Object> payload
    ) {
        return CommandSpec.<Object>builder()
            .rootType(OrderTestEntity.class)
            .op(operation)
            .idempotencyKey(idempotencyKey)
            .resultType(CommandResult.class)
            .payload(payload)
            .build();
    }

    private GatewayFixture gatewayFixture(
        EntityMetaRegistry metaRegistry,
        JdbcTemplate jdbcTemplate,
        SqlSafetyGuard securityGuard
    ) {
        JdbcEntityDaoFactory daoFactory = new JdbcEntityDaoFactory(
            metaRegistry,
            new JdbcGuardedSqlExecutor(jdbcTemplate, securityGuard, new SqlExecutionLogger())
        );
        JdbcCrudCommandHandler<Object, Object> fallback = new JdbcCrudCommandHandler<Object, Object>(
            metaRegistry,
            new JdbcGuardedSqlExecutor(jdbcTemplate, securityGuard, new SqlExecutionLogger())
        );
        CrudCommandRegistry commandRegistry = new CrudCommandRegistry();
        commandRegistry.setDefaultHandler(fallback);
        commandRegistry.register(
            OrderTestEntity.class,
            new JdbcEntityDaoCommandHandler<Object, Object>(metaRegistry, daoFactory, fallback)
        );
        RegistryBackedCommandEngine commandEngine = new RegistryBackedCommandEngine(commandRegistry, securityGuard);
        DefaultCommandRouter commandRouter = new DefaultCommandRouter(commandEngine);

        RecordingAuditRecorder auditRecorder = new RecordingAuditRecorder();
        CrudGovernanceService governanceService = new DefaultCrudGovernanceService(
            metaRegistry,
            new SpecValidator(),
            new CrudSubjectResolver() {
                @Override
                public SubjectContext resolveOrThrow() {
                    SubjectContext subject = new SubjectContext();
                    subject.setSubjectId("dao-mysql-gateway");
                    subject.setTenantId("tenant-a");
                    return subject;
                }
            },
            new AllowAllCrudPermissionService(),
            new CrudDataScopeResolver() {
                @Override
                public CrudDataScope resolveQueryScope(
                    CrudResourceAction action,
                    SubjectContext subject,
                    com.entloom.crud.core.capability.query.spec.QuerySpec<?> spec
                ) {
                    return dataScoped("tenant-a", 7L);
                }

                @Override
                public CrudDataScope resolveCommandScope(
                    CrudResourceAction action,
                    SubjectContext subject,
                    CommandSpec<?> spec
                ) {
                    return dataScoped("tenant-a", 7L);
                }
            },
            Collections.emptyList(),
            auditRecorder,
            new DefaultCrudSpecAttributeResolver(),
            new DefaultScenePolicyService(new ScenePolicyRegistry(null), new AttributeAccessEntryResolver())
        );
        ExecutionPipeline pipeline = new ExecutionPipeline(governanceService);
        JdbcIdempotencyStore idempotencyStore = new JdbcIdempotencyStore(
            jdbcTemplate,
            "entloom_idempotency_record",
            Clock.systemUTC(),
            Duration.ofHours(48)
        );
        idempotencyStore.initializeSchema();
        return new GatewayFixture(
            new CommandGatewayImpl(
                commandRouter,
                new IdempotencyManager(idempotencyStore),
                null,
                pipeline
            ),
            daoFactory,
            auditRecorder
        );
    }

    private static final class GatewayFixture {
        private final CommandGateway gateway;
        private final JdbcEntityDaoFactory daoFactory;
        private final RecordingAuditRecorder auditRecorder;

        private GatewayFixture(
            CommandGateway gateway,
            JdbcEntityDaoFactory daoFactory,
            RecordingAuditRecorder auditRecorder
        ) {
            this.gateway = gateway;
            this.daoFactory = daoFactory;
            this.auditRecorder = auditRecorder;
        }
    }

    interface MysqlPageDao {
        @EntQuery("select * from dao_order where order_no like :orderNo")
        PageResult<DaoOrder> findPage(String orderNo, PageQuery pageQuery);
    }

    private static final class RecordingAuditRecorder implements CrudGovernanceAuditRecorder {
        private final List<CrudGovernanceAuditEvent> events = new ArrayList<CrudGovernanceAuditEvent>();

        @Override
        public void record(CrudGovernanceAuditEvent event) {
            events.add(event);
        }

        private List<CrudGovernanceAuditEvent> getEvents() {
            return Collections.unmodifiableList(new ArrayList<CrudGovernanceAuditEvent>(events));
        }
    }

    private DataSource dataSource(String url, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private String withSchema(String baseUrl, String schema, boolean useAffectedRows) {
        int queryStart = baseUrl.indexOf('?');
        String authority = queryStart < 0 ? baseUrl : baseUrl.substring(0, queryStart);
        String query = queryStart < 0 ? "" : baseUrl.substring(queryStart + 1);
        int slash = authority.lastIndexOf('/');
        StringBuilder result = new StringBuilder(authority.substring(0, slash + 1)).append(schema);
        StringBuilder parameters = new StringBuilder();
        if (!query.isEmpty()) {
            for (String parameter : query.split("&")) {
                if (!parameter.toLowerCase(java.util.Locale.ROOT).startsWith("useaffectedrows=")) {
                    if (parameters.length() > 0) {
                        parameters.append('&');
                    }
                    parameters.append(parameter);
                }
            }
        }
        if (parameters.length() > 0) {
            parameters.append('&');
        }
        parameters.append("useAffectedRows=").append(useAffectedRows);
        return result.append('?').append(parameters).toString();
    }
}
