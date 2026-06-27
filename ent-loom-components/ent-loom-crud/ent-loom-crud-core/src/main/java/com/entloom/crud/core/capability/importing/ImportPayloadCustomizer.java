package com.entloom.crud.core.capability.importing;

/**
 * 导入载荷定制器。
 *
 * <p>该扩展点在治理完成后执行，只允许修改 payload；rootType、scene、subject、
 * attributes、operation 和 governance 字段必须保持不变。</p>
 */
public interface ImportPayloadCustomizer {
    boolean supports(ImportSpec spec);

    ImportSpec customize(ImportSpec spec);
}
