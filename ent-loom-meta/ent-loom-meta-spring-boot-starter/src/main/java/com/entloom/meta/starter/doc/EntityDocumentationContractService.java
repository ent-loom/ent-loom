package com.entloom.meta.starter.doc;

import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.subject.CrudSubjectResolver;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import java.util.Collection;
import java.util.Map;

/**
 * 受显式暴露策略保护的实体文档契约服务。
 *
 * <p>该服务只负责把适配层能力装配给业务项目，不负责认证、登录或 HTTP 路由。
 * 它复用 CRUD 主体解析链，并要求业务按主体解析本次访问的暴露策略。</p>
 */
public class EntityDocumentationContractService {
    private final MetaDocAdapter metaDocAdapter;
    private final CrudSubjectResolver subjectResolver;
    private final EntityDocumentationExposurePolicyResolver exposurePolicyResolver;

    public EntityDocumentationContractService(
        MetaDocAdapter metaDocAdapter,
        CrudSubjectResolver subjectResolver,
        EntityDocumentationExposurePolicyResolver exposurePolicyResolver
    ) {
        if (metaDocAdapter == null) {
            throw new IllegalArgumentException("MetaDocAdapter 不能为空");
        }
        if (subjectResolver == null) {
            throw new IllegalArgumentException("CRUD 主体解析器不能为空");
        }
        if (exposurePolicyResolver == null) {
            throw new IllegalArgumentException("实体文档暴露策略解析器不能为空");
        }
        this.metaDocAdapter = metaDocAdapter;
        this.subjectResolver = subjectResolver;
        this.exposurePolicyResolver = exposurePolicyResolver;
    }

    /** 生成当前适配器已注册实体的安全公共契约。 */
    public Map<String, Object> build() {
        return build(null);
    }

    /** 生成指定实体子集的安全公共契约。 */
    public Map<String, Object> build(Collection<Class<?>> entityClasses) {
        SubjectContext subject = subjectResolver.resolveOrThrow();
        if (subject == null) {
            throw new IllegalStateException("当前主体不能为空");
        }
        EntityDocumentationExposurePolicy exposurePolicy = exposurePolicyResolver.resolve(subject);
        if (exposurePolicy == null) {
            throw new IllegalStateException("实体文档暴露策略解析结果不能为空");
        }
        return metaDocAdapter.buildDocumentationContract(entityClasses, exposurePolicy);
    }
}
