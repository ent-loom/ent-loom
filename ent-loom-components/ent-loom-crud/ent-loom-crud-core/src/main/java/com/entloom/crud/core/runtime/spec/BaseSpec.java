package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议基类（不可变）。
 */
public abstract class BaseSpec {
    /** 场景标识。 */
    private final String scene;
    /** 根类型。 */
    private final Class<?> rootType;
    /** 实体类型列表。 */
    private final List<Class<?>> entityClasses;
    /** 请求主体。 */
    private final SubjectContext subject;
    /** 扩展属性映射。 */
    private final Map<String, Object> attributes;
    /** 已授予的数据范围。 */
    private final CrudDataScope grantedScope;
    /** 治理计算出的数据范围。 */
    private final CrudDataScope governanceScope;
    /** 访问决策。 */
    private final AccessDecision accessDecision;

    protected BaseSpec(AbstractBuilder<?> builder) {
        this.scene = builder.scene;
        this.rootType = builder.rootType;
        this.entityClasses = Collections.unmodifiableList(copyEntityClasses(builder.entityClasses));
        this.subject = copySubject(builder.subject);
        this.attributes = Collections.unmodifiableMap(copyAttributes(builder.attributes));
        this.grantedScope = builder.grantedScope;
        this.governanceScope = builder.governanceScope;
        this.accessDecision = builder.accessDecision == null ? AccessDecision.ALLOW : builder.accessDecision;
    }

    public final String getScene() {
        return scene;
    }

    public final Class<?> getRootType() {
        return rootType;
    }

    public List<Class<?>> getEntityClasses() {
        return copyEntityClasses(entityClasses);
    }

    public SubjectContext getSubject() {
        return copySubject(subject);
    }

    public Map<String, Object> getAttributes() {
        return copyAttributes(attributes);
    }

    public final CrudDataScope getGrantedScope() {
        return grantedScope;
    }

    public final CrudDataScope getGovernanceScope() {
        return governanceScope;
    }

    public final AccessDecision getAccessDecision() {
        return accessDecision;
    }

    protected final <B extends AbstractBuilder<B>> B copyBaseTo(B builder) {
        return builder
            .scene(scene)
            .rootType(rootType)
            .entityClasses(getEntityClasses())
            .subject(getSubject())
            .attributes(getAttributes())
            .grantedScope(grantedScope)
            .governanceScope(governanceScope)
            .accessDecision(accessDecision);
    }

    protected static SubjectContext copySubject(SubjectContext source) {
        if (source == null) {
            return null;
        }
        SubjectContext target = new SubjectContext();
        target.setSubjectId(source.getSubjectId());
        target.setTenantId(source.getTenantId());
        target.setOrgId(source.getOrgId());
        return target;
    }

    private static List<Class<?>> copyEntityClasses(List<Class<?>> source) {
        return source == null ? new ArrayList<Class<?>>() : new ArrayList<Class<?>>(source);
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> source) {
        return source == null ? new HashMap<String, Object>() : new HashMap<String, Object>(source);
    }

    public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {
        private String scene;
        private Class<?> rootType;
        private List<Class<?>> entityClasses = new ArrayList<Class<?>>();
        private SubjectContext subject;
        private Map<String, Object> attributes = new HashMap<String, Object>();
        private CrudDataScope grantedScope;
        private CrudDataScope governanceScope;
        private AccessDecision accessDecision = AccessDecision.ALLOW;

        protected abstract B self();

        public B scene(String scene) {
            this.scene = scene;
            return self();
        }

        public B rootType(Class<?> rootType) {
            this.rootType = rootType;
            return self();
        }

        public B entityClasses(List<Class<?>> entityClasses) {
            this.entityClasses = copyEntityClasses(entityClasses);
            return self();
        }

        public B subject(SubjectContext subject) {
            this.subject = copySubject(subject);
            return self();
        }

        public B attributes(Map<String, Object> attributes) {
            this.attributes = copyAttributes(attributes);
            return self();
        }

        public B grantedScope(CrudDataScope grantedScope) {
            this.grantedScope = grantedScope;
            return self();
        }

        public B governanceScope(CrudDataScope governanceScope) {
            this.governanceScope = governanceScope;
            return self();
        }

        public B accessDecision(AccessDecision accessDecision) {
            this.accessDecision = accessDecision == null ? AccessDecision.ALLOW : accessDecision;
            return self();
        }
    }
}
