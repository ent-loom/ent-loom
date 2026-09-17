package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.Map;

/** 将可信校验后的局部更新转换为结构化写命令。 */
public final class UpdatePatchWriteCommandFactory {
    private UpdatePatchWriteCommandFactory() {
    }

    public static <T> WriteCommand<Map<String, Object>> create(UpdatePatch<T> patch, EntityMeta meta) {
        if (patch == null || meta == null) {
            throw new ValidationException("UpdatePatch 和 EntityMeta 不能为空");
        }
        if (patch.getEntityType() == null || !patch.getEntityType().equals(meta.getEntityType())) {
            throw new ValidationException("UpdatePatch 实体类型与元数据不一致");
        }
        NormalizedUpdatePatch normalized = NormalizedUpdatePatch.from(patch, meta);
        return new WriteCommand<Map<String, Object>>(
            CommandOperation.UPDATE,
            normalized.getId(),
            normalized.getChanges()
        );
    }
}
