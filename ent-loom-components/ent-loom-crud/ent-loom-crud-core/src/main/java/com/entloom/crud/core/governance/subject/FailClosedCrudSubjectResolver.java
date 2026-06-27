package com.entloom.crud.core.governance.subject;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.ValidationException;

/**
 * 默认 fail-closed 主体解析器。
 */
public class FailClosedCrudSubjectResolver implements CrudSubjectResolver {
    @Override
    public SubjectContext resolveOrThrow() {
        throw new ValidationException("操作主体(subject)不能为空");
    }
}
