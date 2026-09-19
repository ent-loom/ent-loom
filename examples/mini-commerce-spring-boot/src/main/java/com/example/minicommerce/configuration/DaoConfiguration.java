package com.example.minicommerce.configuration;

import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.core.convention.RequiredInferenceContribution;
import com.entloom.meta.core.convention.RequiredInferenceFilter;
import com.entloom.meta.core.convention.RequiredInferenceValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 示例主数据范围；生产项目应根据可信租户或组织上下文解析。 */
@Configuration(proxyBeanMethods = false)
public class DaoConfiguration {
    /** 示例项目级约定：可写且无默认值的业务字段在创建时默认必填。 */
    @Bean
    public RequiredInferenceFilter requiredInferenceFilter() {
        return context -> {
            EntField field = context.field().getAnnotation(EntField.class);
            if (context.field().isAnnotationPresent(EntMetaId.class)
                || "id".equals(context.field().getName())
                || (field != null && field.readOnly() == OptionalBoolean.TRUE)
                || (field != null && !field.createDefaultValue().isBlank())) {
                return new RequiredInferenceContribution(
                    RequiredInferenceValue.UNSET, "mini-commerce.required.exclude-managed", "生成、只读或默认值字段由专属规则处理");
            }
            return new RequiredInferenceContribution(
                RequiredInferenceValue.TRUE, "mini-commerce.required.writable-field", "创建场景下业务字段默认必填");
        };
    }

    @Bean
    public EntityDaoScopeResolver entityDaoScopeResolver() {
        return entityType -> EntityAccessScope.unrestricted();
    }
}
