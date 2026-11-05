package com.entloom.crud.core.runtime.model.parser;

import com.entloom.base.util.reflect.EntityProperties;
import com.entloom.base.util.reflect.EntityProperty;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.util.Collections;

/**
 * 将外键声明绑定到同名的单对象导航属性，不推断目标实体、反向集合或加载行为。
 */
public final class RelationNavigationFieldResolver {
    private static final String RULE_ID = "crud.relation.navigation.id-suffix";

    private RelationNavigationFieldResolver() {
    }

    public static MetaDiagnosticResult<String> resolve(
        Class<?> entityClass,
        String relationField,
        String sourceField,
        Class<?> targetClass,
        RelationCardinality cardinality
    ) {
        if (entityClass == null || targetClass == null || sourceField == null
            || !sourceField.equals(relationField) || !sourceField.endsWith("Id") || sourceField.length() <= 2
            || (cardinality != RelationCardinality.MANY_TO_ONE && cardinality != RelationCardinality.ONE_TO_ONE)) {
            return unchanged(relationField);
        }
        Field source = findField(entityClass, sourceField);
        if (source == null || !EntityProperty.isSingleColumnType(source.getType())) {
            return unchanged(relationField);
        }
        String navigationName = sourceField.substring(0, sourceField.length() - 2);
        Field navigation = findField(entityClass, navigationName);
        if (navigation == null) {
            return unchanged(relationField);
        }
        if (!targetClass.equals(navigation.getType())) {
            return MetaDiagnosticResult.of(relationField, Collections.singletonList(
                MetaDiagnostic.error(MetaDiagnosticCode.RELATION_NAVIGATION_TYPE_MISMATCH)
                    .entityClass(entityClass).field(navigationName).property("relationField")
                    .source(MetaValueSource.INFERRED).ruleId(RULE_ID)
                    .location(entityClass.getName() + "#" + navigationName)
                    .message("外键 " + sourceField + " 的同名导航属性类型不匹配，预期 " + targetClass.getName()
                        + "，实际 " + navigation.getType().getTypeName()
                        + "；请调整属性名称或类型，使导航属性与关系目标一致")
                    .build()
            ));
        }
        return MetaDiagnosticResult.of(navigationName, Collections.singletonList(
            MetaDiagnostic.info(MetaDiagnosticCode.INFERRED_VALUE_USED)
                .entityClass(entityClass).field(navigationName).property("relationField")
                .source(MetaValueSource.INFERRED).ruleId(RULE_ID)
                .location(entityClass.getName() + "#" + navigationName)
                .message("外键关系按名称与目标类型绑定导航属性: " + sourceField + " -> " + navigationName)
                .build()
        ));
    }

    private static MetaDiagnosticResult<String> unchanged(String relationField) {
        return MetaDiagnosticResult.of(relationField, Collections.<MetaDiagnostic>emptyList());
    }

    private static Field findField(Class<?> entityClass, String name) {
        for (Field field : EntityProperties.fields(entityClass)) {
            if (name.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }
}
