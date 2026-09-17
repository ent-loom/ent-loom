package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.core.exception.ValidationException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;
import javax.sql.DataSource;

/**
 * 校验 MySQL 无版本更新所需的 matched-rows 连接语义。
 *
 * <p>MySQL Connector/J 的 {@code useAffectedRows=false} 使用 matched rows；未配置时使用
 * Connector/J 默认值 false。其它数据库由各自 JDBC 行为测试负责，不在此处伪装成 MySQL 语义。</p>
 */
public final class JdbcMatchedRowsStartupValidator {
    private final DataSource dataSource;

    public JdbcMatchedRowsStartupValidator(DataSource dataSource) {
        if (dataSource == null) {
            throw new ValidationException("DataSource 不能为空");
        }
        this.dataSource = dataSource;
    }

    /** 在应用启动期连接真实数据源并校验配置。 */
    public void validateOrThrow() {
        try (Connection connection = dataSource.getConnection()) {
            validateConnection(connection);
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            ValidationException validationException = new ValidationException("无法校验 JDBC matched-rows 配置");
            validationException.initCause(ex);
            throw validationException;
        }
    }

    static void validateConnection(Connection connection) throws Exception {
        if (connection == null) {
            throw new ValidationException("JDBC 连接不能为空");
        }
        DatabaseMetaData metaData = connection.getMetaData();
        String productName = metaData == null ? null : metaData.getDatabaseProductName();
        if (!isMySql(productName)) {
            return;
        }

        String driverName = metaData.getDriverName();
        String url = metaData.getURL();
        if (!containsIgnoreCase(driverName, "mysql") && !containsIgnoreCase(url, "jdbc:mysql:")) {
            throw new ValidationException("MySQL 数据库未使用可识别的 MySQL JDBC 驱动: " + driverName);
        }

        Boolean useAffectedRows = resolveUseAffectedRows(connection, url);
        if (Boolean.TRUE.equals(useAffectedRows)) {
            throw new ValidationException(
                "MySQL matched-rows 语义要求 useAffectedRows=false，当前连接配置为 true"
            );
        }
    }

    private static Boolean resolveUseAffectedRows(Connection connection, String url) {
        Boolean urlValue = readBooleanParameter(url, "useAffectedRows");
        if (urlValue != null) {
            return urlValue;
        }
        Boolean driverValue = readConnectorProperty(connection);
        return driverValue == null ? Boolean.FALSE : driverValue;
    }

    /** 通过 Connector/J 的连接实现读取池化连接实际生效的属性。 */
    private static Boolean readConnectorProperty(Connection connection) {
        try {
            Class<?> connectorConnection = Class.forName("com.mysql.cj.jdbc.JdbcConnection");
            Object target = connection.isWrapperFor(connectorConnection)
                ? connection.unwrap(connectorConnection)
                : connection;
            Method getPropertySet = target.getClass().getMethod("getPropertySet");
            Object propertySet = getPropertySet.invoke(target);
            Method getBooleanProperty = propertySet.getClass().getMethod("getBooleanProperty", String.class);
            Object property = getBooleanProperty.invoke(propertySet, "useAffectedRows");
            Method getValue = property.getClass().getMethod("getValue");
            Object value = getValue.invoke(property);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Boolean readBooleanParameter(String url, String parameterName) {
        if (url == null) {
            return null;
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart + 1 >= url.length()) {
            return null;
        }
        String query = url.substring(queryStart + 1);
        for (String part : query.split("[&;]")) {
            int separator = part.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String name = decode(part.substring(0, separator));
            if (!parameterName.equalsIgnoreCase(name)) {
                continue;
            }
            String rawValue = decode(part.substring(separator + 1));
            if ("true".equalsIgnoreCase(rawValue)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(rawValue)) {
                return Boolean.FALSE;
            }
            throw new ValidationException("MySQL useAffectedRows 只能配置为 true 或 false");
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ex) {
            return value;
        }
    }

    private static boolean isMySql(String productName) {
        return containsIgnoreCase(productName, "mysql");
    }

    private static boolean containsIgnoreCase(String value, String fragment) {
        return value != null && fragment != null
            && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }
}
