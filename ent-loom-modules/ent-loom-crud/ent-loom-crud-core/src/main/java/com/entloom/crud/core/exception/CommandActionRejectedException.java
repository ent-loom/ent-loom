package com.entloom.crud.core.exception;

import com.entloom.crud.api.enums.CrudErrorCode;

/** 动作返回失败结果时统一抛出，触发事务回滚并保留业务码。 */
public final class CommandActionRejectedException extends CrudException {
    private final String code;

    public CommandActionRejectedException(String code, String message) {
        super(CrudErrorCode.BUSINESS_REJECTED, message == null ? "业务动作被拒绝" : message);
        this.code = code == null || code.trim().isEmpty() ? CrudErrorCode.BUSINESS_REJECTED.name() : code;
        withReason(this.code);
    }

    public String getCode() { return code; }
}
