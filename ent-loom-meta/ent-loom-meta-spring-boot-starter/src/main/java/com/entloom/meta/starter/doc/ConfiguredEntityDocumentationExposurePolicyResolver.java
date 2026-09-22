package com.entloom.meta.starter.doc;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 根据主体、正式实体编码与字段白名单生成策略；启动时保存独立配置快照。 */
public final class ConfiguredEntityDocumentationExposurePolicyResolver
    implements EntityDocumentationExposurePolicyResolver {
    private final Set<String> subjectIds;
    private final EntityDocumentationExposurePolicy policy;

    public ConfiguredEntityDocumentationExposurePolicyResolver(EntityDocumentationContractProperties.Exposure properties) {
        subjectIds = new LinkedHashSet<>(properties.getSubjectIds());
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        properties.getFields().forEach((code, names) -> fields.put(code,
            names == null ? Collections.emptySet() : new LinkedHashSet<>(names)));
        policy = new EntityDocumentationExposurePolicy() {
            @Override
            public boolean isEntityExposed(DocEntityModel entity) {
                return entity != null && entity.entityClass() != null
                    && fields.containsKey(entity.resourceCode().value());
            }

            @Override
            public boolean isFieldExposed(DocEntityModel entity, DocFieldModel field) {
                return isEntityExposed(entity) && field != null
                    && fields.get(entity.resourceCode().value()).contains(field.property());
            }
        };
    }

    @Override
    public EntityDocumentationExposurePolicy resolve(SubjectContext subject) {
        return subject != null && subject.getSubjectId() != null && !subject.getSubjectId().trim().isEmpty()
            && subjectIds.contains(subject.getSubjectId()) ? policy : EntityDocumentationExposurePolicy.denyAll();
    }
}
