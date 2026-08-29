package com.entloom.crud.core.governance.service;

import com.entloom.crud.core.adapter.AttributeAccessEntryResolver;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.permission.CrudPermissionService;
import com.entloom.crud.core.governance.policy.DefaultScenePolicyService;
import com.entloom.crud.core.governance.policy.ScenePolicyRegistry;
import com.entloom.crud.core.governance.policy.ScenePolicyService;
import com.entloom.crud.core.governance.scope.CrudDataScopeContributor;
import com.entloom.crud.core.governance.scope.CrudDataScopeResolver;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.spec.DefaultCrudSpecAttributeResolver;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import java.util.Collection;

/** 测试用治理服务工厂。 */
public final class TestCrudGovernanceServices {
    private TestCrudGovernanceServices() {
    }

    public static DefaultCrudGovernanceService create(
        EntityMetaRegistry entityMetaRegistry,
        SpecValidator specValidator,
        CrudSubjectResolver subjectResolver,
        CrudPermissionService permissionService,
        CrudDataScopeResolver dataScopeResolver,
        Collection<CrudDataScopeContributor> dataScopeContributors,
        CrudGovernanceAuditRecorder auditRecorder
    ) {
        return create(entityMetaRegistry, specValidator, subjectResolver, permissionService,
            dataScopeResolver, dataScopeContributors, auditRecorder, new DefaultCrudSpecAttributeResolver());
    }

    public static DefaultCrudGovernanceService create(
        EntityMetaRegistry entityMetaRegistry,
        SpecValidator specValidator,
        CrudSubjectResolver subjectResolver,
        CrudPermissionService permissionService,
        CrudDataScopeResolver dataScopeResolver,
        Collection<CrudDataScopeContributor> dataScopeContributors,
        CrudGovernanceAuditRecorder auditRecorder,
        CrudSpecAttributeResolver specAttributeResolver
    ) {
        return new DefaultCrudGovernanceService(
            entityMetaRegistry,
            specValidator,
            subjectResolver,
            permissionService,
            dataScopeResolver,
            dataScopeContributors,
            auditRecorder,
            specAttributeResolver,
            new DefaultScenePolicyService(new ScenePolicyRegistry(null), new AttributeAccessEntryResolver())
        );
    }

    public static DefaultCrudGovernanceService create(
        EntityMetaRegistry entityMetaRegistry,
        SpecValidator specValidator,
        CrudSubjectResolver subjectResolver,
        CrudPermissionService permissionService,
        CrudDataScopeResolver dataScopeResolver,
        Collection<CrudDataScopeContributor> dataScopeContributors,
        CrudGovernanceAuditRecorder auditRecorder,
        ScenePolicyService scenePolicyService
    ) {
        return new DefaultCrudGovernanceService(
            entityMetaRegistry,
            specValidator,
            subjectResolver,
            permissionService,
            dataScopeResolver,
            dataScopeContributors,
            auditRecorder,
            new DefaultCrudSpecAttributeResolver(),
            scenePolicyService
        );
    }
}
