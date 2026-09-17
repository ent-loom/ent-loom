package com.entloom.crud.engine.jdbc;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.query.JdbcQueryCompiler;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcReservedIdentifierIntegrationTest {
    private JdbcTemplate jdbcTemplate;
    private EntityMetaRegistry metaRegistry;
    private JdbcEntityDaoFactory daoFactory;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:ent_loom_reserved_identifier;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa",
            ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists \"order\"");
        jdbcTemplate.execute("create table \"order\" (\"id\" bigint primary key, \"select\" varchar(64), \"group\" varchar(64))");

        metaRegistry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(ReservedIdentifierEntity.class))
        );
        metaRegistry.validateOrThrow();
        SqlSafetyGuard sqlSecurityGuard = new SqlSafetyGuard(
            new SqlIdentifierAllowlistValidator(metaRegistry),
            new SqlParameterLimiter()
        );
        JdbcGuardedSqlExecutor guardedExecutor = new JdbcGuardedSqlExecutor(
            jdbcTemplate,
            sqlSecurityGuard,
            new SqlExecutionLogger()
        );
        daoFactory = new JdbcEntityDaoFactory(metaRegistry, guardedExecutor, StandardJdbcDialect.H2);
    }

    @Test
    void should_execute_dao_crud_against_reserved_table_and_columns() {
        EntityDao<ReservedIdentifierEntity, Long> dao = daoFactory.scoped(
            EntityType.of(ReservedIdentifierEntity.class, Long.class),
            EntityAccessScope.unrestricted()
        );
        ReservedIdentifierEntity entity = new ReservedIdentifierEntity();
        entity.id = 1L;
        entity.select = "first";
        entity.group = "A";

        assertEquals(Long.valueOf(1L), dao.insert(entity));
        Optional<ReservedIdentifierEntity> loaded = dao.findById(1L);
        assertTrue(loaded.isPresent());
        assertEquals("first", loaded.get().select);
        assertEquals("A", loaded.get().group);

        entity.select = "updated";
        Map<String, Object> updateValues = new LinkedHashMap<String, Object>();
        updateValues.put("id", 1L);
        updateValues.put("select", "updated");
        UpdatePatch<ReservedIdentifierEntity> patch = new DefaultCommandPayloadBinder().bindUpdatePatch(
            updateValues,
            ReservedIdentifierEntity.class,
            metaRegistry.getEntityMeta(ReservedIdentifierEntity.class)
        );
        assertEquals(1, dao.updateById(1L, patch));
        assertEquals("updated", dao.findById(1L).get().select);
    }

    @Test
    void should_quote_reserved_identifiers_in_query_compilation() {
        EntityMeta meta = metaRegistry.getEntityMeta(ReservedIdentifierEntity.class);
        QueryPlan plan = new QueryPlan(
            com.entloom.crud.core.capability.query.spec.QuerySpec.<ReservedIdentifierEntity>builder()
                .rootType(ReservedIdentifierEntity.class)
                .resultType(ReservedIdentifierEntity.class)
                .op(QueryOperation.LIST)
                .limit(10)
                .filters(Collections.singletonList(new QueryFilter("select", FilterOperator.EQ, "first")))
                .build(),
            meta,
            RelationGraph.empty(),
            com.entloom.crud.enums.QueryStrategy.ROOT_FIRST,
            QueryOperation.LIST,
            CrudDataScope.allowAll(),
            Collections.singletonList(new QueryFilter("select", FilterOperator.EQ, "first"))
        );

        CompiledQuery compiled = new JdbcQueryCompiler(StandardJdbcDialect.H2).compile(plan);

        assertEquals(
            "select * from \"order\" t where t.\"select\" = ? order by t.\"id\" asc limit ?",
            compiled.getDataSql()
        );
    }

    @EntCrudEntity(table = "order", idField = "id")
    public static class ReservedIdentifierEntity {
        /** 主键。 */
        private Long id;
        /** 映射到数据库保留字 select 的文本字段。 */
        private String select;
        /** 映射到数据库保留字 group 的文本字段。 */
        private String group;
    }
}
