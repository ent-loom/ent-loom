package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.sql.JdbcAuthorizationScopeCompiler;
import com.entloom.crud.engine.jdbc.test.entity.OrderItemTestEntity;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.enums.QueryStrategy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAuthorizationScopeCompilerTest {
    private EntityMeta orderMeta;
    private JdbcQueryCompiler compiler;

    @BeforeEach
    void setUp() {
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Arrays.<Class<?>>asList(OrderTestEntity.class, OrderItemTestEntity.class)));
        orderMeta = registry.getEntityMeta(OrderTestEntity.class);
        compiler = new JdbcQueryCompiler();
    }

    @Test
    void paired_or_scope_should_filter_rows_and_count_without_cross_combinations() throws Exception {
        AuthorizationScope pairs = AuthorizationScope.or(Arrays.asList(
            AuthorizationScope.and(Arrays.asList(
                AuthorizationScope.equal("schoolId", 1L), AuthorizationScope.equal("tenantId", "A"))),
            AuthorizationScope.and(Arrays.asList(
                AuthorizationScope.equal("schoolId", 2L), AuthorizationScope.equal("tenantId", "B")))));
        CompiledQuery query = compiler.compileAuthorized(plan(), pairs);

        assertTrue(query.getDataSql().contains(
            "((t.school_id = ? and t.tenant_id = ?) or (t.school_id = ? and t.tenant_id = ?))"));
        assertEquals(Arrays.<Object>asList(0, 1L, "A", 2L, "B"), query.getCountArgs());
        assertEquals(Arrays.<Object>asList(0, 1L, "A", 2L, "B", 10), query.getDataArgs());
        try (Connection connection = database()) {
            assertEquals(Arrays.asList(1L, 4L), ids(connection, query));
            assertEquals(2L, count(connection, query));
        }
    }

    @Test
    void all_none_and_in_should_keep_explicit_semantics() throws Exception {
        try (Connection connection = database()) {
            assertEquals(4L, count(connection, compiler.compileAuthorized(plan(), AuthorizationScope.all())));
            assertEquals(0L, count(connection, compiler.compileAuthorized(plan(), AuthorizationScope.none())));
            AuthorizationScope narrowed = AuthorizationScope.and(Arrays.asList(
                AuthorizationScope.in("schoolId", Arrays.asList(1L, 2L)),
                AuthorizationScope.equal("tenantId", "A")));
            CompiledQuery query = compiler.compileAuthorized(plan(), narrowed);
            assertEquals(Arrays.asList(1L, 3L), ids(connection, query));
            assertEquals(2L, count(connection, query));
        }
    }

    @Test
    void unknown_relation_and_excessive_scope_should_fail_closed() {
        assertThrows(ValidationException.class, () -> compiler.compileAuthorized(plan(), null));
        assertThrows(DataScopeDeniedException.class, () -> compiler.compileAuthorized(
            plan(), AuthorizationScope.equal("missing", 1)));
        assertThrows(DataScopeDeniedException.class, () -> compiler.compileAuthorized(
            plan(), AuthorizationScope.equal("items", 1)));
        assertThrows(DataScopeDeniedException.class, () -> new JdbcAuthorizationScopeCompiler().compile(
            AuthorizationScope.all(), orderMeta, "t;drop", StandardJdbcDialect.GENERIC));

        AuthorizationScope deep = AuthorizationScope.equal("id", 1L);
        for (int index = 0; index < 65; index++) {
            deep = AuthorizationScope.and(Collections.singletonList(deep));
        }
        AuthorizationScope tooDeep = deep;
        assertThrows(DataScopeDeniedException.class, () -> compiler.compileAuthorized(plan(), tooDeep));
    }

    private QueryPlan plan() {
        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .rootType(OrderTestEntity.class).resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST).limit(10).selectFields(Collections.singletonList("id")).build();
        return new QueryPlan(spec, orderMeta, RelationGraph.empty(), QueryStrategy.ROOT_FIRST,
            QueryOperation.LIST, CrudDataScope.allowAll(), spec.getFilters());
    }

    private Connection database() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:authorization_scope_" + UUID.randomUUID());
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table t_order (id bigint, school_id bigint, tenant_id varchar(20), is_deleted integer)");
            statement.execute("insert into t_order values (1,1,'A',0),(2,1,'B',0),(3,2,'A',0),(4,2,'B',0),(5,1,'A',1)");
        }
        return connection;
    }

    private List<Long> ids(Connection connection, CompiledQuery query) throws SQLException {
        List<Long> result = new ArrayList<Long>();
        try (PreparedStatement statement = prepare(connection, query.getDataSql(), query.getDataArgs());
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) result.add(rows.getLong(1));
        }
        return result;
    }

    private long count(Connection connection, CompiledQuery query) throws SQLException {
        try (PreparedStatement statement = prepare(connection, query.getCountSql(), query.getCountArgs());
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private PreparedStatement prepare(Connection connection, String sql, List<Object> args) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int index = 0; index < args.size(); index++) statement.setObject(index + 1, args.get(index));
        return statement;
    }
}
