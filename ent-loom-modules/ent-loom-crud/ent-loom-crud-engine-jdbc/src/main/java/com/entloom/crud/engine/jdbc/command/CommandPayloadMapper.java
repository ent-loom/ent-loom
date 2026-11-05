package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import java.util.Map;

/** 与 Core 共用实体、DTO、局部更新及动态字段的载荷绑定语义。 */
class CommandPayloadMapper {
    private final DefaultCommandPayloadBinder binder = new DefaultCommandPayloadBinder();

    Map<String, Object> toMap(Object payload) {
        return binder.bindFieldMap(payload, null);
    }
}
