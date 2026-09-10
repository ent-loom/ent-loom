package com.entloom.doc.core.contract;

import com.entloom.doc.core.model.DocEntityModel;
import com.entloom.doc.core.model.DocFieldModel;

/**
 * 实体文档公共投影的暴露策略。
 *
 * <p>策略由集成层绑定实体白名单和当前主体授权；本接口不解释登录、权限或数据范围。
 * 未提供显式策略的公共入口必须拒绝生成文档。</p>
 */
public interface EntityDocumentationExposurePolicy {

    /** 判断实体是否可以进入公共实体文档。 */
    boolean isEntityExposed(DocEntityModel entity);

    /** 判断字段是否可以进入指定实体的公共实体文档。 */
    default boolean isFieldExposed(DocEntityModel entity, DocFieldModel field) {
        return true;
    }

    /** 仅用于维护者或测试显式选择的全量策略，不代表生产默认配置。 */
    static EntityDocumentationExposurePolicy allowAll() {
        return new EntityDocumentationExposurePolicy() {
            @Override
            public boolean isEntityExposed(DocEntityModel entity) {
                return true;
            }
        };
    }

    /** 默认关闭策略，确保未配置白名单时不会公开实体。 */
    static EntityDocumentationExposurePolicy denyAll() {
        return new EntityDocumentationExposurePolicy() {
            @Override
            public boolean isEntityExposed(DocEntityModel entity) {
                return false;
            }
        };
    }
}
