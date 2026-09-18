package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 范围字段数据库结构启动探针测试。
 */
class JdbcInsertScopeDatabaseValidatorTest {
    @Test
    void generated_columns_are_rejected_for_scope_fields() {
        DataSource dataSource = new FakeMysqlDataSource(
            Collections.singletonList(column("tenant_id", "STORED GENERATED", "YES")),
            false
        );

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> new JdbcInsertScopeDatabaseValidator(dataSource, registry()).validateOrThrow()
        );

        assertTrue(exception.getMessage().contains("生成列"), exception.getMessage());
    }

    @Test
    void auto_increment_and_on_update_are_rejected_for_scope_fields() {
        for (Map<String, String> state : Arrays.asList(
            column("tenant_id", "auto_increment", ""),
            column("tenant_id", "on update CURRENT_TIMESTAMP", "")
        )) {
            DataSource dataSource = new FakeMysqlDataSource(
                Collections.singletonList(state),
                false
            );

            assertThrows(
                ValidationException.class,
                () -> new JdbcInsertScopeDatabaseValidator(dataSource, registry()).validateOrThrow()
            );
        }
    }

    @Test
    void any_trigger_on_scope_table_is_rejected_fail_closed() {
        DataSource dataSource = new FakeMysqlDataSource(
            Collections.singletonList(column("tenant_id", "", "")),
            true
        );

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> new JdbcInsertScopeDatabaseValidator(dataSource, registry()).validateOrThrow()
        );

        assertTrue(exception.getMessage().contains("触发器"), exception.getMessage());
    }

    @Test
    void hidden_trigger_metadata_is_rejected_fail_closed() {
        DataSource dataSource = new FakeMysqlDataSource(
            Collections.singletonList(column("tenant_id", "", "")),
            false,
            false
        );

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> new JdbcInsertScopeDatabaseValidator(dataSource, registry()).validateOrThrow()
        );

        assertTrue(exception.getMessage().contains("无法可靠读取"), exception.getMessage());
    }

    @Test
    void custom_registry_without_complete_snapshot_is_rejected() {
        EntityMetaRegistry delegate = registry();
        EntityMetaRegistry incomplete = new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return delegate.getEntityMeta(entityType);
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return delegate.getResourceDescriptor(entityType);
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                return delegate.getRelationGraph(rootType);
            }

            @Override
            public void validateOrThrow() {
                delegate.validateOrThrow();
            }
        };

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> new JdbcInsertScopeDatabaseValidator(
                new FakeMysqlDataSource(
                    Collections.singletonList(column("tenant_id", "", "")),
                    false
                ),
                incomplete
            ).validateOrThrow()
        );

        assertTrue(exception.getMessage().contains("必须实现 getEntityMetas"), exception.getMessage());
    }

    @Test
    void non_mysql_database_is_left_to_its_behavior_tests() {
        DataSource dataSource = new FakeDatabaseDataSource("H2");

        assertDoesNotThrow(
            () -> new JdbcInsertScopeDatabaseValidator(dataSource, registry()).validateOrThrow()
        );
    }

    @Test
    void generated_marker_detection_is_case_insensitive() {
        assertTrue(JdbcInsertScopeDatabaseValidator.isGenerated("VIRTUAL GENERATED", ""));
        assertTrue(JdbcInsertScopeDatabaseValidator.isGenerated("", "tenant_id + 1"));
    }

    @Test
    void default_generated_extra_is_not_treated_as_generated_column() {
        assertFalse(JdbcInsertScopeDatabaseValidator.isGenerated("DEFAULT_GENERATED", ""));
        assertFalse(JdbcInsertScopeDatabaseValidator.isGenerated(
            "DEFAULT_GENERATED on update CURRENT_TIMESTAMP", ""
        ));
    }

    @Test
    void case_insensitive_collation_is_rejected_for_string_scope_fields() {
        DataSource dataSource = new FakeMysqlDataSource(
            Collections.singletonList(column(
                "tenant_id", "varchar", "utf8mb4_0900_ai_ci", "DEFAULT-TENANT", "", ""
            )),
            false
        );

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> new JdbcInsertScopeDatabaseValidator(dataSource, registry()).validateOrThrow()
        );

        assertTrue(exception.getMessage().contains("二进制排序规则"), exception.getMessage());
    }

    @Test
    void binary_collation_and_database_default_are_allowed_for_string_scope_fields() {
        DataSource dataSource = new FakeMysqlDataSource(
            Collections.singletonList(column(
                "tenant_id", "varchar", "utf8mb4_bin", "DEFAULT-TENANT", "", ""
            )),
            false
        );

        assertDoesNotThrow(
            () -> new JdbcInsertScopeDatabaseValidator(dataSource, registry()).validateOrThrow()
        );
    }

    private EntityMetaRegistry registry() {
        EntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(
                Collections.<Class<?>>singletonList(ScopeEntity.class)
            )
        );
        registry.validateOrThrow();
        return registry;
    }

    private static Map<String, String> column(String name, String extra, String generationExpression) {
        return column(name, "varchar", "utf8mb4_bin", null, extra, generationExpression);
    }

    private static Map<String, String> column(
        String name,
        String dataType,
        String collationName,
        String columnDefault,
        String extra,
        String generationExpression
    ) {
        Map<String, String> result = new HashMap<String, String>();
        result.put("column_name", name);
        result.put("data_type", dataType);
        result.put("collation_name", collationName);
        result.put("column_default", columnDefault);
        result.put("extra", extra);
        result.put("generation_expression", generationExpression);
        return result;
    }

    @EntCrudEntity(table = "scope_entity", scopeFields = {"tenantId"})
    public static class ScopeEntity {
        /** 数据库主键。 */
        private Long id;
        /** 租户范围字段。 */
        private String tenantId;
    }

    private static final class FakeMysqlDataSource extends FakeDatabaseDataSource {
        private final List<Map<String, String>> columns;
        private final boolean trigger;
        private final boolean triggerMetadataVisible;

        private FakeMysqlDataSource(List<Map<String, String>> columns, boolean trigger) {
            this(columns, trigger, true);
        }

        private FakeMysqlDataSource(
            List<Map<String, String>> columns,
            boolean trigger,
            boolean triggerMetadataVisible
        ) {
            super("MySQL");
            this.columns = columns;
            this.trigger = trigger;
            this.triggerMetadataVisible = triggerMetadataVisible;
        }

        @Override
        protected Connection connection() {
            return JdbcInsertScopeDatabaseValidatorTest.connection(
                "MySQL", columns, trigger, triggerMetadataVisible
            );
        }
    }

    private static class FakeDatabaseDataSource implements DataSource {
        private final String productName;

        private FakeDatabaseDataSource(String productName) {
            this.productName = productName;
        }

        @Override
        public Connection getConnection() {
            return connection();
        }

        protected Connection connection() {
            return JdbcInsertScopeDatabaseValidatorTest.connection(
                productName,
                Collections.<Map<String, String>>emptyList(),
                false,
                true
            );
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }

    private static Connection connection(
        String productName,
        List<Map<String, String>> columns,
        boolean trigger,
        boolean triggerMetadataVisible
    ) {
        DatabaseMetaData metaData = proxy(DatabaseMetaData.class, (proxy, method, args) -> {
            if ("getDatabaseProductName".equals(method.getName())) {
                return productName;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(Connection.class, (proxy, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return metaData;
            }
            if ("getCatalog".equals(method.getName())) {
                return "test_schema";
            }
            if ("prepareStatement".equals(method.getName())) {
                String sql = String.valueOf(args[0]);
                List<Map<String, String>> rows = sql.contains("information_schema.columns")
                    ? columns
                    : (sql.contains("information_schema.triggers") && trigger
                        ? Collections.singletonList(column("trigger_name", "", ""))
                        : (sql.equalsIgnoreCase("show grants") && triggerMetadataVisible
                            ? Collections.singletonList(column(
                                "grant",
                                "GRANT ALL PRIVILEGES ON *.* TO 'test'@'%'",
                                ""
                            ))
                            : Collections.<Map<String, String>>emptyList()));

                return statement(rows);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static PreparedStatement statement(List<Map<String, String>> rows) {
        ResultSet resultSet = resultSet(rows);
        return proxy(PreparedStatement.class, (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName())) {
                return resultSet;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(List<Map<String, String>> rows) {
        List<Map<String, String>> snapshot = new ArrayList<Map<String, String>>(rows);
        return proxy(ResultSet.class, new InvocationHandler() {
            private int index = -1;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("next".equals(method.getName())) {
                    index++;
                    return index < snapshot.size();
                }
                if ("getString".equals(method.getName())) {
                    Object key = args[0];
                    if (key instanceof Integer) {
                        if ("grant".equals(snapshot.get(index).get("column_name"))) {
                            return snapshot.get(index).get("extra");
                        }
                        return snapshot.get(index).values().iterator().next();
                    }
                    return snapshot.get(index).get(String.valueOf(key).toLowerCase(java.util.Locale.ROOT));
                }
                return defaultValue(method.getReturnType());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            handler
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Character.TYPE) {
            return Character.valueOf('\0');
        }
        if (type == Byte.TYPE) {
            return Byte.valueOf((byte) 0);
        }
        if (type == Short.TYPE) {
            return Short.valueOf((short) 0);
        }
        if (type == Integer.TYPE) {
            return Integer.valueOf(0);
        }
        if (type == Long.TYPE) {
            return Long.valueOf(0L);
        }
        if (type == Float.TYPE) {
            return Float.valueOf(0F);
        }
        if (type == Double.TYPE) {
            return Double.valueOf(0D);
        }
        return null;
    }
}
