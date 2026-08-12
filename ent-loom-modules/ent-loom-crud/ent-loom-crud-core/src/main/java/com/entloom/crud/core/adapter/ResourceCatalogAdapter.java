package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import java.util.Collection;
import java.util.Collections;

/**
 * 业务资源目录到 ent-loom 元数据的适配器。
 */
public interface ResourceCatalogAdapter {
    /**
     * 返回统一 CRUD 运行期模型。
     *
     * @return CRUD 运行期模型
     */
    CrudRuntimeModel runtimeModel();

    /**
     * 返回适配器在解析和合并阶段产生的诊断。
     *
     * @return 诊断集合
     */
    default Collection<MetaDiagnostic> diagnostics() {
        return Collections.emptyList();
    }
}
