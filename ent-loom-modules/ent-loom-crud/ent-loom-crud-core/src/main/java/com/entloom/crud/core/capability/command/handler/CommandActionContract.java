package com.entloom.crud.core.capability.command.handler;

import com.entloom.crud.core.exception.ValidationException;
import lombok.Getter;

/**
 * ACTION scene 命令契约，定义请求与响应数据类型。
 */
@Getter
public final class CommandActionContract {
    /** 请求类型。 */
    private final Class<?> requestType;
    /** 响应类型。 */
    private final Class<?> responseType;

    private final java.util.List<com.entloom.crud.core.capability.command.engine.RelatedDaoAccess> relatedAccess;

    public CommandActionContract(Class<?> requestType, Class<?> responseType,
        java.util.List<com.entloom.crud.core.capability.command.engine.RelatedDaoAccess> relatedAccess) {
        if (requestType == null) {
            throw new ValidationException("requestType 不能为空");
        }
        if (responseType == null) {
            throw new ValidationException("responseType 不能为空");
        }
        java.util.Set<Class<?>> targets = new java.util.HashSet<>();
        for (com.entloom.crud.core.capability.command.engine.RelatedDaoAccess access : relatedAccess) {
            if (access == null || !targets.add(access.getEntityType())) throw new ValidationException("关联 DAO 声明为空或实体重复");
        }
        this.relatedAccess = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(relatedAccess));
        this.requestType = requestType;
        this.responseType = responseType;
    }
}
