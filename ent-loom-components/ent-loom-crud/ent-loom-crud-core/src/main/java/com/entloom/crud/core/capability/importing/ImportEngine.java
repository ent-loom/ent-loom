package com.entloom.crud.core.capability.importing;

import com.entloom.crud.core.runtime.engine.EngineCapability;

/**
 * 导入引擎 SPI。
 */
public interface ImportEngine {
    default EngineCapability capability() {
        return EngineCapability.unknown(getClass().getName());
    }

    ImportResult execute(ImportSpec spec);
}
