package com.entloom.crud.core.governance.service;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.spec.BaseSpec;

/**
 * 治理主链产物。
 */
public class CrudGovernanceResult<S extends BaseSpec> {
    /** 请求主体。 */
    private final SubjectContext subject;
    /** 命令动作实现。 */
    private final CrudResourceAction action;
    /** 访问决策。 */
    private final AccessDecision accessDecision;
    /** 已授予的数据范围。 */
    private final CrudDataScope grantedScope;
    /** 治理计算出的数据范围。 */
    private final CrudDataScope governanceScope;
    /** 开始时间戳（毫秒）。 */
    private final long startTimeMs;
    /** 治理后的有效执行 spec。 */
    private final S effectiveSpec;

    public CrudGovernanceResult(
        SubjectContext subject,
        CrudResourceAction action,
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope,
        long startTimeMs,
        S effectiveSpec
    ) {
        this.subject = subject;
        this.action = action;
        this.accessDecision = accessDecision == null ? AccessDecision.ALLOW : accessDecision;
        this.grantedScope = grantedScope;
        this.governanceScope = governanceScope;
        this.startTimeMs = startTimeMs;
        this.effectiveSpec = effectiveSpec;
    }

    public SubjectContext getSubject() {
        return subject;
    }

    public CrudResourceAction getAction() {
        return action;
    }

    public AccessDecision getAccessDecision() {
        return accessDecision;
    }

    public CrudDataScope getGrantedScope() {
        return grantedScope;
    }

    public CrudDataScope getGovernanceScope() {
        return governanceScope;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public S getEffectiveSpec() {
        return effectiveSpec;
    }
}
