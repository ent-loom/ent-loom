package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 实体只声明操作、输入契约与字段静态边界。 */
class CrudEntityDeclarationsTest {
    @Test
    void 资源名称与静态边界统一解析() {
        var declaration = new CrudEntityDeclarations(List.of(Order.class, URLValue.class));
        var order = declaration.options().get("order");
        assertThat(declaration.options()).containsKeys("order", "urlValue");
        assertThat(order.getPolicy()).isEqualTo("maintainable");
        assertThat(order.getActions()).containsExactly("view", "update");
        assertThat(order.getFieldLimits().get(AuthorizationFields.Use.RETURN)).containsExactly("id", "title");
        assertThat(declaration.required().get("order")).containsExactly("title");
        assertThat(declaration.forbidden().get("order")).containsExactly("id");
    }

    @Test
    void 空动作关闭全部入口且缺省只读() {
        assertThat(new CrudEntityDeclarations(List.of(Closed.class)).actions()).containsEntry("order", Set.of());
        assertThat(new CrudEntityDeclarations(List.of(URLValue.class)).actions()).containsEntry("urlValue", Set.of("view"));
    }

    @Test
    void 重复动作字段用途和错误关系拒绝启动() {
        assertThatThrownBy(() -> new CrudEntityDeclarations(List.of(DuplicateActions.class)))
            .isInstanceOf(com.entloom.crud.core.exception.ValidationException.class).hasMessageContaining("动作重复");
        assertThatThrownBy(() -> new CrudEntityDeclarations(List.of(DuplicateLimits.class)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复字段用途");
    }

    @Test
    void 未注册的实体声明不能静默忽略() {
        var declaration = new CrudEntityDeclarations(List.of(URLValue.class));
        assertThatThrownBy(() -> declaration.validate(CrudPolicyAutoConfigurationTest.metas(Order.class)))
            .hasMessageContaining("实体未注册或未扫描: urlValue");
    }

    @Test
    void 项目动作约定用于实体声明并区分空数组() {
        var defaults = new com.entloom.crud.core.governance.policy.CrudEntityActions(List.of("view", "create", "update", "delete"));
        var declarations = new CrudEntityDeclarations(List.of(Adjusted.class, Closed.class), defaults);
        assertThat(declarations.actions()).containsEntry("adjusted", Set.of("view", "create", "update", "batch-update"))
            .containsEntry("order", Set.of());
    }

    @EntCrudEntity(name = "adjusted", addActions = "batch-update", removeActions = "delete")
    private static class Adjusted {}

    @EntCrudEntity(name = "order", policy = "maintainable", actions = {"view", "update"},
        fieldLimits = {"RETURN:id,title", "SORT:id"}, requiredCreateFields = "title", forbiddenUpdateFields = "id")
    private static class Order { }
    @EntEntity("URL") @EntCrudEntity
    private static class URLValue { }
    @EntCrudEntity(name = "order", actions = {})
    private static class Closed { }
    @EntCrudEntity(actions = {"view", "view"})
    private static class DuplicateActions { }
    @EntCrudEntity(fieldLimits = {"RETURN:id", "RETURN:title"})
    private static class DuplicateLimits { }

}
