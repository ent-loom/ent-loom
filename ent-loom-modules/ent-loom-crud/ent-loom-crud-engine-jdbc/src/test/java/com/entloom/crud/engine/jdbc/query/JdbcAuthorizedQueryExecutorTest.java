package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.access.AuthenticationMode;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.PageCountMode;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.query.spec.ExistsRelationFilter;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.query.ConfigurableQueryDefaultSortResolver;
import java.util.EnumSet;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.governance.admission.AccessAdmissionService;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.admission.AccessTaskSnapshot;
import com.entloom.crud.core.governance.admission.CredentialAuthentication;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGatewayImpl;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.governance.identity.BusinessIdentityRegistry;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.BusinessOperationDeclaration;
import com.entloom.crud.core.governance.policy.CrudOperationMapping;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.EntityPolicyTemplate;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.governance.JdbcCrudGovernanceAuditRecorder;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.test.entity.OrderItemTestEntity;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.enums.QueryStrategy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JdbcAuthorizedQueryExecutorTest {
    private final VisitorContext visitor = VisitorContext.anonymous("trusted-entry", "request-1", null);
    private JdbcAuthorizedQueryExecutor executor;
    private QueryGateway gateway;
    private QueryRouter legacyRouter;
    private CrudGovernanceService legacyGovernance;
    private JdbcTemplate jdbc;
    private final List<CrudGovernanceAuditEvent> audit = new ArrayList<>();
    private AuthorizationScope scope;
    private AuthorizationFields fields;
    private boolean allowed;
    private boolean targetInaccessible;
    private int decisions;
    private final List<String> targets = new ArrayList<String>();
    private final List<String> executedSql = new ArrayList<String>();

    @BeforeEach
    void setUp() {
        EntityMetaRegistry entities = new CrudRuntimeModelBackedEntityMetaRegistry(new CrudNativeRuntimeModelParser()
            .parse(Arrays.<Class<?>>asList(OrderTestEntity.class, OrderItemTestEntity.class)));
        EntityMeta meta = entities.getEntityMeta(OrderTestEntity.class);
        BusinessOperation view = new BusinessOperation(meta.getResourceDescriptor().getResourceCode(), "view");
        BusinessOperationCatalog operations = new BusinessOperationCatalog(
            Collections.singletonList(meta.getResourceDescriptor()),
            Collections.singletonList(new BusinessOperationDeclaration(view, "Project#view")));
        EntityPolicyTemplate template = new EntityPolicyTemplate("public", "Project#public",
            Collections.singletonList(new EntityPolicyTemplate.Rule("view", PolicyIdentityBranch.publicBranch(),
                Collections.emptyList())));
        EntityPolicyRegistry policies = new EntityPolicyRegistry(entities,
            Collections.singletonList(template), Collections.singletonList(new EntityPolicyBinding(
                OrderTestEntity.class, "public", "Order#binding", Collections.emptyMap())),
            operations, new BusinessIdentityRegistry(Collections.emptyList()));
        CrudOperationMappingRegistry mappings = new CrudOperationMappingRegistry(Collections.singletonList(meta), operations,
            Arrays.asList(new CrudOperationMapping(CrudOperationKey.of(QueryOperation.LIST), null, view, "Project#list"),
                new CrudOperationMapping(CrudOperationKey.of(QueryOperation.PAGE), null, view, "Project#page"),
                new CrudOperationMapping(CrudOperationKey.of(QueryOperation.FIND_ONE), null, view, "Project#findOne"),
                new CrudOperationMapping(CrudOperationKey.of(QueryOperation.DETAIL), null, view, "Project#detail")));
        BusinessAuthorizationService authorization = new BusinessAuthorizationService(operations, policies,
            (current, operation, rule, target) -> {
                decisions++;
                assertTrue(current.isAnonymous());
                assertEquals(view, operation);
                targets.add(target);
                return targetInaccessible ? BusinessPermissionDecision.targetInaccessible()
                    : allowed ? BusinessPermissionDecision.allow(scope, fields) : BusinessPermissionDecision.deny();
            });
        SqlSafetyGuard safety = new SqlSafetyGuard(new SqlIdentifierAllowlistValidator(entities), new SqlParameterLimiter());
        SqlSecurityGuard guard = new SqlSecurityGuard() {
            @Override public void beforeCompile(BaseSpec spec) {
                assertNull(spec.getSubject());
                safety.beforeCompile(spec);
            }
            @Override public void beforeExecute(String sql, List<Object> args, CrudExecutionContext context) {
                safety.beforeExecute(sql, args, context);
                executedSql.add(sql);
            }
        };
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:authorized_" + UUID.randomUUID() + ";MODE=MYSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table t_order(id bigint primary key, order_no varchar(64), school_id bigint, tenant_id varchar(64), is_deleted int)");
        jdbc.update("insert into t_order values (1,'one',1,'A',0),(2,'two',1,'B',0),(3,'three',2,'A',0),(4,'four',2,'B',0),(5,'deleted',1,'A',1)");
        jdbc.execute("create table t_order_item(id bigint primary key, order_id bigint, student_id bigint, tenant_id varchar(64), sku_code varchar(64), quantity int, is_deleted int)");
        jdbc.update("insert into t_order_item values (11,1,null,'A','visible-a',2,0),(12,1,null,'A','visible-b',3,0),"
            + "(13,2,null,'B','other-order',9,0),(14,4,null,'B','visible-c',4,0),(15,1,null,'A','deleted',8,1)");
        CrudBusinessAuthorizationAdapter adapter = new CrudBusinessAuthorizationAdapter(entities, mappings, authorization);
        executor = new JdbcAuthorizedQueryExecutor(entities, adapter,
            new JdbcGuardedSqlExecutor(jdbc, guard, new SqlExecutionLogger()), guard, StandardJdbcDialect.GENERIC, audit::add,
            new ConfigurableQueryDefaultSortResolver(true, EnumSet.of(QueryOperation.LIST, QueryOperation.PAGE),
                SortDirection.DESC, true, SortDirection.ASC, true));
        legacyRouter = mock(QueryRouter.class);
        legacyGovernance = mock(CrudGovernanceService.class);
        gateway = new QueryGatewayImpl(legacyRouter,
            new ExecutionPipeline(legacyGovernance, adapter), executor);
        allowed = true;
        fields = fields("id", "orderNo");
        scope = AuthorizationScope.or(Arrays.asList(
            AuthorizationScope.and(Arrays.asList(AuthorizationScope.equal("schoolId", 1L), AuthorizationScope.equal("tenantId", "A"))),
            AuthorizationScope.and(Arrays.asList(AuthorizationScope.equal("schoolId", 2L), AuthorizationScope.equal("tenantId", "B")))));
    }

    record OrderSummary(Long id, String orderNo) { }
    record ItemSummary(String skuCode) { }
    record OrderWithItems(String orderNo, List<ItemSummary> items) { }
    record ItemWithQuantity(String skuCode, Integer quantity) { }
    record OrderWithExtraItemFields(String orderNo, List<ItemWithQuantity> items) { }
    record OrderWithWrongCardinality(String orderNo, ItemSummary items) { }

    @Test
    void 类型化查询自动推导DTO字段并保留分页与单条语义() {
        QuerySpec<OrderSummary> spec = QuerySpec.builder(OrderTestEntity.class, OrderSummary.class)
            .sorts(Collections.singletonList(new QuerySort("id", SortDirection.ASC))).build();
        List<OrderSummary> rows = executor.list(visitor, spec);
        assertEquals(Arrays.asList(1L, 4L), rows.stream().map(OrderSummary::id).toList());
        assertEquals("one", rows.get(0).orderNo());
        PageResult<OrderSummary> page = executor.page(visitor, spec.toBuilder().page(new PageRequest(1, 1)).build());
        assertEquals(2L, page.getTotal());
        assertEquals(1L, page.getItems().get(0).id());
        var target = spec.toBuilder().sorts(Collections.emptyList()).filters(Collections.singletonList(
            new QueryFilter("id", FilterOperator.EQ, 1L))).build();
        assertEquals("one", executor.detail(visitor, target).orderNo());
        assertEquals("one", executor.findOne(visitor, target).orderNo());
        assertNull(executor.findOne(visitor, target.toBuilder().filters(Collections.singletonList(
            new QueryFilter("id", FilterOperator.EQ, 999L))).build()));
        PageResult<OrderSummary> withoutCount = executor.page(visitor,
            spec.toBuilder().page(new PageRequest(1, 1)).countMode(PageCountMode.NONE).build());
        assertNull(withoutCount.getTotal());
        assertTrue(withoutCount.getHasNext());
    }

    @Test
    void 普通实体查询要求完整字段权限且不允许不完整实体投影() {
        var spec = QuerySpec.builder(OrderTestEntity.class).build();
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, spec));
        assertTrue(executedSql.isEmpty());
        fields = fields("id", "orderNo", "schoolId", "tenantId", "isDeleted");
        List<OrderTestEntity> rows = executor.list(visitor, spec);
        assertEquals(2, rows.size());
        assertEquals("A", rows.stream().filter(row -> row.getId().equals(1L)).findFirst().orElseThrow().getTenantId());
        assertNull(rows.get(0).getItems());
        executedSql.clear();
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
            spec.toBuilder().selectFields(Arrays.asList("id", "orderNo")).build()));
        assertTrue(executedSql.isEmpty());
    }

    @Test
    void DTO与关联DTO所需字段必须在任何SQL之前完整授权() {
        fields = fields("id");
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
            QuerySpec.builder(OrderTestEntity.class, OrderSummary.class).build()));
        Map<AuthorizationFields.Use, List<String>> uses = new EnumMap<>(AuthorizationFields.Use.class);
        uses.put(AuthorizationFields.Use.RETURN, Collections.singletonList("orderNo"));
        uses.put(AuthorizationFields.Use.SORT, Collections.singletonList("id"));
        uses.put(AuthorizationFields.Use.EXPAND, Collections.singletonList("items.skuCode"));
        fields = AuthorizationFields.of(uses);
        var spec = QuerySpec.builder(OrderTestEntity.class, OrderWithItems.class)
            .expandRelations(Collections.singletonList("items")).build();
        assertTrue(executedSql.isEmpty());
        List<OrderWithItems> rows = executor.list(visitor, spec);
        assertEquals("visible-c", rows.stream().filter(row -> "four".equals(row.orderNo())).findFirst().orElseThrow().items().get(0).skuCode());
        executedSql.clear();
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
            QuerySpec.builder(OrderTestEntity.class).expandRelations(Collections.singletonList("items")).build()));
        assertTrue(executedSql.isEmpty());
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
            QuerySpec.builder(OrderTestEntity.class, OrderWithExtraItemFields.class)
                .expandRelations(Collections.singletonList("items")).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
            QuerySpec.builder(OrderTestEntity.class, OrderWithWrongCardinality.class)
                .expandRelations(Collections.singletonList("items")).build()));
        assertTrue(executedSql.isEmpty());
    }

    @Test
    void 目标拒绝与未命中一致且不执行SQL() {
        targetInaccessible = true;
        var denied = assertThrows(com.entloom.crud.core.exception.NotFoundException.class, () -> executor.detail(visitor, targetQuery(1L)));
        assertEquals(com.entloom.crud.api.enums.CrudErrorStage.EXECUTE, denied.getStage());
        assertNull(executor.findOne(visitor, targetQuery(1L)));
        assertThrows(PermissionDeniedException.class, () -> executor.page(visitor, query()));
        assertTrue(executedSql.isEmpty());
        assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, audit.get(0).getOutcome());
        assertEquals("DATA_SCOPE_DENIED", audit.get(0).getReasonCode());
        assertEquals(BusinessPermissionDecision.Kind.TARGET_INACCESSIBLE, audit.get(0).getAuthorizationResult().getKind());
        new JdbcCrudGovernanceAuditRecorder(jdbc, null, true).record(audit.get(0));
        String json = jdbc.queryForObject("select authorization_json from entloom_crud_governance_audit", String.class);
        assertTrue(json.contains("\"kind\":\"TARGET_INACCESSIBLE\""));
    }

    @Test
    void 关联按授权根记录批量加载且连接键和未授权字段不泄露() {
        Map<AuthorizationFields.Use, List<String>> uses = new EnumMap<>(AuthorizationFields.Use.class);
        uses.put(AuthorizationFields.Use.RETURN, Collections.singletonList("orderNo"));
        uses.put(AuthorizationFields.Use.SORT, Collections.singletonList("id"));
        uses.put(AuthorizationFields.Use.EXPAND, Collections.singletonList("items.skuCode"));
        fields = AuthorizationFields.of(uses);
        QuerySpec<CrudRecord> request = query().toBuilder().expandRelations(Collections.singletonList("items"))
            .page(new PageRequest(1, 10)).build();
        PageResult<CrudRecord> page = executor.page(visitor, request);
        assertEquals(2, page.getTotal());
        assertEquals(Arrays.asList("visible-a", "visible-b"), page.getItems().get(0).records("items").stream()
            .map(row -> row.string("skuCode")).collect(Collectors.toList()));
        assertEquals("visible-c", page.getItems().get(1).records("items").get(0).string("skuCode"));
        assertEquals(Arrays.asList("orderNo", "items"), new ArrayList<>(page.getItems().get(0).keys()));
        assertEquals(Collections.singleton("skuCode"), page.getItems().get(0).records("items").get(0).keys());
        assertEquals(3, executedSql.size());
        assertTrue(executedSql.stream().noneMatch(sql -> sql.contains("quantity")));
        assertEquals(1, decisions);
        assertEquals(CrudGovernanceAuditOutcome.SUCCESS, audit.get(0).getOutcome());

        executedSql.clear();
        page = executor.page(visitor, request.toBuilder().page(new PageRequest(1, 1)).countMode(PageCountMode.NONE).build());
        assertTrue(page.getHasNext());
        assertEquals(1, page.getItems().size());
        assertEquals(2, page.getItems().get(0).records("items").size());
        assertEquals(2, executedSql.size());

        executedSql.clear();
        scope = AuthorizationScope.none();
        assertTrue(executor.page(visitor, request).getItems().isEmpty());
        assertEquals(1, executedSql.size());
    }

    @Test
    void 关联重复嵌套或未授权投影在任何SQL执行前拒绝() {
        Map<AuthorizationFields.Use, List<String>> uses = new EnumMap<>(AuthorizationFields.Use.class);
        uses.put(AuthorizationFields.Use.RETURN, Arrays.asList("id", "orderNo"));
        uses.put(AuthorizationFields.Use.EXPAND, Collections.singletonList("items.skuCode"));
        fields = AuthorizationFields.of(uses);
        for (List<String> relations : Arrays.asList(Arrays.asList("items", "items"),
            Collections.singletonList("items.student"), Collections.singletonList("missing"))) {
            assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
                query().toBuilder().expandRelations(relations).build()));
        }
        fields = fields("id", "orderNo");
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
            query().toBuilder().expandRelations(Collections.singletonList("items")).build()));
        assertTrue(executedSql.isEmpty());
    }

    @Test
    void 关联超限明确失败且不记录成功或返回截断明细() {
        Map<AuthorizationFields.Use, List<String>> uses = new EnumMap<>(AuthorizationFields.Use.class);
        uses.put(AuthorizationFields.Use.RETURN, Collections.singletonList("id"));
        uses.put(AuthorizationFields.Use.EXPAND, Collections.singletonList("items.skuCode"));
        fields = AuthorizationFields.of(uses);
        jdbc.update("insert into t_order_item(id,order_id,sku_code,is_deleted) "
            + "select 100+x,1,'overflow',0 from system_range(1,1001)");
        assertThrows(ValidationException.class, () -> executor.detail(visitor,
            targetQuery(1L).toBuilder().expandRelations(Collections.singletonList("items")).build()));
        assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, audit.get(0).getOutcome());
        assertFalse(audit.stream().anyMatch(CrudGovernanceAuditEvent::isSuccess));
    }

    @Test
    void 静态SQL范围贯穿列表分页计数与单条读取且保留字段投影和逻辑删除() {
        scope = AuthorizationScope.sqlWhere("school_id = 1 AND tenant_id = 'A' OR school_id = 2 AND tenant_id IN ('B')");
        assertEquals(Arrays.asList(1L, 4L), ids(executor.list(visitor, query())));
        PageResult<CrudRecord> page = executor.page(visitor,
            query().toBuilder().page(new PageRequest(1, 10)).build());
        assertEquals(2L, page.getTotal());
        assertEquals(Arrays.asList(1L, 4L), ids(page.getItems()));
        assertEquals(Long.valueOf(1L), executor.detail(visitor, targetQuery(1L)).longValue("id"));
        assertEquals(Long.valueOf(4L), executor.findOne(visitor, targetQuery(4L)).longValue("id"));
        assertNull(executor.findOne(visitor, targetQuery(2L)));
        assertNull(executor.findOne(visitor, targetQuery(5L)));
        assertThrows(com.entloom.crud.core.exception.NotFoundException.class,
            () -> executor.detail(visitor, targetQuery(2L)));
        for (CrudRecord row : page.getItems()) assertEquals(new java.util.HashSet<>(Arrays.asList("id", "orderNo")), row.keys());
    }

    @Test
    void paired_scope_and_logic_delete_should_apply_to_real_rows_and_page_count() {
        List<CrudRecord> rows = executor.list(visitor, query());
        assertEquals(Arrays.asList(1L, 4L), ids(rows));
        assertEquals(Arrays.asList("id", "orderNo"), new ArrayList<String>(rows.get(0).keys()));
        assertFalse(rows.get(0).containsKey("tenantId"));
        assertFalse(executedSql.get(0).contains("select *"));

        PageResult<CrudRecord> first = executor.page(visitor, query().toBuilder().page(new PageRequest(1, 1)).build());
        PageResult<CrudRecord> second = executor.page(visitor, query().toBuilder().page(new PageRequest(2, 1)).build());
        assertEquals(2L, first.getTotal());
        assertEquals(first.getTotal(), second.getTotal());
        assertEquals(Collections.singletonList(1L), ids(first.getItems()));
        assertEquals(Collections.singletonList(4L), ids(second.getItems()));
        assertEquals(3, decisions);
    }

    @Test
    void projection_filter_and_sort_should_have_independent_permissions() {
        fields = fields("orderNo");
        List<CrudRecord> rows = executor.list(visitor, query().toBuilder()
            .filters(Collections.singletonList(new QueryFilter("orderNo", FilterOperator.EQ, "four")))
            .sorts(Collections.singletonList(new QuerySort("orderNo", SortDirection.DESC))).build());
        assertEquals(Collections.singletonList("four"), rows.stream().map(row -> row.string("orderNo")).collect(Collectors.toList()));
        assertEquals(Collections.singleton("orderNo"), rows.get(0).keys());
        // 主键只作为框架内部稳定排序依据；不自动授权返回、显式过滤或排序。
        assertEquals(Collections.singleton("orderNo"), executor.list(visitor, query()).get(0).keys());
        int executions = executedSql.size();
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .selectFields(Collections.singletonList("id")).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .filters(Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, 1L))).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .sorts(Collections.singletonList(new QuerySort("id", SortDirection.ASC))).build()));
        fields = AuthorizationFields.none();
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query()));
        assertEquals(executions, executedSql.size());
    }

    @Test
    void stable_page_sort_requires_primary_key_sort_permission() {
        // 缺少主键 SORT 时不能通过框架追加排序间接使用该字段。
        fields = AuthorizationFields.of(Collections.singletonMap(
            AuthorizationFields.Use.RETURN, Collections.singletonList("orderNo")));
        QuerySpec<CrudRecord> firstPage = query().toBuilder().page(new PageRequest(1, 1)).build();
        QuerySpec<CrudRecord> secondPage = query().toBuilder().page(new PageRequest(2, 1)).build();
        int executions = executedSql.size();
        assertThrows(PermissionDeniedException.class, () -> executor.page(visitor, firstPage));
        assertThrows(PermissionDeniedException.class, () -> executor.page(visitor, firstPage.toBuilder()
            .sorts(Collections.singletonList(new QuerySort("id", SortDirection.ASC))).build()));
        assertEquals(executions, executedSql.size());

        Map<AuthorizationFields.Use, List<String>> granted = new EnumMap<>(AuthorizationFields.Use.class);
        granted.put(AuthorizationFields.Use.RETURN, Collections.singletonList("orderNo"));
        granted.put(AuthorizationFields.Use.SORT, Collections.singletonList("id"));
        fields = AuthorizationFields.of(granted);
        assertEquals("one", executor.page(visitor, firstPage).getItems().get(0).string("orderNo"));
        assertEquals("four", executor.page(visitor, secondPage).getItems().get(0).string("orderNo"));
        assertEquals(Collections.singleton("orderNo"), executor.page(visitor, firstPage).getItems().get(0).keys());

        // 授权业务字段排序后，相同值由框架追加主键排序，跨页顺序确定。
        fields = fields("id", "orderNo");
        jdbc.update("update t_order set order_no = 'same' where id in (1, 4)");
        List<QuerySort> sorts = Collections.singletonList(new QuerySort("orderNo", SortDirection.DESC));
        assertEquals(Collections.singletonList(1L), ids(executor.page(visitor,
            firstPage.toBuilder().sorts(sorts).build()).getItems()));
        assertEquals(Collections.singletonList(4L), ids(executor.page(visitor,
            secondPage.toBuilder().sorts(sorts).build()).getItems()));
    }

    @Test
    void 缺省筛选始终与身份范围取交集且显式空列表关闭筛选() {
        fields = AuthorizationFields.of(Collections.singletonMap(
            AuthorizationFields.Use.RETURN, Collections.singletonList("orderNo")));
        QuerySpec<CrudRecord> request = query().toBuilder().filters(Collections.singletonList(
            new QueryFilter("orderNo", FilterOperator.EQ, "four"))).build();
        assertEquals("four", executor.list(visitor, request).get(0).string("orderNo"));
        assertTrue(executor.list(visitor, request.toBuilder().filters(Collections.singletonList(
            new QueryFilter("orderNo", FilterOperator.EQ, "two"))).build()).isEmpty());
        int executions = executedSql.size();
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, request.toBuilder()
            .filters(Collections.singletonList(new QueryFilter("tenantId", FilterOperator.EQ, "B"))).build()));
        Map<AuthorizationFields.Use, List<String>> disabled = new EnumMap<>(AuthorizationFields.Use.class);
        disabled.put(AuthorizationFields.Use.RETURN, Collections.singletonList("orderNo"));
        disabled.put(AuthorizationFields.Use.FILTER, Collections.emptyList());
        fields = AuthorizationFields.of(disabled);
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, request));
        assertEquals(executions, executedSql.size());
    }

    @Test
    void every_execution_should_reauthorize_and_ignore_forged_legacy_subject() {
        QuerySpec<CrudRecord> request = query();
        assertEquals(Arrays.asList(1L, 4L), ids(executor.list(visitor, request)));
        allowed = false;
        int executions = executedSql.size();
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, request));
        assertEquals(executions, executedSql.size());
        allowed = true;
        scope = AuthorizationScope.none();
        assertTrue(executor.list(visitor, request).isEmpty());
        assertEquals(0L, executor.page(visitor, request).getTotal());
        scope = AuthorizationScope.all();
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L), ids(executor.list(visitor, request)));
        assertEquals(5, decisions);
    }

    @Test
    void count_free_page_and_caller_or_legacy_conditions_should_only_narrow_scope() {
        PageResult<CrudRecord> page = executor.page(visitor, query().toBuilder()
            .page(new PageRequest(1, 1)).countMode(PageCountMode.NONE).build());
        assertNull(page.getTotal());
        assertTrue(page.getHasNext());
        assertEquals(Collections.singletonList(1L), ids(page.getItems()));
        assertEquals(1, executedSql.size());
        assertEquals(Collections.singletonList(4L), ids(executor.list(visitor, query().toBuilder()
            .governanceScope(CrudDataScope.scoped(Collections.singletonMap("tenantId", "B"))).build())));
        assertTrue(executor.list(visitor, query().toBuilder()
            .filters(Collections.singletonList(new QueryFilter("orderNo", FilterOperator.EQ, "two"))).build()).isEmpty());
    }

    @Test
    void unsupported_shape_and_mismatched_operation_should_fail_before_sql() {
        List<QuerySpec<?>> unsupported = Arrays.<QuerySpec<?>>asList(
            query().toBuilder().scene("custom").build(),
            query().toBuilder().entityClasses(Arrays.<Class<?>>asList(OrderTestEntity.class, OrderItemTestEntity.class)).build(),
            query().toBuilder().expandRelations(Collections.singletonList("items")).build(),
            query().toBuilder().existsRelationFilter(new ExistsRelationFilter("items", Collections.singletonList(
                new QueryFilter("id", FilterOperator.EQ, 1L)))).build(),
            query().toBuilder().strategy(QueryStrategy.EXISTS).build(),
            query().toBuilder().time(new QueryTimeRange()).build(),
            query().toBuilder().grantedScope(CrudDataScope.allowAll()).build(),
            QuerySpec.<OrderTestEntity>builder().rootType(OrderTestEntity.class).resultType(OrderTestEntity.class).build());
        for (QuerySpec<?> spec : unsupported) {
            assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, spec));
        }
        assertThrows(ValidationException.class, () -> executor.list(visitor, query().toBuilder().op(QueryOperation.DETAIL).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .rootType(OrderItemTestEntity.class).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .filters(Collections.singletonList(new QueryFilter("items.id", FilterOperator.EQ, 1L))).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .selectFields(Collections.singletonList("items")).build()));
        assertThrows(ValidationException.class, () -> executor.list(visitor, query().toBuilder()
            .sorts(Collections.singletonList(new QuerySort("orderNo", SortDirection.ASC, SortTarget.METRIC))).build()));
        assertTrue(executedSql.isEmpty());
    }

    @Test
    void trusted_queries_should_normalize_without_legacy_subject_and_reject_invalid_requests() {
        assertEquals(Arrays.asList(1L, 4L), ids(executor.list(visitor, query().toBuilder().subject(null).build())));
        int executions = executedSql.size();
        assertThrows(ValidationException.class, () -> executor.list(visitor, query().toBuilder().limit(1001).build()));
        assertThrows(ValidationException.class, () -> executor.page(visitor, query().toBuilder()
            .page(new PageRequest(Integer.MAX_VALUE, 1000)).build()));
        assertThrows(ValidationException.class, () -> executor.list(visitor, query().toBuilder()
            .filters(Collections.singletonList(new QueryFilter("orderNo", null, "one"))).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .accessDecision(AccessDecision.DENY).build()));
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor, query().toBuilder()
            .selectFields(Collections.singletonList("orderNo as secret")).build()));
        assertEquals(executions, executedSql.size());
    }

    @Test
    void gateway_should_execute_authorized_rows_and_audit_after_projection_without_legacy_routing() {
        AccessAdmission admission = admission();
        AccessContextScope.withAdmission(admission, () -> {
            assertEquals(Arrays.asList(1L, 4L), ids(gateway.list(query())));
            assertEquals(2L, gateway.page(query().toBuilder().page(new PageRequest(1, 10)).build()).getTotal());
        });
        assertEquals(2, decisions);
        assertEquals(2, audit.size());
        for (CrudGovernanceAuditEvent event : audit) {
            assertEquals(CrudGovernanceAuditOutcome.SUCCESS, event.getOutcome());
            assertSame(admission.getVisitorContext(), event.getVisitor());
            assertEquals("actor-1", event.getSubject().getSubjectId());
            assertEquals("request-1", event.getRequestId());
            assertEquals("view", event.getAction().getOperation());
            assertTrue(event.getAuthorizationResult().getFields().allows(AuthorizationFields.Use.RETURN, "orderNo"));
            assertEquals(AuthorizationScope.Kind.OR, event.getAuthorizationResult().getScope().getKind());
            assertNull(event.getGovernanceScope());
        }
        verifyNoInteractions(legacyRouter, legacyGovernance);
    }

    @Test
    void target_queries_should_bind_primary_key_to_authorization_and_preserve_cardinality() {
        AccessAdmission admission = admission();
        AccessContextScope.withAdmission(admission, () -> {
            CrudRecord found = gateway.findOne(targetQuery(1L));
            assertEquals(Long.valueOf(1L), found.longValue("id"));
            assertEquals(Long.valueOf(1L), gateway.detail(targetQuery(1L)).longValue("id"));
            assertNull(gateway.findOne(targetQuery(99L)));
            assertThrows(com.entloom.crud.core.exception.NotFoundException.class,
                () -> gateway.detail(targetQuery(99L)));
        });
        assertEquals(4, decisions);
        assertEquals(4, audit.size());
        assertEquals("1", audit.get(0).getAuthorizationResult().getRequest().getTargetId());
        assertEquals("1", audit.get(1).getAuthorizationResult().getRequest().getTargetId());
        assertEquals("99", audit.get(2).getAuthorizationResult().getRequest().getTargetId());
        assertEquals("99", audit.get(3).getAuthorizationResult().getRequest().getTargetId());
        assertEquals(Arrays.asList("1", "1", "99", "99"), targets);
    }

    @Test
    void target_queries_should_reject_missing_or_ambiguous_primary_key_before_authorization() {
        AccessAdmission admission = admission();
        AccessContextScope.withAdmission(admission, () -> {
            assertThrows(PermissionDeniedException.class, () -> gateway.findOne(query()));
            assertThrows(PermissionDeniedException.class, () -> gateway.detail(query().toBuilder()
                .filters(Arrays.asList(new QueryFilter("id", FilterOperator.EQ, 1L),
                    new QueryFilter("id", FilterOperator.EQ, 2L))).build()));
            assertThrows(PermissionDeniedException.class, () -> gateway.detail(query().toBuilder()
                .filters(Collections.singletonList(new QueryFilter("id", FilterOperator.IN,
                    Collections.singletonList(1L)))).build()));
            assertThrows(PermissionDeniedException.class, () -> gateway.detail(query().toBuilder()
                .filters(Arrays.asList(new QueryFilter("id", FilterOperator.EQ, 1L),
                    new QueryFilter("orderNo", FilterOperator.EQ, "one"))).build()));
        });
        assertEquals(0, decisions);
        assertEquals(4, audit.size());
        assertTrue(audit.stream().allMatch(event -> event.getOutcome() == CrudGovernanceAuditOutcome.GOVERNANCE_DENIED));
    }

    @Test
    void business_authorization_audit_should_persist_new_authorization_details() {
        executor.list(visitor, query());
        JdbcCrudGovernanceAuditRecorder recorder = new JdbcCrudGovernanceAuditRecorder(jdbc, null, true);
        recorder.record(audit.get(0));

        String json = jdbc.queryForObject(
            "select authorization_json from entloom_crud_governance_audit",
            String.class
        );
        assertTrue(json.contains("\"accessEntry\":\"trusted-entry\""));
        assertTrue(json.contains("\"invocationId\""));
        assertTrue(json.contains("\"scope\""));
        assertTrue(json.contains("\"fields\""));
    }

    @Test
    void async_gateway_should_deny_without_scope_and_reauthorize_after_snapshot_restore() throws Exception {
        AccessAdmissionService admissionService = new AccessAdmissionService(new BusinessIdentityRegistry(Collections.emptyList()));
        AccessAdmission admission = admission();
        AccessTaskSnapshot snapshot = AccessTaskSnapshot.capture(admission);
        var worker = Executors.newSingleThreadExecutor();
        try {
            AccessContextScope.withAdmission(admission, () -> {
                try {
                    worker.submit(() -> assertThrows(PermissionDeniedException.class, () -> gateway.list(query()))).get();
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            assertTrue(executedSql.isEmpty());
            assertEquals(0, decisions);
            assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, audit.get(0).getOutcome());
            assertEquals(Arrays.asList(1L, 4L), worker.submit(() -> {
                AccessAdmission restored = snapshot.restore(admissionService, () -> CredentialAuthentication.authenticated(
                    new AccessSubject("actor-1", AccessSubjectType.HUMAN)));
                return AccessContextScope.withAdmission(restored, () -> ids(gateway.list(query())));
            }).get());
            allowed = false;
            worker.submit(() -> {
                AccessAdmission restored = snapshot.restore(admissionService, () -> CredentialAuthentication.authenticated(
                    new AccessSubject("actor-1", AccessSubjectType.HUMAN)));
                AccessContextScope.withAdmission(restored, () ->
                    assertThrows(PermissionDeniedException.class, () -> gateway.list(query())));
            }).get();
            assertEquals(2, decisions);
            assertEquals(1, executedSql.size());
            assertFalse(worker.submit(() -> AccessContextScope.currentVisitor().isPresent()).get());
        } finally { worker.shutdownNow(); }
        verifyNoInteractions(legacyRouter, legacyGovernance);
    }

    @Test
    void rejected_fields_and_sql_failure_should_have_distinct_audit_outcomes() {
        fields = fields("orderNo");
        assertThrows(PermissionDeniedException.class, () -> executor.list(visitor,
            query().toBuilder().selectFields(Collections.singletonList("id")).build()));
        assertTrue(executedSql.isEmpty());
        assertEquals(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, audit.get(0).getOutcome());
        assertEquals(AccessDecision.DENY, audit.get(0).getAccessDecision());
        jdbc.execute("drop table t_order");
        assertThrows(RuntimeException.class, () -> executor.list(visitor, query()));
        assertEquals(CrudGovernanceAuditOutcome.EXECUTION_FAILED, audit.get(1).getOutcome());
        assertFalse(audit.stream().anyMatch(CrudGovernanceAuditEvent::isSuccess));
    }

    private AccessAdmission admission() {
        return new AccessAdmissionService(new BusinessIdentityRegistry(Collections.emptyList()))
            .admit(AuthenticationMode.ANONYMOUS, () -> CredentialAuthentication.authenticated(
                new AccessSubject("actor-1", AccessSubjectType.HUMAN)), "trusted-entry", "request-1", null);
    }

    private QuerySpec<CrudRecord> query() {
        SubjectContext forged = new SubjectContext();
        forged.setSubjectId("forged-subject");
        forged.setTenantId("B");
        return QuerySpec.<CrudRecord>builder().rootType(OrderTestEntity.class).resultType(CrudRecord.class)
            .subject(forged).build();
    }

    private QuerySpec<CrudRecord> targetQuery(Long id) {
        return query().toBuilder().filters(Collections.singletonList(
            new QueryFilter("id", FilterOperator.EQ, id))).build();
    }

    private AuthorizationFields fields(String... returned) {
        Map<AuthorizationFields.Use, List<String>> uses = new EnumMap<AuthorizationFields.Use, List<String>>(AuthorizationFields.Use.class);
        uses.put(AuthorizationFields.Use.RETURN, Arrays.asList(returned));
        uses.put(AuthorizationFields.Use.FILTER, Collections.singletonList("orderNo"));
        uses.put(AuthorizationFields.Use.SORT, Arrays.asList(returned).contains("id")
            ? Arrays.asList("id", "orderNo") : Collections.singletonList("orderNo"));
        return AuthorizationFields.of(uses);
    }

    private List<Long> ids(List<CrudRecord> rows) {
        return rows.stream().map(row -> row.longValue("id")).collect(Collectors.toList());
    }
}
