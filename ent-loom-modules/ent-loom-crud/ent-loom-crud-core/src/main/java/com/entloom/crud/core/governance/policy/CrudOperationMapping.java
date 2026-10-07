package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Objects;

/** 项目声明的 CRUD 操作到业务操作映射；ACTION 以 scene 区分动作。 */
public final class CrudOperationMapping {
    private final CrudOperationKey operationKey;
    private final String scene;
    private final BusinessOperation businessOperation;
    private final String source;

    public CrudOperationMapping(CrudOperationKey operationKey, String scene,
                                BusinessOperation businessOperation, String source) {
        this.operationKey = Objects.requireNonNull(operationKey, "CRUD 操作不能为空");
        this.businessOperation = Objects.requireNonNull(businessOperation, "业务操作不能为空");
        this.scene = RouteKeyFactory.normalizeScene(scene);
        boolean action = CrudOperationKey.of(CommandOperation.ACTION).equals(operationKey);
        if (action && this.scene.isEmpty()) {
            throw new ValidationException("ACTION 映射必须声明 scene: " + businessOperation);
        }
        if (!action && !this.scene.isEmpty()) {
            throw new ValidationException("非 ACTION 映射不能声明 scene: " + operationKey);
        }
        if (source == null || source.trim().isEmpty()) {
            throw new ValidationException("CRUD 映射来源不能为空: " + businessOperation);
        }
        this.source = source.trim();
    }

    public CrudOperationKey getOperationKey() { return operationKey; }
    public String getScene() { return scene; }
    public BusinessOperation getBusinessOperation() { return businessOperation; }
    public String getSource() { return source; }
}
