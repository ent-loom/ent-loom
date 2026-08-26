package com.entloom.testsupport.mysql;

import java.util.Collections;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MySQL 集成测试配置的优先级与兼容性测试。 */
class MysqlIntegrationSettingsTest {

    @Test
    void shouldPreferSystemPropertyOverEnvironmentAndLocalConfiguration() {
        Properties systemProperties = properties(
                MysqlIntegrationSettings.URL_PROPERTY, "jdbc:mysql://system:3306/",
                MysqlIntegrationSettings.USERNAME_PROPERTY, "system-user",
                MysqlIntegrationSettings.PASSWORD_PROPERTY, "system-password");
        Properties localProperties = properties(
                "ENTLOOM_TEST_MYSQL_URL", "jdbc:mysql://local:3306/",
                "ENTLOOM_TEST_MYSQL_USERNAME", "local-user",
                "ENTLOOM_TEST_MYSQL_PASSWORD", "local-password");

        MysqlIntegrationSettings settings = MysqlIntegrationSettings.resolve(systemProperties,
                Collections.singletonMap("ENTLOOM_TEST_MYSQL_URL", "jdbc:mysql://environment:3306/"),
                localProperties);

        assertEquals("jdbc:mysql://system:3306/", settings.url());
        assertEquals("system-user", settings.username());
        assertEquals("system-password", settings.password());
    }

    @Test
    void shouldReadDedicatedLocalConfigurationBeforeLegacySpringDatasource() {
        Properties localProperties = properties(
                "ENTLOOM_TEST_MYSQL_URL", "jdbc:mysql://test:3306/",
                "ENTLOOM_TEST_MYSQL_USERNAME", "test-user",
                "ENTLOOM_TEST_MYSQL_PASSWORD", "test-password",
                "SPRING_DATASOURCE_URL", "jdbc:mysql://legacy:3306/",
                "SPRING_DATASOURCE_USERNAME", "legacy-user");

        MysqlIntegrationSettings settings = MysqlIntegrationSettings.resolve(new Properties(),
                Collections.<String, String>emptyMap(), localProperties);

        assertEquals("jdbc:mysql://test:3306/", settings.url());
        assertEquals("test-user", settings.username());
        assertEquals("test-password", settings.password());
    }

    @Test
    void shouldReadKeepSchemaSwitchFromSystemProperty() {
        Properties systemProperties = properties(
                MysqlIntegrationSettings.URL_PROPERTY, "jdbc:mysql://system:3306/",
                MysqlIntegrationSettings.USERNAME_PROPERTY, "system-user",
                MysqlIntegrationSettings.KEEP_SCHEMA_PROPERTY, "true");

        MysqlIntegrationSettings settings = MysqlIntegrationSettings.resolve(systemProperties,
                Collections.<String, String>emptyMap(), new Properties());

        assertTrue(settings.keepSchema());

        systemProperties.setProperty(MysqlIntegrationSettings.KEEP_SCHEMA_PROPERTY, "false");
        settings = MysqlIntegrationSettings.resolve(systemProperties,
                Collections.<String, String>emptyMap(), new Properties());

        assertFalse(settings.keepSchema());
    }

    @Test
    void shouldRejectMissingRequiredConnectionConfiguration() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> MysqlIntegrationSettings.resolve(new Properties(),
                        Collections.<String, String>emptyMap(), new Properties()));

        assertTrue(exception.getMessage().contains(MysqlIntegrationSettings.URL_PROPERTY));
    }

    private static Properties properties(String... entries) {
        Properties properties = new Properties();
        for (int index = 0; index < entries.length; index += 2) {
            properties.setProperty(entries[index], entries[index + 1]);
        }
        return properties;
    }
}
