package com.entloom.crud.core.governance.permission;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;

/**
 * 默认规则型权限条目。
 */
public class CrudPermissionRule {
    /** 资源标识。 */
    private final String resource;
    /** 动作标识。 */
    private final String action;
    /** 场景标识。 */
    private final String scene;
    @Getter
    /** 访问决策。 */
    private final AccessDecision decision;
    /** 主体标识集合。 */
    private final Set<String> subjectIds;
    /** 租户标识集合。 */
    private final Set<String> tenantIds;
    /** 组织标识集合。 */
    private final Set<String> orgIds;

    public CrudPermissionRule(
        String resource,
        String action,
        String scene,
        AccessDecision decision,
        Set<String> subjectIds,
        Set<String> tenantIds,
        Set<String> orgIds
    ) {
        this.resource = normalizePattern(resource);
        this.action = normalizePattern(action);
        this.scene = normalizePattern(scene);
        this.decision = decision == null ? AccessDecision.DENY : decision;
        this.subjectIds = immutableSet(subjectIds);
        this.tenantIds = immutableSet(tenantIds);
        this.orgIds = immutableSet(orgIds);
    }

    /**
     * 判断规则是否匹配当前资源和主体。
     */
    public boolean matches(CrudResourceAction action, SubjectContext subject) {
        if (!matchesPattern(resource, action == null ? null : action.getResource())) {
            return false;
        }
        if (!matchesPattern(this.action, action == null ? null : action.getAction())) {
            return false;
        }
        if (!matchesPattern(scene, action == null ? null : action.getScene())) {
            return false;
        }
        if (!matchesValue(subjectIds, subject == null ? null : subject.getSubjectId())) {
            return false;
        }
        if (!matchesValue(tenantIds, subject == null ? null : subject.getTenantId())) {
            return false;
        }
        return matchesValue(orgIds, subject == null ? null : subject.getOrgId());
    }

    private boolean matchesValue(Set<String> candidates, String actual) {
        return candidates.isEmpty() || candidates.contains(actual);
    }

    private boolean matchesPattern(String pattern, String actual) {
        return "*".equals(pattern) || pattern.equals(actual);
    }

    private String normalizePattern(String pattern) {
        String value = pattern == null ? "" : pattern.trim();
        return value.isEmpty() ? "*" : value;
    }

    private Set<String> immutableSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<String>(values));
    }
}
