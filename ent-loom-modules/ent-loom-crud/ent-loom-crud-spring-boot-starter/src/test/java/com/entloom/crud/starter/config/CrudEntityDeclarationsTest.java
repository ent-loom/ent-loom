package com.entloom.crud.starter.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 实体治理注解的编译与校验合同。 */
class CrudEntityDeclarationsTest {
    @Test
    void 注解使用实体实际资源码() {
        var declarations = new CrudEntityDeclarations(List.of(Named.class, URLValue.class));
        assertThat(declarations.options()).containsKeys("custom", "urlValue");
        assertThat(declarations.options().get("custom").getPolicy()).isEqualTo("read");
    }

    @Test
    void 显式实体类自动编译治理注解() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(CrudEntityDeclarationConfiguration.class)
            .withPropertyValues("ent.loom.meta.entity-class-names=" + Named.class.getName())
            .withBean(com.entloom.crud.core.runtime.meta.EntityMetaRegistry.class,
                () -> {
                    var registry = org.mockito.Mockito.mock(com.entloom.crud.core.runtime.meta.EntityMetaRegistry.class);
                    var meta = org.mockito.Mockito.mock(com.entloom.crud.core.runtime.meta.EntityMeta.class);
                    org.mockito.Mockito.when(meta.getResourceDescriptor()).thenReturn(
                        new com.entloom.crud.core.runtime.meta.ResourceDescriptor(Named.class, "custom", null, List.of()));
                    org.mockito.Mockito.when(registry.getEntityMetas()).thenReturn(List.of(meta));
                    return registry;
                })
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CrudEntityDeclarations.class).options()).containsKey("custom");
            });
    }

    @Test
    void 重复身份和字段上限拒绝启动() {
        assertThatThrownBy(() -> new CrudEntityDeclarations(List.of(DuplicateAccess.class)))
            .hasMessageContaining("重复身份分支");
        assertThatThrownBy(() -> new CrudEntityDeclarations(List.of(DuplicateLimits.class)))
            .hasMessageContaining("重复字段用途");
    }

    @Test
    void 注解资源未注册时拒绝静默忽略() {
        var declarations = new CrudEntityDeclarations(List.of(Named.class));
        assertThatThrownBy(() -> declarations.validate(CrudPolicyAutoConfigurationTest.metas(Order.class)))
            .hasMessageContaining("实体未注册或未扫描: custom");
    }

    @com.entloom.crud.annotations.EntCrudEntity
    @com.entloom.crud.annotations.EntCrudAccess(identity = "merchant")
    @com.entloom.crud.annotations.EntCrudAccess(identity = "merchant")
    private static class DuplicateAccess { }

    @com.entloom.crud.annotations.EntCrudEntity(fieldLimits = {"RETURN:id", "return:name"})
    private static class DuplicateLimits { }

    @com.entloom.meta.annotations.EntEntity(entity = "custom")
    @com.entloom.crud.annotations.EntCrudEntity(policy = "read")
    private static class Named { }

    @com.entloom.meta.annotations.EntEntity
    @com.entloom.crud.annotations.EntCrudEntity
    private static class URLValue { }

    @Test
    void 注解编译动作输入契约与字段差异() {
        var declaration = new CrudEntityDeclarations(List.of(AnnotatedOrder.class));
        var options = declaration.options().get("order");
        assertThat(options.getPolicy()).isEqualTo("maintainable");
        assertThat(options.getActionSelection().replacement()).containsExactly("view", "update");
        assertThat(options.getFieldLimits().get(com.entloom.crud.core.governance.authorization.AuthorizationFields.Use.RETURN))
            .containsExactly("id", "title");
        assertThat(options.getAccess().getAccess().get("merchant").getFields()
            .get(com.entloom.crud.core.governance.authorization.AuthorizationFields.Use.WRITE).getFields())
            .containsExactly("title");
        assertThat(declaration.required().get("order")).containsExactly("title");
        assertThat(declaration.forbidden().get("order")).containsExactly("id");
    }

    @Test
    void 错误字段和关系声明拒绝() {
        for (Class<?> type : List.of(InvalidRelation.class, DuplicateFieldUse.class, EmptyInclude.class, AllWithFields.class)) {
            assertThatThrownBy(() -> new CrudEntityDeclarations(List.of(type))).as(type.getSimpleName())
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 显式动作列表限制业务处理器包括空集合() {
        var closed = new CrudEntityDeclarations(List.of(ClosedActions.class));
        var standard = new CrudEntityDeclarations(List.of(StandardActions.class));
        assertThat(closed.actions()).containsEntry("order", java.util.Set.of());
        assertThat(standard.actions()).containsEntry("order", java.util.Set.of("view"));
    }

    @com.entloom.crud.annotations.EntCrudEntity(name = "order", policy = "maintainable",
        actions = {"view", "update"}, fieldLimits = {"RETURN:id,title", "SORT:id"},
        requiredCreateFields = "title", forbiddenUpdateFields = "id")
    @com.entloom.crud.annotations.EntCrudAccess(identity = "merchant", fields =
        @com.entloom.crud.annotations.EntCrudAccessField(use = "WRITE", mode = "INCLUDE", fields = "title"))
    private static class AnnotatedOrder { }

    @com.entloom.crud.annotations.EntCrudEntity(relations = "bad")
    private static class InvalidRelation { }
    @com.entloom.crud.annotations.EntCrudEntity
    @com.entloom.crud.annotations.EntCrudAccess(identity = "merchant", fields = {
        @com.entloom.crud.annotations.EntCrudAccessField(use = "WRITE"),
        @com.entloom.crud.annotations.EntCrudAccessField(use = "write")})
    private static class DuplicateFieldUse { }
    @com.entloom.crud.annotations.EntCrudEntity
    @com.entloom.crud.annotations.EntCrudAccess(identity = "merchant", fields =
        @com.entloom.crud.annotations.EntCrudAccessField(use = "WRITE", mode = "INCLUDE"))
    private static class EmptyInclude { }
    @com.entloom.crud.annotations.EntCrudEntity
    @com.entloom.crud.annotations.EntCrudAccess(identity = "merchant", fields =
        @com.entloom.crud.annotations.EntCrudAccessField(use = "WRITE", mode = "ALL", fields = "id"))
    private static class AllWithFields { }

    @com.entloom.crud.annotations.EntCrudEntity(name = "order", actionsSpecified = true)
    private static class ClosedActions { }
    @com.entloom.crud.annotations.EntCrudEntity(name = "order", actions = "view")
    private static class StandardActions { }

    private static class Order { }
}
