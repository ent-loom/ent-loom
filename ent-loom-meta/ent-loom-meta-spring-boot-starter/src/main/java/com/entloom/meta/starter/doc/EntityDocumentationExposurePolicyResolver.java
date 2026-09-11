package com.entloom.meta.starter.doc;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;

/**
 * 按当前请求主体解析实体文档暴露策略。
 *
 * <p>业务项目负责把认证主体映射到实体白名单和字段策略。返回
 * {@link EntityDocumentationExposurePolicy#denyAll()} 可明确拒绝本次文档访问。</p>
 */
@FunctionalInterface
public interface EntityDocumentationExposurePolicyResolver {
    /**
     * 为当前主体解析一次性文档暴露策略。
     *
     * @param subject 已由 CRUD 主体解析链确认的当前主体
     * @return 本次访问的实体和字段暴露策略，不得为 {@code null}
     */
    EntityDocumentationExposurePolicy resolve(SubjectContext subject);
}
