package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CommandActionSceneResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpExcludedActionsValidationTest {
    private final EntityMetaRegistry entities = mock(EntityMetaRegistry.class);
    private final CommandActionSceneResolver resolver = mock(CommandActionSceneResolver.class);

    private void validate(Class<?> type) {
        EntityMeta meta = mock(EntityMeta.class);
        doReturn(type).when(meta).getEntityType();
        when(entities.getEntityMetas()).thenReturn(List.of(meta));
        new CrudEntityDeclarations().validateHttpExcludedActions(entities, resolver);
    }

    @Test
    void 已注册规范动作通过校验() {
        when(resolver.canonicalizeActionScene(Valid.class, List.of(Valid.class), "create")).thenReturn("create");
        when(resolver.resolveActionContract(Valid.class, List.of(Valid.class), "create"))
            .thenReturn(new CommandActionContract(Void.class, Long.class, List.of()));
        assertDoesNotThrow(() -> validate(Valid.class));
    }

    static List<Class<?>> invalidTypes() { return List.of(Blank.class, Duplicate.class, Typo.class, Alias.class); }

    @ParameterizedTest
    @MethodSource("invalidTypes")
    void 空值重复未注册与别名声明均拒绝(Class<?> type) {
        when(resolver.canonicalizeActionScene(any(), anyList(), anyString())).thenReturn("create");
        when(resolver.resolveActionContract(any(), anyList(), anyString()))
            .thenReturn(new CommandActionContract(Void.class, Long.class, List.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(type));
    }

    @Test
    void 动作解析异常转换为明确配置错误() {
        when(resolver.canonicalizeActionScene(any(), anyList(), anyString()))
            .thenThrow(new IllegalStateException("未注册路由"));
        var error = assertThrows(IllegalArgumentException.class, () -> validate(Valid.class));
        assertTrue(error.getMessage().contains("httpExcludedActions"));
    }

    @EntCrudEntity(httpExcludedActions = "create") static class Valid { }
    @EntCrudEntity(httpExcludedActions = " ") static class Blank { }
    @EntCrudEntity(httpExcludedActions = {"create", "create"}) static class Duplicate { }
    @EntCrudEntity(httpExcludedActions = "craete") static class Typo { }
    @EntCrudEntity(httpExcludedActions = "place-alias") static class Alias { }
}
