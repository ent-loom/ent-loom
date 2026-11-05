package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.adapter.ResourceCatalogAdapter;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.starter.config.module.CrudCommonConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 身份结构不再推导归属；只有显式元数据保护写入字段。 */
class EntityScopeMetadataTest {
    @Test
    void 注册商家身份不会将同名字段升级为安全范围() {
        runner(Ordinary.class).run(context -> {
            assertThat(context).hasNotFailed();
            var field = context.getBean(EntityMetaRegistry.class).getEntityMeta(Ordinary.class).resolveFieldMeta("merchantId");
            assertThat(field.isScopeField()).isFalse();
            assertThat(field.isWritable()).isTrue();
        });
    }

    @Test
    void 显式归属元数据独立于身份定义冻结() {
        runner(Owned.class).run(context -> {
            assertThat(context).hasNotFailed();
            var field = context.getBean(EntityMetaRegistry.class).getEntityMeta(Owned.class).resolveFieldMeta("merchantId");
            assertThat(field.isScopeField()).isTrue();
            assertThat(field.isWritable()).isFalse();
            context.getBean(CrudProperties.class).getIdentities().clear();
            assertThat(field.isScopeField()).isTrue();
        });
    }

    @Test
    void 错误归属字段必须启动失败() {
        for (var type : List.of(Missing.class, Primary.class, Duplicate.class, Blank.class)) {
            assertThatThrownBy(() -> new CrudNativeRuntimeModelParser().parse(List.of(type)))
                .as(type.getSimpleName()).isInstanceOf(RuntimeException.class);
        }
    }

    private ApplicationContextRunner runner(Class<?> type) {
        return new ApplicationContextRunner().withUserConfiguration(MetadataConfiguration.class)
            .withPropertyValues("ent.loom.crud.identities.merchant.id-type=LONG")
            .withBean(ResourceCatalogAdapter.class, () -> {
                var adapter = mock(ResourceCatalogAdapter.class);
                when(adapter.runtimeModel()).thenReturn(new CrudNativeRuntimeModelParser().parse(List.of(type)));
                return adapter;
            });
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Import(CrudPropertiesConfiguration.class)
    static class MetadataConfiguration {
        @Bean
        EntityMetaRegistry entities(ObjectProvider<ResourceCatalogAdapter[]> adapters) {
            return new CrudCommonConfiguration().metaRegistry(adapters);
        }
    }

    @EntCrudEntity static class Ordinary { Long id; Long merchantId; }
    @EntCrudEntity(scopeFields = "merchantId") static class Owned { Long id; Long merchantId; }
    @EntCrudEntity(scopeFields = "missing") static class Missing { Long id; }
    @EntCrudEntity(scopeFields = "id") static class Primary { Long id; }
    @EntCrudEntity(scopeFields = {"merchantId", "merchantId"}) static class Duplicate { Long id; Long merchantId; }
    @EntCrudEntity(scopeFields = " ") static class Blank { Long id; }
}
