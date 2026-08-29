package com.entloom.crud.core.governance.model;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Collections;
import lombok.Getter;

/**
 * CRUD 资源动作标识。
 */
@Getter
public class CrudResourceAction {
    /** 资源完整描述。 */
    private final ResourceDescriptor resourceDescriptor;
    /** 资源标识。 */
    private final String resource;
    /** 动作标识。 */
    private final String action;
    /** 操作域。 */
    private final CrudOperationDomain operationDomain;
    /** 域内操作。 */
    private final String operation;
    /** 场景标识。 */
    private final String scene;
    /** 业务权限码。 */
    private final String capability;
    /** 可信访问入口。 */
    private final String accessEntry;
    /** 可信入口形态。 */
    private final String portal;
    /** 是否命中 Scene Policy。 */
    private final boolean policyMatched;
    /** Scene Policy 拒绝详情。 */
    private final String policyRejectionReason;

    public CrudResourceAction(ResourceDescriptor resourceDescriptor, String action, String scene) {
        this(resourceDescriptor, null, action, scene, null);
    }

    public CrudResourceAction(ResourceDescriptor resourceDescriptor, CrudOperationKey operationKey, String scene, String capability) {
        this(resourceDescriptor, operationKey, operationKey == null ? null : operationKey.getAction(), scene, capability);
    }

    private CrudResourceAction(
        ResourceDescriptor resourceDescriptor,
        CrudOperationKey operationKey,
        String action,
        String scene,
        String capability
    ) {
        if (resourceDescriptor == null) {
            throw new IllegalArgumentException("resourceDescriptor 不能为空");
        }
        this.resourceDescriptor = resourceDescriptor;
        this.resource = resourceDescriptor.getResourceCode();
        this.action = action;
        this.operationDomain = operationKey == null ? null : operationKey.getDomain();
        this.operation = operationKey == null ? action : operationKey.getOperation();
        this.scene = scene;
        this.capability = capability;
        this.accessEntry = null;
        this.portal = null;
        this.policyMatched = false;
        this.policyRejectionReason = null;
    }

    public CrudResourceAction(
        ResourceDescriptor resourceDescriptor,
        CrudOperationKey operationKey,
        String scene,
        String capability,
        String accessEntry,
        String portal,
        boolean policyMatched
    ) {
        this(resourceDescriptor, operationKey, scene, capability, accessEntry, portal, policyMatched, null);
    }

    public CrudResourceAction(
        ResourceDescriptor resourceDescriptor,
        CrudOperationKey operationKey,
        String scene,
        String capability,
        String accessEntry,
        String portal,
        boolean policyMatched,
        String policyRejectionReason
    ) {
        if (resourceDescriptor == null) throw new IllegalArgumentException("resourceDescriptor 不能为空");
        this.resourceDescriptor = resourceDescriptor;
        this.resource = resourceDescriptor.getResourceCode();
        this.action = operationKey == null ? null : operationKey.getAction();
        this.operationDomain = operationKey == null ? null : operationKey.getDomain();
        this.operation = operationKey == null ? null : operationKey.getOperation();
        this.scene = scene;
        this.capability = capability;
        this.accessEntry = accessEntry;
        this.portal = portal;
        this.policyMatched = policyMatched;
        this.policyRejectionReason = policyRejectionReason;
    }

    public CrudResourceAction(String resource, String action, String scene) {
        this(
            new ResourceDescriptor(null, resource, null, Collections.<String>emptyList()),
            action,
            scene
        );
    }

    public String getOwnerService() {
        return resourceDescriptor.getOwnerService();
    }
}
