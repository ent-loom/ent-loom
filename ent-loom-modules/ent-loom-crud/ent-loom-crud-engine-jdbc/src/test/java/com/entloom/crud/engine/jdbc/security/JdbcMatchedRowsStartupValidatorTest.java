package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.core.exception.ValidationException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcMatchedRowsStartupValidatorTest {
    @Test
    void mysql_use_affected_rows_true_is_rejected() {
        Connection connection = connection("MySQL", "MySQL Connector/J", "jdbc:mysql://localhost/test?useAffectedRows=true");

        Assertions.assertThrows(
            ValidationException.class,
            () -> JdbcMatchedRowsStartupValidator.validateConnection(connection)
        );
    }

    @Test
    void mysql_use_affected_rows_invalid_value_is_rejected() {
        Connection connection = connection("MySQL", "MySQL Connector/J", "jdbc:mysql://localhost/test?useAffectedRows=maybe");

        Assertions.assertThrows(
            ValidationException.class,
            () -> JdbcMatchedRowsStartupValidator.validateConnection(connection)
        );
    }

    @Test
    void mysql_false_and_non_mysql_are_accepted() throws Exception {
        JdbcMatchedRowsStartupValidator.validateConnection(
            connection("MySQL", "MySQL Connector/J", "jdbc:mysql://localhost/test?useAffectedRows=false")
        );
        JdbcMatchedRowsStartupValidator.validateConnection(
            connection("H2", "H2 JDBC Driver", "jdbc:h2:mem:test")
        );
    }

    @Test
    void mysql_without_explicit_url_value_fails_closed_when_driver_property_cannot_be_read() {
        Assertions.assertThrows(
            ValidationException.class,
            () -> JdbcMatchedRowsStartupValidator.validateConnection(
                connection("MySQL", "MySQL Connector/J", "jdbc:mysql://localhost/test")
            )
        );
    }

    private Connection connection(String product, String driver, String url) {
        DatabaseMetaData metaData = proxy(DatabaseMetaData.class, (proxy, method, args) -> {
            if ("getDatabaseProductName".equals(method.getName())) {
                return product;
            }
            if ("getDriverName".equals(method.getName())) {
                return driver;
            }
            if ("getURL".equals(method.getName())) {
                return url;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(Connection.class, (proxy, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return metaData;
            }
            if ("isWrapperFor".equals(method.getName())) {
                return false;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private Object defaultValue(Class<?> type) {
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
