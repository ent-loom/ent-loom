package com.entloom.crud.core.capability.exporting;

import java.util.Collection;

/**
 * 导出格式注册表。
 */
public interface ExportFormatRegistry {
    ExportFormatDescriptor getRequired(String format);

    boolean supports(String format);

    Collection<ExportFormatDescriptor> descriptors();
}
