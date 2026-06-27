package com.entloom.meta.core.support;

import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.core.model.EntRelationEdgeModel;
import com.entloom.meta.core.parser.EntMetaParser;
import com.entloom.meta.core.spi.EntRelationEntityResolver;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves runtime relation edges from EntEntity/EntRelation metadata.
 */
public final class EntRelationEdgeResolver {
    private final EntMetaParser parser;

    public EntRelationEdgeResolver(EntMetaParser parser) {
        if (parser == null) {
            throw new IllegalArgumentException("parser 不能为空");
        }
        this.parser = parser;
    }

    public List<EntRelationEdgeModel> resolve(
        Collection<Class<?>> entityClasses,
        EntRelationEntityResolver entityResolver
    ) {
        if (entityClasses == null || entityClasses.isEmpty() || entityResolver == null) {
            return java.util.Collections.emptyList();
        }
        List<EntRelationEdgeModel> edges = new ArrayList<EntRelationEdgeModel>();
        Set<String> registered = new LinkedHashSet<String>();
        for (Class<?> childType : entityClasses) {
            if (childType == null) {
                continue;
            }
            MetaDiagnosticResult<EntEntityDescriptor> result = parser.parseWithDiagnostics(childType);
            EntEntityDescriptor descriptor = result.value();
            if (descriptor == null || descriptor.relations().isEmpty()) {
                continue;
            }
            for (EntRelationDescriptor relation : descriptor.relations()) {
                if (relation == null || isBlank(relation.targetEntity())) {
                    continue;
                }
                String toField = relation.sourceField();
                if (isBlank(toField) || !entityResolver.isAllowedField(childType, toField)) {
                    continue;
                }
                Class<?> rootType = entityResolver.resolveEntityClass(relation.targetEntity());
                String rootIdField = entityResolver.resolveIdField(rootType);
                if (rootType == null || isBlank(rootIdField)) {
                    continue;
                }
                String relationFieldName = resolveRelationFieldName(rootType, childType);
                String key = rootType.getName() + "->" + childType.getName() + "#" + toField;
                if (registered.add(key)) {
                    edges.add(new EntRelationEdgeModel(
                        rootType,
                        childType,
                        relationFieldName,
                        rootIdField,
                        toField,
                        resolveCardinality(rootType, childType, relationFieldName, relation.cardinality())
                    ));
                }
            }
        }
        return edges;
    }

    private String resolveRelationFieldName(Class<?> rootType, Class<?> childType) {
        if (rootType == null || childType == null) {
            return null;
        }
        String expectedCollectionFieldName = expectedCollectionRelationFieldName(childType);
        Field collectionField = null;
        Field directField = null;
        Field invalidCollectionField = null;
        for (Field field : getAllFields(rootType)) {
            if (Collection.class.isAssignableFrom(field.getType()) && isCollectionElementType(field, childType)) {
                if (expectedCollectionFieldName.equals(field.getName())) {
                    collectionField = field;
                } else if (invalidCollectionField == null) {
                    invalidCollectionField = field;
                }
                continue;
            }
            if (directField == null && Objects.equals(field.getType(), childType)) {
                directField = field;
            }
        }
        if (invalidCollectionField != null) {
            throw new IllegalStateException(
                "检测到非规范 1:N 关系字段 "
                    + rootType.getSimpleName()
                    + "."
                    + invalidCollectionField.getName()
                    + "，框架要求使用 "
                    + expectedCollectionFieldName
            );
        }
        if (collectionField != null) {
            return collectionField.getName();
        }
        return directField == null ? null : directField.getName();
    }

    private RelationCardinality resolveCardinality(
        Class<?> rootType,
        Class<?> childType,
        String relationFieldName,
        RelationCardinality declaredCardinality
    ) {
        if (rootType == null || childType == null || isBlank(relationFieldName)) {
            return declaredCardinality == null ? RelationCardinality.ONE_TO_MANY : declaredCardinality;
        }
        for (Field field : getAllFields(rootType)) {
            if (!relationFieldName.equals(field.getName())) {
                continue;
            }
            if (Collection.class.isAssignableFrom(field.getType()) && isCollectionElementType(field, childType)) {
                return RelationCardinality.ONE_TO_MANY;
            }
            if (Objects.equals(field.getType(), childType)) {
                return RelationCardinality.ONE_TO_ONE;
            }
        }
        return declaredCardinality == null ? RelationCardinality.ONE_TO_MANY : declaredCardinality;
    }

    private List<Field> getAllFields(Class<?> entityClass) {
        ArrayList<Field> fields = new ArrayList<Field>();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isCollectionElementType(Field field, Class<?> childType) {
        Type type = field.getGenericType();
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type[] args = parameterizedType.getActualTypeArguments();
        return args.length == 1 && Objects.equals(args[0], childType);
    }

    private String expectedCollectionRelationFieldName(Class<?> childType) {
        String simpleName = childType.getSimpleName();
        if (isBlank(simpleName)) {
            return "list";
        }
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1) + "List";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
