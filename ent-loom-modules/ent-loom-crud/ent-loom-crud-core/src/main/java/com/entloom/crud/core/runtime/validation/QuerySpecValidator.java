package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.Objects;

/**
 * Query 规格专用校验器。
 */
public class QuerySpecValidator {
    private final SpecValidator baseValidator;

    public QuerySpecValidator() {
        this(new SpecValidator());
    }

    public QuerySpecValidator(SpecValidator baseValidator) {
        this.baseValidator = Objects.requireNonNull(baseValidator, "baseValidator 不能为空");
    }

    /**
     * 校验查询规格并返回规范化副本。
     */
    public <R> QuerySpec<R> validate(QuerySpec<R> spec) {
        QuerySpec<R> base = baseValidator.validateBase(spec);
        QueryOperation op = base.getOp();
        if (op == null) {
            throw new ValidationException("查询操作不能为空");
        }
        if (base.getResultType() == null) {
            throw new ValidationException("查询结果类型(resultType)不能为空");
        }
        QuerySpec.Builder<R> builder = base.toBuilder();
        switch (op) {
            case PAGE:
                PageRequest page = base.getPage();
                int pageNo = page == null || page.getPage() <= 0 ? SpecValidator.DEFAULT_PAGE : page.getPage();
                int pageLimit = page == null || page.getLimit() <= 0 ? SpecValidator.DEFAULT_PAGE_LIMIT : page.getLimit();
                if (pageLimit > SpecValidator.DEFAULT_MAX_LIMIT) {
                    throw new ValidationException("返回条数超过最大限制 " + SpecValidator.DEFAULT_MAX_LIMIT);
                }
                builder.page(new PageRequest(pageNo, pageLimit));
                break;
            case LIST:
                int listLimit = base.getLimit() == null || base.getLimit() <= 0
                    ? SpecValidator.DEFAULT_LIST_LIMIT
                    : base.getLimit();
                if (listLimit > SpecValidator.DEFAULT_MAX_LIMIT) {
                    throw new ValidationException("返回条数超过最大限制 " + SpecValidator.DEFAULT_MAX_LIMIT);
                }
                builder.limit(listLimit);
                break;
            case FIND_ONE:
                if (base.getPage() != null) {
                    throw new ValidationException("findOne 不支持分页参数");
                }
                if (base.getLimit() != null) {
                    throw new ValidationException("findOne 不支持 limit 参数");
                }
                builder.limit(2);
                break;
            case DETAIL:
                if (base.getPage() != null) {
                    throw new ValidationException("detail 不支持分页参数");
                }
                builder.limit(2);
                break;
            default:
                break;
        }
        return builder.build();
    }
}
