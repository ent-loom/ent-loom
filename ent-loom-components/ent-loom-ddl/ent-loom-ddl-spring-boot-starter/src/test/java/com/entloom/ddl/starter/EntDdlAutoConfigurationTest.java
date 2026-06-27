package com.entloom.ddl.starter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EntDdlAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(EntDdlAutoConfiguration.class);

    @Test
    void invalidEntityClassNameShouldFailFast() {
        contextRunner
            .withPropertyValues("entloom.ddl.entity-class-names=com.entloom.missing.NoSuchEntity")
            .run(context -> {
                Assertions.assertNotNull(context.getStartupFailure());
                Assertions.assertTrue(context.getStartupFailure().getMessage()
                    .contains("无法加载 entloom.ddl.entity-class-names 配置的实体类"));
            });
    }

    @Test
    void configuredButEmptyBasePackageShouldFailFastWhenEnabled() {
        contextRunner
            .withPropertyValues(
                "entloom.ddl.enabled=true",
                "entloom.ddl.base-packages=com.entloom.missing"
            )
            .run(context -> {
                Assertions.assertNotNull(context.getStartupFailure());
                Assertions.assertTrue(context.getStartupFailure().getMessage()
                    .contains("已配置 DDL 实体类或扫描包，但未加载到任何 @EntDbEntity 实体"));
            });
    }
}
