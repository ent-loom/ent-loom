package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 命令 payload 统一映射器。
 */
class CommandPayloadMapper {
    /**
     * 将命令载荷转换为字段映射。
     */
    Map<String, Object> toMap(Object payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        if (payload instanceof CrudRecord) {
            return new HashMap<String, Object>(((CrudRecord) payload).asMap());
        }
        if (payload instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) payload;
            Map<String, Object> result = new HashMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        throw new ValidationException(
            "命令载荷必须是 Map 或 CrudRecord，当前类型: " + payload.getClass().getName()
        );
    }
}
