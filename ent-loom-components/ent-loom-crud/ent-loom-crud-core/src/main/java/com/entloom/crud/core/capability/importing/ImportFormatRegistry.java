package com.entloom.crud.core.capability.importing;

import java.util.Collection;

/**
 * 导入格式注册表。
 */
public interface ImportFormatRegistry {
    ImportFormatDescriptor getRequired(String format);

    boolean supports(String format);

    Collection<ImportFormatDescriptor> descriptors();
}
