package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.core.adapter.AccessEntryResolver;
import com.entloom.crud.core.adapter.AttributePortalResolver;
import com.entloom.crud.core.adapter.PortalResolver;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.spec.BaseSpec;

/** 默认 Scene Policy 准入实现。 */
public final class DefaultScenePolicyService implements ScenePolicyService {
    public static final String PORTAL_ATTRIBUTE_KEY = PortalResolver.ATTRIBUTE_KEY;
    private final ScenePolicyRegistry registry;
    private final AccessEntryResolver accessEntryResolver;
    private final PortalResolver portalResolver;

    public DefaultScenePolicyService(ScenePolicyRegistry registry, AccessEntryResolver accessEntryResolver) {
        this(registry, accessEntryResolver, new AttributePortalResolver());
    }

    public DefaultScenePolicyService(
        ScenePolicyRegistry registry,
        AccessEntryResolver accessEntryResolver,
        PortalResolver portalResolver
    ) {
        this.registry = registry == null ? new ScenePolicyRegistry(null) : registry;
        this.accessEntryResolver = accessEntryResolver;
        this.portalResolver = portalResolver == null ? new AttributePortalResolver() : portalResolver;
    }

    @Override
    public ScenePolicyMatch match(CrudResourceAction action, BaseSpec spec) {
        String accessEntry = accessEntryResolver == null ? AccessEntryResolver.DEFAULT_ENTRY : accessEntryResolver.resolveAccessEntry(spec);
        String portal = portalResolver.resolvePortal(spec);
        if (!requiresPolicy(action)) return ScenePolicyMatch.skipped(accessEntry, portal);
        ScenePolicyKey key = new ScenePolicyKey(accessEntry, action.getResource(), operationKey(action), action.getScene());
        ScenePolicy policy = registry.find(key);
        if (policy == null) return ScenePolicyMatch.rejected(accessEntry, portal, "高风险场景未配置 Scene Policy: " + key);
        if (!policy.getAllowedPortals().isEmpty() && (portal == null || !policy.getAllowedPortals().contains(portal))) {
            return ScenePolicyMatch.rejected(accessEntry, portal, "Scene Policy 不允许当前 portal: " + portal + ", key=" + key);
        }
        return ScenePolicyMatch.matched(accessEntry, portal, policy.getCapability());
    }

    private boolean requiresPolicy(CrudResourceAction action) {
        if (action == null || action.getOperationDomain() == null) return false;
        if (action.getOperationDomain() == CrudOperationDomain.IMPORT || action.getOperationDomain() == CrudOperationDomain.EXPORT) return true;
        return action.getOperationDomain() == CrudOperationDomain.COMMAND && "ACTION".equals(action.getOperation());
    }

    private com.entloom.crud.api.enums.CrudOperationKey operationKey(CrudResourceAction action) {
        return com.entloom.crud.api.enums.CrudOperationKey.of(action.getOperationDomain(), action.getOperation());
    }
}
