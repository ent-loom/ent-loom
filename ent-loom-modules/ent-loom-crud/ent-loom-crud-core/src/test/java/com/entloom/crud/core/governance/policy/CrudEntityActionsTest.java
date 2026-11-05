package com.entloom.crud.core.governance.policy;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrudEntityActionsTest {
    private static final CrudEntityActions CRUD = new CrudEntityActions(List.of("view", "create", "update", "delete"));

    @Test
    void 缺省使用项目动作集而框架缺省只读() {
        assertEquals(Set.of("view"), CrudEntityActions.readOnly().resolve(Default.class));
        assertEquals(Set.of("view", "create", "update", "delete"), CRUD.resolve(Default.class));
        assertEquals(Set.of(), CRUD.resolve(Unmanaged.class));
    }

    @Test
    void 增删基于默认集且保持稳定顺序() {
        assertEquals(List.of("view", "create", "update", "batch-update"), List.copyOf(CRUD.resolve(Adjusted.class)));
    }

    @Test
    void 显式动作完整替换且空数组关闭全部动作() {
        assertEquals(Set.of("view", "cancel"), CRUD.resolve(Workflow.class));
        assertEquals(Set.of(), CRUD.resolve(Closed.class));
    }

    @Test
    void 冲突重复及不合法增删均启动失败() {
        for (Class<?> type : List.of(Mixed.class, EmptyMixed.class, Both.class, Missing.class, Existing.class,
            Duplicate.class, Blank.class, SentinelMixed.class)) {
            assertThrows(ValidationException.class, () -> CRUD.resolve(type), type.getSimpleName());
        }
        assertThrows(ValidationException.class, () -> new CrudEntityActions(List.of("view", "view")));
        assertThrows(ValidationException.class, () -> new CrudEntityActions(List.of("*")));
        assertThrows(ValidationException.class, () -> new CrudEntityActions(null));
    }

    @EntCrudEntity static class Default {}
    static class Unmanaged {}
    @EntCrudEntity(addActions = "batch-update", removeActions = "delete") static class Adjusted {}
    @EntCrudEntity(actions = {"view", "cancel"}) static class Workflow {}
    @EntCrudEntity(actions = {}) static class Closed {}
    @EntCrudEntity(actions = "view", addActions = "cancel") static class Mixed {}
    @EntCrudEntity(actions = {}, removeActions = "delete") static class EmptyMixed {}
    @EntCrudEntity(addActions = "cancel", removeActions = "cancel") static class Both {}
    @EntCrudEntity(removeActions = "cancel") static class Missing {}
    @EntCrudEntity(addActions = "view") static class Existing {}
    @EntCrudEntity(addActions = {"cancel", "cancel"}) static class Duplicate {}
    @EntCrudEntity(actions = " ") static class Blank {}
    @EntCrudEntity(actions = {EntCrudEntity.INHERIT_ACTIONS, "view"}) static class SentinelMixed {}
}
