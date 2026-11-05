package com.entloom.crud.core.runtime.model.parser;

import com.entloom.base.util.reflect.EntityProperties;
import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.runtime.model.CrudRuntimeEntityModel;
import com.entloom.crud.core.runtime.model.CrudRuntimeRelationModel;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 根据已注册的唯一反向引用补全集合映射；不读取组件注解，不触发加载或授权。 */
public final class CollectionRelationMappingResolver {
    private static final String RULE_ID = "crud.relation.collection.unique-reverse";

    private CollectionRelationMappingResolver() {
    }

    public static List<CrudRuntimeRelationModel> resolve(
        Map<Class<?>, CrudRuntimeEntityModel> entities,
        Collection<CrudRuntimeRelationModel> declaredRelations
    ) {
        List<CrudRuntimeRelationModel> declared = new ArrayList<CrudRuntimeRelationModel>();
        if (declaredRelations != null) {
            for (CrudRuntimeRelationModel relation : declaredRelations) {
                if (relation != null && !RULE_ID.equals(relation.getInferenceRuleId())) {
                    declared.add(relation);
                }
            }
        }
        List<CrudRuntimeRelationModel> result = new ArrayList<CrudRuntimeRelationModel>(declared);
        for (CrudRuntimeEntityModel parent : entities.values()) {
            for (Field field : EntityProperties.fields(parent.getEntityType())) {
                if (!Collection.class.isAssignableFrom(field.getType()) || Modifier.isTransient(field.getModifiers())
                    || hasMapping(declared, parent.getEntityType(), field.getName())) {
                    continue;
                }
                Class<?> childType = EntityProperties.describe(field).elementType();
                CrudRuntimeEntityModel child = entities.get(childType);
                if (child == null) {
                    continue;
                }
                // 只反转原始关系，不让本轮补全结果继续成为推导依据。
                Map<String, CrudRuntimeRelationModel> candidates = new LinkedHashMap<String, CrudRuntimeRelationModel>();
                for (CrudRuntimeRelationModel relation : declared) {
                    if (relation != null && relation.getFromEntity() == childType
                        && relation.getToEntity() == parent.getEntityType()
                        && relation.getScope() == RelationScope.LOCAL_DB
                        && relation.getCardinality() == RelationCardinality.MANY_TO_ONE
                        && child.getField(relation.getFromField()) != null
                        && parent.getField(relation.getToField()) != null) {
                        // 同一引用的不同导航别名不构成连接歧义。
                        candidates.put(relation.getFromField() + "->" + relation.getToField(), relation);
                    }
                }
                if (candidates.size() == 1) {
                    CrudRuntimeRelationModel reverse = candidates.values().iterator().next();
                    result.add(new CrudRuntimeRelationModel(
                        parent.getEntityType(), childType, field.getName(), reverse.getToField(), reverse.getFromField(),
                        RelationScope.LOCAL_DB, JoinType.LEFT, RelationCardinality.ONE_TO_MANY, RULE_ID
                    ));
                }
            }
        }
        return result;
    }

    private static boolean hasMapping(List<CrudRuntimeRelationModel> relations, Class<?> type, String field) {
        for (CrudRuntimeRelationModel relation : relations) {
            if (relation != null && relation.getFromEntity() == type
                && Objects.equals(relation.getRelationField(), field)) {
                return true;
            }
        }
        return false;
    }
}
