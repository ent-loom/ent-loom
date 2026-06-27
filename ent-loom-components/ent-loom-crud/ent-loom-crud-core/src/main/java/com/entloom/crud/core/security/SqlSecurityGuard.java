package com.entloom.crud.core.security;

import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import java.util.List;

/**
 * SQL 安全守卫接口。
 */
public interface SqlSecurityGuard {
    /**
     * 编译前安全校验。
     *
     * @param spec 协议对象
     */
    void beforeCompile(BaseSpec spec);

    /**
     * 执行前安全校验。
     *
     * @param sql SQL 模板
     * @param args 参数列表
     * @param context 执行上下文
     */
    void beforeExecute(String sql, List<Object> args, CrudExecutionContext context);
}
