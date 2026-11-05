package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.query.gateway.QueryGatewayImpl;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.QueryRouter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryGatewayPrimaryKeyTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 使用元数据主键属性而非约定名称() {
        var entities = mock(EntityMetaRegistry.class);
        var meta = mock(EntityMeta.class);
        when(entities.getEntityMeta(Article.class)).thenReturn(meta);
        when(meta.getIdField()).thenReturn("articleKey");
        var gateway = spy(new QueryGatewayImpl(mock(QueryRouter.class), mock(ExecutionPipeline.class), entities, null));
        var article = new Article();
        doReturn(article).when(gateway).detail(any(QuerySpec.class));

        assertSame(article, gateway.detail(EntityType.of(Article.class, String.class), "A-1"));
        ArgumentCaptor<QuerySpec<Article>> captured = ArgumentCaptor.forClass(QuerySpec.class);
        verify(gateway).detail(captured.capture());
        var spec = captured.getValue();
        assertEquals(Article.class, spec.getRootType());
        assertEquals(Article.class, spec.getResultType());
        assertEquals(1, spec.getFilters().size());
        assertEquals("articleKey", spec.getFilters().get(0).getField());
        assertEquals(FilterOperator.EQ, spec.getFilters().get(0).getOperator());
        assertEquals("A-1", spec.getFilters().get(0).getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 拒绝空主键错误主键类型及缺失主键元数据() {
        var entities = mock(EntityMetaRegistry.class);
        var router = mock(QueryRouter.class);
        var pipeline = mock(ExecutionPipeline.class);
        var gateway = new QueryGatewayImpl(router, pipeline, entities, null);
        var type = EntityType.of(Article.class, String.class);
        assertThrows(ValidationException.class, () -> gateway.detail(type, null));
        assertThrows(ValidationException.class, () -> gateway.detail((EntityType) type, 1L));
        verifyNoInteractions(entities);
        assertThrows(ValidationException.class, () -> gateway.detail(type, "A-1"));
        var meta = mock(EntityMeta.class);
        when(entities.getEntityMeta(Article.class)).thenReturn(meta);
        when(meta.getIdField()).thenReturn(" ");
        assertThrows(ValidationException.class, () -> gateway.detail(type, "A-1"));
        verifyNoInteractions(router, pipeline);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 基本类型主键接受对应包装值且拒绝其他类型() {
        var entities = mock(EntityMetaRegistry.class);
        var meta = mock(EntityMeta.class);
        when(entities.getEntityMeta(Article.class)).thenReturn(meta);
        when(meta.getIdField()).thenReturn("articleKey");
        var gateway = spy(new QueryGatewayImpl(mock(QueryRouter.class), mock(ExecutionPipeline.class), entities, null));
        var article = new Article();
        doReturn(article).when(gateway).detail(any(QuerySpec.class));
        Class<?>[] types = {long.class, int.class, short.class, byte.class,
            boolean.class, double.class, float.class, char.class};
        Object[] ids = {1L, 1, (short) 1, (byte) 1, true, 1D, 1F, 'A'};
        for (int i = 0; i < types.length; i++) {
            assertSame(article, gateway.detail((EntityType) EntityType.of(Article.class, types[i]), ids[i]));
        }
        var longType = EntityType.of(Article.class, long.class);
        assertThrows(ValidationException.class, () -> gateway.detail(longType, null));
        assertThrows(ValidationException.class, () -> gateway.detail((EntityType) longType, 1));
        verify(gateway, times(types.length)).detail(any(QuerySpec.class));
    }

    private static class Article { }
}
