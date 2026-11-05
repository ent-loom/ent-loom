package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.authorization.*;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.*;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.meta.*;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.*;
import com.entloom.crud.engine.jdbc.transaction.JdbcCrudWriteTransactionExecutor;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

/** 创建的生成主键、输入契约、服务端默认值与事务回滚合同。 */
class JdbcAuthorizedCreateTest {
    private JdbcTemplate jdbc;
    private JdbcAuthorizedCommandExecutor executor;
    private final VisitorContext visitor = VisitorContext.anonymous("test", null, null);
    private final List<CrudGovernanceAuditEvent> events = new ArrayList<>();
    private List<String> writeFields = List.of("name", "email");
    private List<String> requiredFields = List.of("email");

    @BeforeEach
    void prepare() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:create_" + UUID.randomUUID() + ";MODE=MYSQL", "sa", "");
        // 测试每次调用会新建连接，保持内存库直到测试进程结束。
        dataSource.setUrl(dataSource.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table created_customer(id bigint auto_increment primary key, name varchar(32), email varchar(64) unique, owner_id bigint, version int, deleted int)");
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<>();
        fields.put("id", field("id", Long.class, false, null));
        fields.put("name", field("name", String.class, false, "默认姓名"));
        fields.put("email", field("email", String.class, false, null));
        fields.put("ownerId", new EntityFieldMeta("ownerId", Long.class, "owner_id", false, false, true, true, false, true, false));
        fields.put("version", field("version", Integer.class, false, null));
        fields.put("deleted", field("deleted", Integer.class, false, null));
        var resource = new ResourceDescriptor(Customer.class, "customer", null, List.of());
        var meta = new EntityMeta(Customer.class, resource, "created_customer", "id", EntityIdPolicy.GENERATED, "deleted", 0, 1, fields);
        var entities = new CrudRuntimeModelBackedEntityMetaRegistry(CrudRuntimeModel.from(List.of(meta), List.of()));
        var create = new BusinessOperation("customer", "create");
        var operations = new BusinessOperationCatalog(List.of(resource), List.of(new BusinessOperationDeclaration(create, "test")));
        var policies = new EntityPolicyRegistry(List.of(meta), List.of(new EntityPolicyTemplate("create", "test", List.of(
            new EntityPolicyTemplate.Rule("create", PolicyIdentityBranch.publicBranch(), List.of())))), List.of(
                new EntityPolicyBinding(Customer.class, "create", "test", Map.of())), operations, new BusinessIdentityRegistry(List.of()));
        var service = new BusinessAuthorizationService(operations, policies, (current, operation, rule, target) ->
            BusinessPermissionDecision.allow(AuthorizationScope.equal("ownerId", 7L),
                AuthorizationFields.of(Map.of(AuthorizationFields.Use.WRITE, writeFields))));
        var mappings = new CrudOperationMappingRegistry(List.of(meta), operations, List.of(new CrudOperationMapping(
            CrudOperationKey.of(CommandOperation.CREATE), null, create, "test")));
        var adapter = new CrudBusinessAuthorizationAdapter(entities, mappings, service);
        var guard = new SqlSafetyGuard(new SqlIdentifierAllowlistValidator(entities), new SqlParameterLimiter());
        var sql = new JdbcGuardedSqlExecutor(jdbc, guard, new SqlExecutionLogger());
        executor = new JdbcAuthorizedCommandExecutor(entities, adapter, sql, guard, StandardJdbcDialect.GENERIC,
            events::add, new JdbcCrudWriteTransactionExecutor(dataSource), null, (entity, values) -> {},
            new CrudInputContract(Map.of("customer", requiredFields), Map.of()));
    }

    @Test
    void 创建补齐默认值归属和初始版本并返回生成主键() {
        CommandResult<?> response = executor.action(visitor, create(Map.of("email", "a@example.com")));
        long id = ((Number) ((Map<?, ?>) response.getData()).get("id")).longValue();
        Map<String, Object> row = jdbc.queryForMap("select * from created_customer where id=?", id);
        assertEquals("默认姓名", row.get("NAME"));
        assertEquals(7L, row.get("OWNER_ID"));
        assertEquals(0, row.get("VERSION"));
        assertEquals(0, row.get("DELETED"));
        assertTrue(id > 0);
    }

    @Test
    void 创建必填校验先于默认值且禁止显式生成主键及版本() {
        assertThrows(ValidationException.class, () -> executor.action(visitor, create(Map.of("name", "缺邮箱"))));
        assertThrows(ValidationException.class, () -> executor.action(visitor, create(Map.of("id", 99L, "email", "a@example.com"))));
        assertThrows(com.entloom.crud.core.exception.PermissionDeniedException.class, () -> executor.action(visitor,
            create(Map.of("email", "a@example.com", "version", 3))));
        assertEquals(0, jdbc.queryForObject("select count(*) from created_customer", Integer.class));
    }

    @Test
    void 显式null不被默认值覆盖() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("email", "a@example.com");
        input.put("name", null);
        executor.action(visitor, create(input));
        assertNull(jdbc.queryForObject("select name from created_customer", String.class));
        assertNull(input.get("name"));
        assertFalse(input.containsKey("ownerId"));
    }

    @Test
    void 服务端默认值不能满足请求显式必填() {
        requiredFields = List.of("email", "name");
        prepare();
        assertThrows(ValidationException.class, () -> executor.action(visitor, create(Map.of("email", "a@example.com"))));
        assertEquals(0, jdbc.queryForObject("select count(*) from created_customer", Integer.class));
    }

    @Test
    void 默认值可由服务端提供但客户端不能越过字段授权() {
        writeFields = List.of("email");
        assertThrows(com.entloom.crud.core.exception.PermissionDeniedException.class, () -> executor.action(visitor,
            create(Map.of("email", "a@example.com", "name", "越权"))));
        executor.action(visitor, create(Map.of("email", "a@example.com")));
        assertEquals("默认姓名", jdbc.queryForObject("select name from created_customer", String.class));
    }

    @Test
    void 唯一键冲突不留下额外记录() {
        executor.action(visitor, create(Map.of("email", "a@example.com")));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> executor.action(visitor, create(Map.of("email", "a@example.com"))));
        assertEquals(1, jdbc.queryForObject("select count(*) from created_customer", Integer.class));
    }

    @Test
    void 试运行和幂等键不能被静默忽略后写入() {
        var spec = create(Map.of("email", "a@example.com"));
        assertThrows(ValidationException.class, () -> executor.action(visitor, spec.toBuilder().dryRun(true).build()));
        assertThrows(ValidationException.class, () -> executor.action(visitor, spec.toBuilder().idempotencyKey("once").build()));
        assertEquals(0, jdbc.queryForObject("select count(*) from created_customer", Integer.class));
    }

    private EntityFieldMeta field(String name, Class<?> type, boolean scope, Object defaultValue) {
        return new EntityFieldMeta(name, type, name, true, false, true, true, true, scope, false, name, false, defaultValue);
    }

    private CommandSpec<Map<String, Object>> create(Map<String, Object> values) {
        return CommandSpec.<Map<String, Object>>builder().rootType(Customer.class).op(CommandOperation.CREATE)
            .payload(values).resultType(CommandResult.class).build();
    }

    static class Customer { }
}
