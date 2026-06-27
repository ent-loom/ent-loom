package com.entloom.crud.core.governance.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 组合审计实现。
 */
public class CompositeCrudGovernanceAuditRecorder implements CrudGovernanceAuditRecorder {
    /** 委托记录器列表。 */
    private final List<CrudGovernanceAuditRecorder> delegates;

    public CompositeCrudGovernanceAuditRecorder(Collection<CrudGovernanceAuditRecorder> delegates) {
        this.delegates = delegates == null
            ? new ArrayList<CrudGovernanceAuditRecorder>()
            : new ArrayList<CrudGovernanceAuditRecorder>(delegates);
    }

    @Override
    public void record(CrudGovernanceAuditEvent event) {
        for (CrudGovernanceAuditRecorder delegate : delegates) {
            if (delegate != null) {
                delegate.record(event);
            }
        }
    }
}
