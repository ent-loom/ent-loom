package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.runtime.engine.EngineCapability;

/**
 * 导出引擎 SPI。
 */
public interface ExportEngine {
    default EngineCapability capability() {
        return EngineCapability.unknown(getClass().getName());
    }

    ExportResult execute(ExportSpec spec);
}
