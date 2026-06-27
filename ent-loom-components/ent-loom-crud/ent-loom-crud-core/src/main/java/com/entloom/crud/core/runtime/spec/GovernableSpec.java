package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.Map;

/**
 * 可由统一治理链复制并回填治理结果的 spec。
 *
 * @param <S> 具体 spec 类型
 */
public interface GovernableSpec<S extends BaseSpec> {
    S withSubject(SubjectContext subject);

    S withAttributes(Map<String, Object> attributes);

    S withGovernance(
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope
    );
}
