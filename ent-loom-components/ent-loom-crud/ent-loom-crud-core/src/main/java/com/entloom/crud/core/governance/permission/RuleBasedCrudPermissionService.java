package com.entloom.crud.core.governance.permission;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 基于规则集的默认接口权限实现。
 */
public class RuleBasedCrudPermissionService implements CrudPermissionService {
    /** 权限规则列表。 */
    private final List<CrudPermissionRule> rules;

    public RuleBasedCrudPermissionService() {
        this(new ArrayList<CrudPermissionRule>());
    }

    public RuleBasedCrudPermissionService(Collection<CrudPermissionRule> rules) {
        this.rules = rules == null ? new ArrayList<CrudPermissionRule>() : new ArrayList<CrudPermissionRule>(rules);
    }

    /**
     * 根据规则判定当前访问决策。
     */
    @Override
    public AccessDecision decide(CrudResourceAction action, SubjectContext subject, BaseSpec spec) {
        for (CrudPermissionRule rule : rules) {
            if (rule.matches(action, subject)) {
                return rule.getDecision();
            }
        }
        throw new PermissionDeniedException("未找到权限规则");
    }
}
