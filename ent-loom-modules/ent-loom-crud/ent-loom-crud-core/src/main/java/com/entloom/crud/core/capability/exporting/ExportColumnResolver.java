package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import java.util.List;

/**
 * 导出列解析器。
 */
public interface ExportColumnResolver {
    List<ExportColumn> resolve(ExportSpec spec, EntityMeta meta, RelationGraph relationGraph);
}
