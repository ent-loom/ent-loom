package com.entloom.e5.statictest;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.e5.statictest.fixture.CustomerProfile;
import com.entloom.e5.statictest.fixture.CustomerProfileCreateRequest;
import com.entloom.e5.statictest.fixture.CustomerProfileCrudAdapter;
import com.entloom.e5.statictest.fixture.CustomerProfileQuery;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.entloom.crud.starter.config.CrudAutoConfiguration;

/** U3：验证强类型 Adapter 与等价基础 Spec 共享治理、默认引擎和审计语义。 */
class CustomerProfileCrudAdapterEquivalenceTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            E5CrudMvcAcceptanceTest.E5CrudMvcTestConfiguration.class,
            CrudAutoConfiguration.class
        )
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.sql-log.mode=full"
        );

    @Test
    @DisplayName("CustomerProfile Adapter 与基础 Spec 的结果、SQL 状态和审计应一致")
    void typed_adapter_should_match_equivalent_basic_specs() {
        contextRunner.run(this::assertEquivalentExecution);
    }

    private void assertEquivalentExecution(AssertableApplicationContext context) {
        JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        QueryGateway queryGateway = context.getBean(QueryGateway.class);
        CommandGateway commandGateway = context.getBean(CommandGateway.class);
        EntityMetaRegistry metaRegistry = context.getBean(EntityMetaRegistry.class);
        CustomerProfileCrudAdapter adapter = new CustomerProfileCrudAdapter(
            queryGateway,
            commandGateway,
            metaRegistry
        );

        long auditBefore = maxAuditId(jdbc);
        CustomerProfileCreateRequest request = new CustomerProfileCreateRequest(
            1001L,
            "等价用户",
            new java.math.BigDecimal("100.00"),
            LocalDateTime.of(2026, 1, 1, 0, 0),
            "https://example.test/equivalent.png"
        );

        long typedId = resultId(adapter.create(request));
        CustomerProfileCreateRequest basicRequest = new CustomerProfileCreateRequest(
            1002L,
            request.getDisplayName(),
            request.getCreditLimit(),
            request.getRegisteredAt(),
            request.getAvatarUrl()
        );
        long basicId = resultId(commandGateway.action(createSpec(basicRequest)));
        assertProfileDataEquals(jdbc, typedId, basicId);

        PageResult<CustomerProfile> typedPage = adapter.page(new CustomerProfileQuery("等价", 1, 10));
        PageResult<CustomerProfile> basicPage = queryGateway.page(basicPageSpec());
        assertPageEquals(typedPage, basicPage);

        UpdatePatch<CustomerProfile> typedPatch = patch(typedId);
        Object typedUpdate = adapter.update(typedPatch);
        Object basicUpdate = commandGateway.action(updateSpec(basicId));
        assertRowsResult(typedUpdate, basicUpdate);
        assertProfileDataEquals(jdbc, typedId, basicId);

        List<Map<String, Object>> audits = auditsAfter(jdbc, auditBefore);
        assertEquals(6, audits.size(), "CREATE、PAGE、UPDATE 各应记录两条审计");
        assertAuditPairEquals(audits, 0);
        assertAuditPairEquals(audits, 2);
        assertAuditPairEquals(audits, 4);
    }

    private CommandSpec<WriteCommand<Map<String, Object>>> createSpec(CustomerProfileCreateRequest request) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("displayName", request.getDisplayName());
        values.put("creditLimit", request.getCreditLimit());
        values.put("registeredAt", request.getRegisteredAt());
        values.put("avatarUrl", request.getAvatarUrl());
        return commandSpec(
            CommandOperation.CREATE,
            new WriteCommand<Map<String, Object>>(CommandOperation.CREATE, request.getId(), values)
        );
    }

    private CommandSpec<WriteCommand<Map<String, Object>>> updateSpec(long id) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("displayName", "等价用户（更新）");
        return commandSpec(
            CommandOperation.UPDATE,
            new WriteCommand<Map<String, Object>>(CommandOperation.UPDATE, id, values)
        );
    }

    private CommandSpec<WriteCommand<Map<String, Object>>> commandSpec(
        CommandOperation operation,
        WriteCommand<Map<String, Object>> payload
    ) {
        return CommandSpec.<WriteCommand<Map<String, Object>>>builder()
            .rootType(CustomerProfile.class)
            .entityClasses(Collections.<Class<?>>singletonList(CustomerProfile.class))
            .op(operation)
            .payload(payload)
            .resultType(Map.class)
            .build();
    }

    private QuerySpec<CustomerProfile> basicPageSpec() {
        return QuerySpec.<CustomerProfile>builder()
            .rootType(CustomerProfile.class)
            .entityClasses(Collections.<Class<?>>singletonList(CustomerProfile.class))
            .op(QueryOperation.PAGE)
            .filters(Collections.singletonList(new QueryFilter("displayName", FilterOperator.LIKE, "等价")))
            .page(new PageRequest(1, 10))
            .resultType(CustomerProfile.class)
            .build();
    }

    private UpdatePatch<CustomerProfile> patch(long id) {
        final Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("displayName", "等价用户（更新）");
        return new UpdatePatch<CustomerProfile>() {
            @Override
            public Class<CustomerProfile> getEntityType() { return CustomerProfile.class; }

            @Override
            public CustomerProfile getEntity() { return null; }

            @Override
            public Object getId() { return id; }

            @Override
            public Long getLongId() { return id; }

            @Override
            public Set<String> getPresentFields() { return Collections.singleton("displayName"); }

            @Override
            public Set<String> getPersistableFields() { return Collections.singleton("displayName"); }

            @Override
            public Map<String, Object> getValuesForDelegate() { return values; }

            @Override
            @SuppressWarnings("unchecked")
            public <V> V get(String field) { return (V) values.get(field); }

            @Override
            public <V> V get(String field, Class<V> targetType) { return targetType.cast(values.get(field)); }
        };
    }

    private void assertPageEquals(PageResult<CustomerProfile> typed, PageResult<CustomerProfile> basic) {
        assertEquals(basic.getTotal(), typed.getTotal());
        assertEquals(basic.isTotalKnown(), typed.isTotalKnown());
        assertEquals(basic.getPage(), typed.getPage());
        assertEquals(basic.getLimit(), typed.getLimit());
        assertEquals(basic.getItems().size(), typed.getItems().size());
        for (int i = 0; i < typed.getItems().size(); i++) {
            assertEquals(profileId(basic.getItems().get(i)), profileId(typed.getItems().get(i)));
        }
    }

    private void assertRowsResult(Object typed, Object basic) {
        assertTrue(typed instanceof Map<?, ?>);
        assertTrue(basic instanceof Map<?, ?>);
        assertEquals(((Map<?, ?>) basic).get("rows"), ((Map<?, ?>) typed).get("rows"));
    }

    private void assertProfileDataEquals(JdbcTemplate jdbc, long typedId, long basicId) {
        Map<String, Object> typed = profileRow(jdbc, typedId);
        Map<String, Object> basic = profileRow(jdbc, basicId);
        assertEquals(basic.get("display_name"), typed.get("display_name"));
        assertEquals(basic.get("credit_limit"), typed.get("credit_limit"));
        assertEquals(basic.get("registered_at"), typed.get("registered_at"));
        assertEquals(basic.get("avatar_url"), typed.get("avatar_url"));
    }

    private Map<String, Object> profileRow(JdbcTemplate jdbc, long id) {
        return jdbc.queryForMap(
            "select display_name, credit_limit, registered_at, avatar_url from customer_profile where id = ?",
            id
        );
    }

    private long profileId(CustomerProfile profile) {
        try {
            Field field = CustomerProfile.class.getDeclaredField("id");
            field.setAccessible(true);
            return ((Number) field.get(profile)).longValue();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("无法读取 CustomerProfile.id", ex);
        }
    }

    private long resultId(Object result) {
        assertTrue(result instanceof Map<?, ?>, "CREATE 结果必须是默认引擎 Map");
        Object id = ((Map<?, ?>) result).get("id");
        assertNotNull(id);
        return ((Number) id).longValue();
    }

    private long maxAuditId(JdbcTemplate jdbc) {
        Number value = jdbc.queryForObject(
            "select coalesce(max(id), 0) from entloom_crud_governance_audit",
            Number.class
        );
        return value == null ? 0L : value.longValue();
    }

    private List<Map<String, Object>> auditsAfter(JdbcTemplate jdbc, long auditBefore) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select id, subject_id, tenant_id, resource, action, scene, access_decision, allowed, outcome, "
                + "reason_code, granted_scope_json, governance_scope_json from entloom_crud_governance_audit "
                + "where id > ? order by id",
            auditBefore
        );
        List<Map<String, Object>> normalized = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                copy.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
            normalized.add(copy);
        }
        return normalized;
    }

    private void assertAuditPairEquals(List<Map<String, Object>> audits, int first) {
        Map<String, Object> typed = audits.get(first);
        Map<String, Object> basic = audits.get(first + 1);
        for (String field : Arrays.asList(
            "subject_id", "tenant_id", "resource", "action", "scene", "access_decision", "allowed",
            "outcome", "reason_code", "granted_scope_json", "governance_scope_json"
        )) {
            assertEquals(basic.get(field), typed.get(field), "审计字段不一致: " + field);
        }
    }
}
