package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.runtime.scene.SceneHandler;

/**
 * 导出场景处理器 SPI。
 */
public interface ExportHandler extends SceneHandler<ExportSpec, ExportResult> {
    ExportResult handle(ExportSpec spec);

    @Override
    default ExportResult handle(ExportSpec spec, com.entloom.crud.core.runtime.scene.SceneDelegate<ExportSpec, ExportResult> delegate) {
        return handle(spec);
    }
}
