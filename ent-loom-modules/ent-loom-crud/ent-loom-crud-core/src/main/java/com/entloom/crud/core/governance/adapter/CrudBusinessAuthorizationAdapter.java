package com.entloom.crud.core.governance.adapter;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.entloom.crud.core.governance.policy.CrudOperationMappingRegistry;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.OperationKeySpec;
import java.util.Objects;

/** 将 CRUD 操作映射到业务授权；执行端仍须消费并校验完整授权结果。 */
public final class CrudBusinessAuthorizationAdapter {
    private final EntityMetaRegistry entities;
    private final CrudOperationMappingRegistry mappings;
    private final BusinessAuthorizationService authorization;

    public CrudBusinessAuthorizationAdapter(EntityMetaRegistry entities, CrudOperationMappingRegistry mappings,
                                            BusinessAuthorizationService authorization) {
        this.entities = Objects.requireNonNull(entities, "实体元数据注册表不能为空");
        this.mappings = Objects.requireNonNull(mappings, "CRUD 操作映射不能为空");
        this.authorization = Objects.requireNonNull(authorization, "业务授权服务不能为空");
    }

    /** 新策略绑定是资源级边界，与当前线程是否持有身份无关。 */
    public boolean governs(BaseSpec spec) {
        if (spec == null || spec.getRootType() == null) return false;
        EntityMeta meta = entities.getEntityMeta(spec.getRootType());
        return meta != null && authorization.governs(meta.getResourceDescriptor().getResourceCode());
    }

    public AuthorizationResult authorize(VisitorContext visitor, BaseSpec spec, String targetId) {
        Objects.requireNonNull(visitor, "可信访问者上下文不能为空");
        Objects.requireNonNull(spec, "CRUD Spec 不能为空");
        if (!(spec instanceof OperationKeySpec)) {
            throw new ValidationException("CRUD Spec 没有结构化操作: " + spec.getClass().getName());
        }
        if (spec.getRootType() == null) {
            throw new ValidationException("CRUD 根实体不能为空");
        }
        CrudOperationKey operationKey = ((OperationKeySpec) spec).getOperationKey();
        if (operationKey == null) {
            throw new ValidationException("CRUD 操作不能为空");
        }
        EntityMeta meta = entities.getEntityMeta(spec.getRootType());
        if (meta == null || meta.getEntityType() != spec.getRootType()) {
            throw new PermissionDeniedException("CRUD 根实体没有规范资源元数据");
        }
        BusinessOperation operation = mappings.require(meta.getResourceDescriptor().getResourceCode(),
            operationKey, spec.getScene());
        AuthorizationRequest request = new AuthorizationRequest(visitor, operation, targetId);
        AuthorizationResult result = authorization.authorize(request);
        if (result.getDecision() == AccessDecision.ALLOW) {
            result.requireApplicable(request);
        }
        return result;
    }
}
