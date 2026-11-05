package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CommandActionSceneResolver;
import com.entloom.crud.starter.web.dto.CrudCommandHttpRequest;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** 通用 HTTP 在场景规范化后检查动作暴露，避免别名绕过。 */
class CrudCommandActionExposureTest {
    private final CrudRequestSupport support = mock(CrudRequestSupport.class);
    private final CommandActionSceneResolver resolver = mock(CommandActionSceneResolver.class);
    private final List<Class<?>> types = List.of(Order.class);
    private final CrudCommandSpecAssembler assembler = new CrudCommandSpecAssembler(
        support, resolver, mock(EntityMetaRegistry.class), new ObjectMapper());

    private void prepare(String scene, String canonical) {
        when(support.normalizeEntityCodes("order", List.of())).thenReturn(List.of("order"));
        when(support.resolveEntityClasses(List.of("order"), "order")).thenReturn(types);
        when(support.normalizeScene(scene)).thenReturn(scene);
        when(resolver.canonicalizeActionScene(Order.class, types, scene)).thenReturn(canonical);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "place-alias"})
    void 规范动作与别名均不能访问已关闭入口(String scene) {
        prepare(scene, "create");
        assertThrows(PermissionDeniedException.class, () -> assembler.assemble(
            "order", CommandOperation.ACTION, scene, new CrudCommandHttpRequest(), null));
        verify(resolver, never()).resolveActionContract(any(), anyList(), anyString());
    }

    @Test
    void 同实体其他动作继续开放() {
        prepare("cancel", "cancel");
        when(resolver.resolveActionContract(Order.class, types, "cancel"))
            .thenReturn(new CommandActionContract(Void.class, Long.class, List.of()));
        assertEquals("cancel", assembler.assemble("order", CommandOperation.ACTION, "cancel",
            new CrudCommandHttpRequest(), null).getScene());
    }

    @EntCrudEntity(httpExcludedActions = "create")
    static class Order { }
}
