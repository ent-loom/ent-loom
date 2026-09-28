package com.entloom.meta.starter.doc;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 根据主体与展示范围生成策略；启动时校验正式资源编码和字段并保存独立快照。 */
public final class ConfiguredEntityDocumentationExposurePolicyResolver
    implements EntityDocumentationExposurePolicyResolver {
    private final Set<String> subjectIds;
    private final EntityDocumentationExposurePolicy policy;

    public ConfiguredEntityDocumentationExposurePolicyResolver(
        EntityDocumentationContractProperties.Exposure properties,
        Collection<DocEntityModel> models
    ) {
        subjectIds = new LinkedHashSet<>(properties.getSubjectIds());
        Set<String> includes = new LinkedHashSet<>(properties.getIncludeEntities());
        Set<String> excludes = new LinkedHashSet<>(properties.getExcludeEntities());
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        properties.getFields().forEach((code, names) -> fields.put(code,
            names == null ? Collections.emptySet() : new LinkedHashSet<>(names)));
        Map<String, Set<String>> registeredFields = new LinkedHashMap<>();
        for (DocEntityModel model : models) {
            Set<String> names = new LinkedHashSet<>();
            model.fields().forEach(field -> names.add(field.property()));
            registeredFields.put(model.resourceCode().value(), names);
        }
        validateEntities("include-entities", includes, registeredFields);
        validateEntities("exclude-entities", excludes, registeredFields);
        validateEntities("fields", fields.keySet(), registeredFields);
        fields.forEach((code, names) -> {
            for (String name : names) {
                if (!registeredFields.get(code).contains(name)) {
                    throw new IllegalArgumentException("ent.loom.doc.contract.exposure.fields." + code
                        + " 配置了不存在的字段: " + name);
                }
            }
        });
        policy = new EntityDocumentationExposurePolicy() {
            @Override
            public boolean isEntityExposed(DocEntityModel entity) {
                return entity != null && entity.entityClass() != null
                    && registeredFields.containsKey(entity.resourceCode().value())
                    && (includes.isEmpty() || includes.contains(entity.resourceCode().value()))
                    && !excludes.contains(entity.resourceCode().value());
            }

            @Override
            public boolean isFieldExposed(DocEntityModel entity, DocFieldModel field) {
                return isEntityExposed(entity) && field != null
                    && registeredFields.get(entity.resourceCode().value()).contains(field.property())
                    && (!fields.containsKey(entity.resourceCode().value())
                        || fields.get(entity.resourceCode().value()).contains(field.property()));
            }
        };
    }

    private static void validateEntities(String property, Set<String> codes, Map<String, Set<String>> registeredFields) {
        for (String code : codes) {
            if (!registeredFields.containsKey(code)) {
                throw new IllegalArgumentException("ent.loom.doc.contract.exposure." + property
                    + " 配置了未注册的实体资源编码: " + code);
            }
        }
    }

    @Override
    public EntityDocumentationExposurePolicy resolve(SubjectContext subject) {
        return subject != null && subject.getSubjectId() != null && !subject.getSubjectId().trim().isEmpty()
            && subjectIds.contains(subject.getSubjectId()) ? policy : EntityDocumentationExposurePolicy.denyAll();
    }
}
