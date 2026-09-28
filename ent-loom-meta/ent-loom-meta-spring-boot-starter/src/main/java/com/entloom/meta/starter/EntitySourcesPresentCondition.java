package com.entloom.meta.starter;

import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 显式实体来源或 Boot 自动配置包存在时装配适配器。
 */
class EntitySourcesPresentCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        EntLoomMetaProperties properties = Binder.get(context.getEnvironment())
            .bind("ent.loom.meta", Bindable.of(EntLoomMetaProperties.class))
            .orElseGet(EntLoomMetaProperties::new);
        return !properties.getEntityClassNames().isEmpty() || !properties.getBasePackages().isEmpty()
            || (context.getBeanFactory() != null && AutoConfigurationPackages.has(context.getBeanFactory()));
    }
}
