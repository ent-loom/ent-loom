package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.api.model.CommandAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import static org.assertj.core.api.Assertions.*;

class CrudActionPoliciesTest {
    @Test
    void 动作注解编译入口和能力且不创建处理器() {
        var beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("handler", new RootBeanDefinition(Handler.class));
        var policies = CrudActionPolicies.collect(beans, CrudPolicyAutoConfigurationTest.metas(Order.class));
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).getKey().getAccessEntry()).isEqualTo("commerce");
        assertThat(policies.get(0).getCapability()).isEqualTo("place-order");
        assertThat(beans.containsSingleton("handler")).isFalse();
    }

    @Test
    void 声明不完整时拒绝() {
        var beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("handler", new RootBeanDefinition(IncompleteHandler.class));
        assertThatThrownBy(() -> CrudActionPolicies.collect(beans, CrudPolicyAutoConfigurationTest.metas(Order.class)))
            .hasMessageContaining("capability 和 accessEntries");
    }

    @Test
    void 空白入口类型不能退化为无限制() {
        var beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("handler", new RootBeanDefinition(BlankPortalHandler.class));
        assertThatThrownBy(() -> CrudActionPolicies.collect(beans, CrudPolicyAutoConfigurationTest.metas(Order.class)))
            .hasMessageContaining("portal 不能为空");
    }

    private static class Order { }

    @EntCrudCommandAction(value = HandlerDefinition.class, capability = "place-order", accessEntries = "commerce")
    private static class Handler { }

    @EntCrudCommandAction(value = IncompleteHandlerDefinition.class, capability = "place-order")
    private static class IncompleteHandler { }

    @EntCrudCommandAction(value = BlankPortalHandlerDefinition.class, capability = "place-order", accessEntries = "commerce", portals = " ")
    private static class BlankPortalHandler { }

    public static final class HandlerDefinition extends CommandAction<Void, Void> {
        public HandlerDefinition() {
            super(Order.class, "create", Void.class, Void.class);
        }
    }

    public static final class IncompleteHandlerDefinition extends CommandAction<Void, Void> {
        public IncompleteHandlerDefinition() {
            super(Order.class, "create", Void.class, Void.class);
        }
    }

    public static final class BlankPortalHandlerDefinition extends CommandAction<Void, Void> {
        public BlankPortalHandlerDefinition() {
            super(Order.class, "create", Void.class, Void.class);
        }
    }
}
