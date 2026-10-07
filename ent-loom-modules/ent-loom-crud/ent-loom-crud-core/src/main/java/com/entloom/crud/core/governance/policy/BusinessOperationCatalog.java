package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 启动期编译并冻结的业务操作目录；此目录只声明操作存在，不授予权限。 */
public final class BusinessOperationCatalog {
    private final Map<BusinessOperation, BusinessOperationDeclaration> declarations;

    public BusinessOperationCatalog(
        Collection<ResourceDescriptor> resources,
        Collection<BusinessOperationDeclaration> source
    ) {
        Objects.requireNonNull(resources, "资源描述不能为空");
        Objects.requireNonNull(source, "操作声明不能为空");
        Map<String, ResourceDescriptor> knownResources = new LinkedHashMap<String, ResourceDescriptor>();
        for (ResourceDescriptor resource : resources) {
            if (resource == null) {
                throw new ValidationException("资源描述不能为空");
            }
            ResourceDescriptor previous = knownResources.put(resource.getResourceCode(), resource);
            if (previous != null) {
                throw new ValidationException("资源 code 重复: " + resource.getResourceCode());
            }
        }
        Map<BusinessOperation, BusinessOperationDeclaration> compiled =
            new LinkedHashMap<BusinessOperation, BusinessOperationDeclaration>();
        for (BusinessOperationDeclaration declaration : source) {
            if (declaration == null) {
                throw new ValidationException("业务操作声明不能为空");
            }
            BusinessOperation operation = declaration.getOperation();
            if (!knownResources.containsKey(operation.getResourceCode())) {
                throw new ValidationException("业务操作引用未知资源: " + operation + "，来源: " + declaration.getSource());
            }
            BusinessOperationDeclaration previous = compiled.put(operation, declaration);
            if (previous != null) {
                throw new ValidationException("业务操作重复声明: " + operation + "，来源: "
                    + previous.getSource() + " / " + declaration.getSource());
            }
        }
        this.declarations = Collections.unmodifiableMap(compiled);
    }

    /** 未注册资源或操作默认拒绝，不能由入口名称、身份或页面声明自动推断授权。 */
    public BusinessOperationDeclaration require(BusinessOperation operation) {
        Objects.requireNonNull(operation, "业务操作不能为空");
        BusinessOperationDeclaration declaration = declarations.get(operation);
        if (declaration == null) {
            throw new PermissionDeniedException("业务操作未注册: " + operation);
        }
        return declaration;
    }

    public Map<BusinessOperation, BusinessOperationDeclaration> snapshot() {
        return declarations;
    }
}
