package com.entloom.testsupport.mysql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * MySQL 集成测试的本地连接和执行配置。
 *
 * <p>该类仅位于测试辅助构件，供需要建库、建表和删库权限的集成测试复用，
 * 不属于框架运行时 API。连接参数优先读取统一系统属性，其次读取环境变量和 Maven
 * 根目录的 {@code .env.local}。</p>
 */
public final class MysqlIntegrationSettings {

    /** 是否启用 MySQL 集成测试的统一系统属性。 */
    public static final String INTEGRATION_ENABLED_PROPERTY = "entloom.mysql.integration.enabled";

    /** 是否在测试结束后保留随机 schema 供本地检查的统一系统属性。 */
    public static final String KEEP_SCHEMA_PROPERTY = "entloom.mysql.integration.keep-schema";

    /** MySQL 连接地址的统一系统属性。 */
    public static final String URL_PROPERTY = "entloom.mysql.integration.url";

    /** MySQL 用户名的统一系统属性。 */
    public static final String USERNAME_PROPERTY = "entloom.mysql.integration.username";

    /** MySQL 密码的统一系统属性。 */
    public static final String PASSWORD_PROPERTY = "entloom.mysql.integration.password";

    private static final String TEST_URL_VARIABLE = "ENTLOOM_TEST_MYSQL_URL";
    private static final String TEST_USERNAME_VARIABLE = "ENTLOOM_TEST_MYSQL_USERNAME";
    private static final String TEST_PASSWORD_VARIABLE = "ENTLOOM_TEST_MYSQL_PASSWORD";
    private static final String LEGACY_SPRING_URL_PROPERTY = "SPRING_DATASOURCE_URL";
    private static final String LEGACY_SPRING_USERNAME_PROPERTY = "SPRING_DATASOURCE_USERNAME";
    private static final String LEGACY_SPRING_PASSWORD_PROPERTY = "SPRING_DATASOURCE_PASSWORD";

    private final String url;
    private final String username;
    private final String password;
    private final boolean keepSchema;

    private MysqlIntegrationSettings(String url, String username, String password, boolean keepSchema) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.keepSchema = keepSchema;
    }

    /**
     * 从当前进程和本地配置文件加载连接参数。
     *
     * @return 已校验连接地址和用户名的配置
     */
    public static MysqlIntegrationSettings load() {
        return resolve(System.getProperties(), System.getenv(), loadLocalProperties());
    }

    /**
     * 返回 MySQL JDBC 地址。
     *
     * @return MySQL JDBC 地址
     */
    public String url() {
        return url;
    }

    /**
     * 返回 MySQL 用户名。
     *
     * @return MySQL 用户名
     */
    public String username() {
        return username;
    }

    /**
     * 返回 MySQL 密码；允许空密码以兼容本地临时实例。
     *
     * @return MySQL 密码
     */
    public String password() {
        return password;
    }

    /**
     * 返回测试结束后是否保留随机 schema。
     *
     * @return 开关值，默认关闭
     */
    public boolean keepSchema() {
        return keepSchema;
    }

    static MysqlIntegrationSettings resolve(Properties systemProperties,
                                            Map<String, String> environment,
                                            Properties localProperties) {
        String url = requiredValue(URL_PROPERTY, TEST_URL_VARIABLE, LEGACY_SPRING_URL_PROPERTY,
                systemProperties, environment, localProperties);
        String username = requiredValue(USERNAME_PROPERTY, TEST_USERNAME_VARIABLE,
                LEGACY_SPRING_USERNAME_PROPERTY, systemProperties, environment, localProperties);
        String password = optionalValue(PASSWORD_PROPERTY, TEST_PASSWORD_VARIABLE,
                LEGACY_SPRING_PASSWORD_PROPERTY, systemProperties, environment, localProperties);
        boolean keepSchema = Boolean.parseBoolean(text(systemProperties.getProperty(KEEP_SCHEMA_PROPERTY)));
        return new MysqlIntegrationSettings(url, username, password, keepSchema);
    }

    private static String requiredValue(String systemProperty,
                                        String environmentVariable,
                                        String legacyLocalProperty,
                                        Properties systemProperties,
                                        Map<String, String> environment,
                                        Properties localProperties) {
        String value = optionalValue(systemProperty, environmentVariable, legacyLocalProperty,
                systemProperties, environment, localProperties);
        if (!value.isEmpty()) {
            return value;
        }
        throw new IllegalStateException("缺少 MySQL 集成测试连接配置: " + systemProperty
                + "。请通过 -D 参数、环境变量 " + environmentVariable
                + " 或 Maven 根目录 .env.local 提供。");
    }

    private static String optionalValue(String systemProperty,
                                        String environmentVariable,
                                        String legacyLocalProperty,
                                        Properties systemProperties,
                                        Map<String, String> environment,
                                        Properties localProperties) {
        String value = text(systemProperties.getProperty(systemProperty));
        if (!value.isEmpty()) {
            return value;
        }
        value = text(environment.get(environmentVariable));
        if (!value.isEmpty()) {
            return value;
        }
        value = text(localProperties.getProperty(environmentVariable));
        if (!value.isEmpty()) {
            return value;
        }
        return text(localProperties.getProperty(legacyLocalProperty));
    }

    private static Properties loadLocalProperties() {
        Properties properties = new Properties();
        Optional<Path> localFile = findLocalFile();
        if (localFile.isEmpty()) {
            return properties;
        }
        try (InputStream inputStream = Files.newInputStream(localFile.get())) {
            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("读取 MySQL 本地配置失败: " + localFile.get(), exception);
        }
    }

    private static Optional<Path> findLocalFile() {
        String configuredPath = text(System.getProperty("entloom.local.env.path"));
        if (!configuredPath.isEmpty()) {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("指定的本地配置文件不存在: " + path);
            }
            return Optional.of(path);
        }

        String mavenRoot = text(System.getProperty("maven.multiModuleProjectDirectory"));
        if (!mavenRoot.isEmpty()) {
            Path path = Path.of(mavenRoot, ".env.local").toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }

        Path currentDirectory = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
        while (currentDirectory != null) {
            Path path = currentDirectory.resolve(".env.local");
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
            currentDirectory = currentDirectory.getParent();
        }
        return Optional.empty();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
