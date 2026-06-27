package com.entloom.crud.core.capability.importing;

import com.entloom.crud.core.runtime.scene.SceneHandler;

/**
 * 导入场景处理器 SPI。
 */
public interface ImportHandler extends SceneHandler<ImportSpec, ImportResult> {
    ImportResult handle(ImportSpec spec);

    @Override
    default ImportResult handle(ImportSpec spec, com.entloom.crud.core.runtime.scene.SceneDelegate<ImportSpec, ImportResult> delegate) {
        return handle(spec);
    }
}
