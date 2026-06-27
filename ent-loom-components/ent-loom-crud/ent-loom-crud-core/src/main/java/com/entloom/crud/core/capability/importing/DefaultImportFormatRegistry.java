package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 默认导入格式注册表。
 */
public class DefaultImportFormatRegistry implements ImportFormatRegistry {
    private final Map<String, ImportFormatDescriptor> descriptors;

    public DefaultImportFormatRegistry(Collection<ImportFormatDescriptor> descriptors) {
        Map<String, ImportFormatDescriptor> map = new LinkedHashMap<String, ImportFormatDescriptor>();
        if (descriptors != null) {
            for (ImportFormatDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    continue;
                }
                String key = normalize(descriptor.getFormat());
                if (key == null) {
                    throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "Import format 不能为空");
                }
                if (map.containsKey(key)) {
                    throw new CrudException(CrudErrorCode.ROUTE_AMBIGUOUS, "Import format 重复注册: " + key);
                }
                map.put(key, descriptor);
            }
        }
        this.descriptors = Collections.unmodifiableMap(map);
    }

    @Override
    public ImportFormatDescriptor getRequired(String format) {
        String key = normalize(format);
        ImportFormatDescriptor descriptor = key == null ? null : descriptors.get(key);
        if (descriptor == null) {
            throw new CrudException(CrudErrorCode.UNSUPPORTED_FORMAT, "不支持的导入格式: " + format);
        }
        return descriptor;
    }

    @Override
    public boolean supports(String format) {
        String key = normalize(format);
        return key != null && descriptors.containsKey(key);
    }

    @Override
    public Collection<ImportFormatDescriptor> descriptors() {
        return new ArrayList<ImportFormatDescriptor>(descriptors.values());
    }

    private static String normalize(String format) {
        if (format == null || format.trim().isEmpty()) {
            return null;
        }
        return format.trim().toLowerCase(Locale.ROOT);
    }
}
