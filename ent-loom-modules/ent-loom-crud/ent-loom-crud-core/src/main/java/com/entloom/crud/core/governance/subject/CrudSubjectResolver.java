package com.entloom.crud.core.governance.subject;

import com.entloom.crud.api.model.SubjectContext;

/**
 * 主体解析器。
 */
public interface CrudSubjectResolver {
    /**
     * 解析当前请求主体，失败时抛异常。
     *
     * @return 主体
     */
    SubjectContext resolveOrThrow();
}
