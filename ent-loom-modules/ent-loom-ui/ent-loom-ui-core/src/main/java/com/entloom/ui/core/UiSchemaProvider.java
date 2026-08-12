package com.entloom.ui.core;

/**
 * UI 元数据契约提供器。
 */
public interface UiSchemaProvider {
    /**
     * 根据实体编码解析 UI 契约。
     *
     * @param entityCode 实体编码（例如 trade_order）
     * @return UI 契约
     */
    UiEntityContract resolve(String entityCode);
}
