package com.entloom.ddl.spring;

import com.entloom.ddl.annotations.EntDbEntity;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import java.util.Collections;
import java.util.List;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring 注解元数据加载合同测试。
 */
class SpringAnnotationMetadataLoaderTest {

    @Test
    @DisplayName("Spring 加载器对包装类型 id 使用非空主键")
    void shouldMakeInferredIdNonNullable() {
        List<DdlEntityMetadata> entities = new SpringAnnotationMetadataLoader(null).load(
                new MetadataLoadRequest(Collections.<String>emptyList(),
                        Collections.<Class<?>>singletonList(AccountEntity.class)));

        DdlFieldMetadata id = entities.get(0).fields().get(0);

        assertTrue(id.primaryKey());
        assertFalse(id.nullable());
    }

    @Test
    @DisplayName("Spring 配置不默认注册 Noop SPI")
    void shouldNotRegisterNoopSpiByDefault() {
        org.springframework.context.annotation.AnnotationConfigApplicationContext context =
                new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        context.register(EntDdlSpringConfiguration.class);
        context.refresh();
        try {
            assertTrue(context.getBeansOfType(com.entloom.ddl.api.QueryStrategy.class).isEmpty());
            assertTrue(context.getBeansOfType(com.entloom.ddl.api.SqlExecutor.class).isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    @DisplayName("启用 DDL 但未配置 SPI 时必须快速失败")
    void shouldFailWhenEnabledWithoutSpi() {
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setEnabled(true);
        options.setMode(DdlExecutionMode.CREATE_TABLE);
        EntDdlSpringExecutor executor = new EntDdlSpringExecutor(
                new EmptyDdlEngine(),
                request -> Collections.<DdlEntityMetadata>emptyList(),
                null,
                null,
                options);

        assertThrows(IllegalStateException.class,
                () -> executor.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext())));
    }

    @Test
    @DisplayName("关闭 DDL 时不加载实体也不调用引擎")
    void shouldSkipWhenDisabled() {
        CountingDdlEngine engine = new CountingDdlEngine();
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setMode(DdlExecutionMode.CREATE_TABLE);
        EntDdlSpringExecutor executor = new EntDdlSpringExecutor(
                engine,
                request -> {
                    throw new AssertionError("关闭 DDL 时不应加载实体");
                },
                null,
                null,
                options);

        executor.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext()));

        assertEquals(0, engine.calls);
    }

    @Test
    @DisplayName("Spring 容器重复刷新时只执行一次 DDL")
    void shouldExecuteOnlyOnceAfterRepeatedRefreshEvents() {
        CountingDdlEngine engine = new CountingDdlEngine();
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setEnabled(true);
        options.setMode(DdlExecutionMode.CREATE_TABLE);
        EntDdlSpringExecutor executor = new EntDdlSpringExecutor(
                engine,
                request -> Collections.<DdlEntityMetadata>emptyList(),
                (schema, tableName) -> false,
                sqlStatements -> {
                },
                options);

        executor.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext()));
        executor.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext()));

        assertEquals(1, engine.calls);
    }

    @Test
    @DisplayName("引擎失败结果保留错误信息并向 Spring 暴露诊断")
    void shouldPreserveEngineErrors() {
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setEnabled(true);
        options.setMode(DdlExecutionMode.CREATE_TABLE);
        EntDdlSpringExecutor executor = new EntDdlSpringExecutor(
                (request, queryStrategy, sqlExecutor) -> new DdlExecutionResult(
                        Collections.singletonList("CREATE TABLE `account`"),
                        Collections.<String>emptyList(),
                        Collections.singletonList("数据库连接失败")),
                request -> Collections.<DdlEntityMetadata>emptyList(),
                (schema, tableName) -> false,
                sqlStatements -> {
                },
                options);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> executor.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext())));

        assertTrue(exception.getMessage().contains("数据库连接失败"));
    }

    @EntDbEntity(table = "account")
    private static final class AccountEntity {
        private Long id;
        private String nickname;
    }

    private static final class EmptyDdlEngine implements DdlEngine {
        @Override
        public DdlExecutionResult execute(com.entloom.ddl.api.DdlExecutionRequest request,
                                          QueryStrategy queryStrategy,
                                          SqlExecutor sqlExecutor) {
            return new DdlExecutionResult(Collections.<String>emptyList(),
                    Collections.<String>emptyList(), Collections.<String>emptyList());
        }
    }

    private static final class CountingDdlEngine implements DdlEngine {
        private int calls;

        @Override
        public DdlExecutionResult execute(com.entloom.ddl.api.DdlExecutionRequest request,
                                          QueryStrategy queryStrategy,
                                          SqlExecutor sqlExecutor) {
            calls++;
            return new DdlExecutionResult(Collections.<String>emptyList(),
                    Collections.<String>emptyList(), Collections.<String>emptyList());
        }
    }
}
