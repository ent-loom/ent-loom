package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 异步任务重新进入治理链所需的上下文快照。
 */
public final class CrudTaskContextSnapshot {
    private final String scene;
    private final Class<?> rootType;
    private final CrudOperationKey operationKey;
    private final SubjectContext subject;
    private final Map<String, Object> attributes;

    private CrudTaskContextSnapshot(Builder builder) {
        this.scene = builder.scene;
        this.rootType = builder.rootType;
        this.operationKey = builder.operationKey;
        this.subject = copySubject(builder.subject);
        this.attributes = Collections.unmodifiableMap(copyAttributes(builder.attributes));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CrudTaskContextSnapshot fromSpec(BaseSpec spec, CrudOperationKey operationKey) {
        if (spec == null) {
            return builder().operationKey(operationKey).build();
        }
        return builder()
            .scene(spec.getScene())
            .rootType(spec.getRootType())
            .operationKey(operationKey)
            .subject(spec.getSubject())
            .attributes(spec.getAttributes())
            .build();
    }

    public String getScene() {
        return scene;
    }

    public Class<?> getRootType() {
        return rootType;
    }

    public CrudOperationKey getOperationKey() {
        return operationKey;
    }

    public SubjectContext getSubject() {
        return copySubject(subject);
    }

    public Map<String, Object> getAttributes() {
        return copyAttributes(attributes);
    }

    private static SubjectContext copySubject(SubjectContext source) {
        if (source == null) {
            return null;
        }
        SubjectContext target = new SubjectContext();
        target.setSubjectId(source.getSubjectId());
        target.setTenantId(source.getTenantId());
        target.setOrgId(source.getOrgId());
        return target;
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> source) {
        return source == null ? new HashMap<String, Object>() : new HashMap<String, Object>(source);
    }

    public static final class Builder {
        private String scene;
        private Class<?> rootType;
        private CrudOperationKey operationKey;
        private SubjectContext subject;
        private Map<String, Object> attributes = new HashMap<String, Object>();

        public Builder scene(String scene) {
            this.scene = scene;
            return this;
        }

        public Builder rootType(Class<?> rootType) {
            this.rootType = rootType;
            return this;
        }

        public Builder operationKey(CrudOperationKey operationKey) {
            this.operationKey = operationKey;
            return this;
        }

        public Builder subject(SubjectContext subject) {
            this.subject = copySubject(subject);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = copyAttributes(attributes);
            return this;
        }

        public CrudTaskContextSnapshot build() {
            return new CrudTaskContextSnapshot(this);
        }
    }
}
