package com.entloom.crud.starter;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.AllowAllCrudDataScopeResolver;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.starter.config.CrudExampleAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class CrudExampleAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CrudExampleAutoConfiguration.class));

    @Test
    void 默认和显式关闭时均不装配演示治理() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CrudSubjectResolver.class);
            assertThat(context).doesNotHaveBean(CrudDataScopeResolver.class);
        });
        runner.withPropertyValues("ent.loom.crud.example.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CrudSubjectResolver.class);
            assertThat(context).doesNotHaveBean(CrudDataScopeResolver.class);
        });
    }

    @Test
    void 启用后使用默认主体且每次解析互相隔离() {
        runner.withPropertyValues("ent.loom.crud.example.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CrudSubjectResolver.class);
            assertThat(context).hasSingleBean(AllowAllCrudDataScopeResolver.class);
            CrudSubjectResolver resolver = context.getBean(CrudSubjectResolver.class);
            SubjectContext first = resolver.resolveOrThrow();
            first.setSubjectId("changed");
            assertThat(resolver.resolveOrThrow().getSubjectId()).isEqualTo("local-developer");
        });
    }

    @Test
    void 可配置主体并单独关闭全量数据范围() {
        runner.withPropertyValues("ent.loom.crud.example.enabled=true",
            "ent.loom.crud.example.subject-id=demo-user", "ent.loom.crud.example.allow-all-data-scope=false")
            .run(context -> {
                assertThat(context.getBean(CrudSubjectResolver.class).resolveOrThrow().getSubjectId()).isEqualTo("demo-user");
                assertThat(context).doesNotHaveBean(CrudDataScopeResolver.class);
            });
    }

    @Test
    void 自定义主体不影响默认范围且自定义范围不影响默认主体() {
        CrudSubjectResolver subject = () -> new SubjectContext();
        CrudDataScopeResolver scope = org.mockito.Mockito.mock(CrudDataScopeResolver.class);
        runner.withPropertyValues("ent.loom.crud.example.enabled=true")
            .withBean(CrudSubjectResolver.class, () -> subject).run(context -> {
                assertThat(context.getBean(CrudSubjectResolver.class)).isSameAs(subject);
                assertThat(context).hasSingleBean(AllowAllCrudDataScopeResolver.class);
            });
        runner.withPropertyValues("ent.loom.crud.example.enabled=true")
            .withBean(CrudDataScopeResolver.class, () -> scope).run(context -> {
                assertThat(context.getBean(CrudDataScopeResolver.class)).isSameAs(scope);
                assertThat(context.getBean(CrudSubjectResolver.class).resolveOrThrow().getSubjectId()).isEqualTo("local-developer");
            });
    }

    @Test
    void 空主体配置在启动时失败() {
        runner.withPropertyValues("ent.loom.crud.example.enabled=true", "ent.loom.crud.example.subject-id= ")
            .run(context -> assertThat(context).hasFailed());
    }
}
