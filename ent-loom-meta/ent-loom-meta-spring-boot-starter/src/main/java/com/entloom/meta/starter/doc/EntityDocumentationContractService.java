package com.entloom.meta.starter.doc;

import com.entloom.doc.core.contract.EntityDocumentationExposurePolicy;
import com.entloom.meta.adapter.doc.MetaDocAdapter;
import java.util.Collection;
import java.util.Map;

/**
 * 受显式暴露策略保护的实体文档契约服务。
 *
 * <p>该服务只负责把适配层能力装配给业务项目，不负责认证、登录或 HTTP 路由。
 * 只有应用显式提供 {@link EntityDocumentationExposurePolicy} 后，Starter 才会创建本服务。</p>
 */
public class EntityDocumentationContractService {
    private final MetaDocAdapter metaDocAdapter;
    private final EntityDocumentationExposurePolicy exposurePolicy;

    public EntityDocumentationContractService(
        MetaDocAdapter metaDocAdapter,
        EntityDocumentationExposurePolicy exposurePolicy
    ) {
        if (metaDocAdapter == null) {
            throw new IllegalArgumentException("MetaDocAdapter 不能为空");
        }
        if (exposurePolicy == null) {
            throw new IllegalArgumentException("实体文档暴露策略不能为空");
        }
        this.metaDocAdapter = metaDocAdapter;
        this.exposurePolicy = exposurePolicy;
    }

    /** 生成当前适配器已注册实体的安全公共契约。 */
    public Map<String, Object> build() {
        return metaDocAdapter.buildDocumentationContract(null, exposurePolicy);
    }

    /** 生成指定实体子集的安全公共契约。 */
    public Map<String, Object> build(Collection<Class<?>> entityClasses) {
        return metaDocAdapter.buildDocumentationContract(entityClasses, exposurePolicy);
    }
}
