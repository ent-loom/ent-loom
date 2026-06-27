package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.core.runtime.meta.RelationEdge;
import java.util.List;

/**
 * 特殊本地或远程关系的批量加载 SPI。
 */
public interface RelationLoader {
    /**
     * 是否支持当前关系边。
     */
    boolean supports(RelationEdge edge);

    /**
     * 批量加载关系目标对象。
     */
    List<Object> load(RelationLoadRequest request);
}
