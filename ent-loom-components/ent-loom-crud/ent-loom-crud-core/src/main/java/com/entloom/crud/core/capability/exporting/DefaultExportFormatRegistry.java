package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 默认导出格式注册表。
 */
public class DefaultExportFormatRegistry implements ExportFormatRegistry {
    private final Map<String, ExportFormatDescriptor> descriptors;

    public DefaultExportFormatRegistry(Collection<ExportFormatDescriptor> descriptors) {
        Map<String, ExportFormatDescriptor> map = new LinkedHashMap<String, ExportFormatDescriptor>();
        if (descriptors != null) {
            for (ExportFormatDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    continue;
                }
                String key = normalize(descriptor.getFormat());
                if (key == null) {
                    throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "Export format 不能为空");
                }
                if (map.containsKey(key)) {
                    throw new CrudException(CrudErrorCode.ROUTE_AMBIGUOUS, "Export format 重复注册: " + key);
                }
                map.put(key, descriptor);
            }
        }
        this.descriptors = Collections.unmodifiableMap(map);
    }

    @Override
    public ExportFormatDescriptor getRequired(String format) {
        String key = normalize(format);
        ExportFormatDescriptor descriptor = key == null ? null : descriptors.get(key);
        if (descriptor == null) {
            throw new CrudException(CrudErrorCode.UNSUPPORTED_FORMAT, "不支持的导出格式: " + format);
        }
        return descriptor;
    }

    @Override
    public boolean supports(String format) {
        String key = normalize(format);
        return key != null && descriptors.containsKey(key);
    }

    @Override
    public Collection<ExportFormatDescriptor> descriptors() {
        return new ArrayList<ExportFormatDescriptor>(descriptors.values());
    }

    private static String normalize(String format) {
        if (format == null || format.trim().isEmpty()) {
            return null;
        }
        return format.trim().toLowerCase(Locale.ROOT);
    }
}
