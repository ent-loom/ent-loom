package com.entloom.crud.starter.dao;

import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.config.EntDaoAutoConfiguration;
import com.entloom.crud.starter.daofixture.TestCustomerDao;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/** 验证真实 Spring 泛型注入、默认注册退让、生命周期及原有范围门禁。 */
class EntityDaoInjectionTest {
    private final EntityMetaRegistry registry = mock(EntityMetaRegistry.class);
    private final EntityDaoFactory factory = mock(EntityDaoFactory.class);
    private final EntityDaoScopeResolver resolver = mock(EntityDaoScopeResolver.class);
    @SuppressWarnings("unchecked")
    private final EntityDao<String, Long> strings = mock(EntityDao.class);
    @SuppressWarnings("unchecked")
    private final EntityDao<Integer, Long> integers = mock(EntityDao.class);

    private EntityMeta meta(Class<?> entityType) {
        EntityMeta meta = mock(EntityMeta.class);
        EntityFieldMeta id = mock(EntityFieldMeta.class);
        doReturn(entityType).when(meta).getEntityType();
        when(meta.getIdField()).thenReturn("id");
        when(meta.getIdPolicy()).thenReturn(EntityIdPolicy.EXPLICIT);
        when(meta.resolveFieldMeta("id")).thenReturn(id);
        doReturn(long.class).when(id).getJavaType();
        when(registry.getEntityMeta(entityType)).thenReturn(meta);
        return meta;
    }

    private ApplicationContextRunner runner() {
        var metas = List.of(meta(String.class), meta(Integer.class));
        when(registry.getEntityMetas()).thenReturn(metas);
        when(resolver.resolve(any())).thenReturn(EntityAccessScope.unrestricted());
        doAnswer(call -> call.getArgument(0, EntityType.class)
            .getEntityClass() == String.class ? strings : integers).when(factory).scoped(any(), any());
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EntDaoAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> registry)
            .withBean(EntityDaoFactory.class, () -> factory)
            .withBean(EntityDaoScopeResolver.class, () -> resolver);
    }

    @Test
    void injects_distinct_generic_daos_into_constructor_and_inherited_field() {
        runner().withBean(Consumer.class).withBean(InheritedConsumer.class).run(context -> {
            assertThat(context).hasNotFailed();
            var consumer = context.getBean(Consumer.class);
            assertThat(context.getBean(InheritedConsumer.class).dao).isSameAs(consumer.strings);
            assertThat(consumer.strings).isNotSameAs(consumer.integers);
            assertThat(context.getBeanNamesForType(ResolvableType.forClassWithGenerics(
                EntityDao.class, String.class, Long.class))).hasSize(1);
            verifyNoInteractions(factory, resolver);
            when(strings.findById(1L)).thenReturn(Optional.of("客户"));
            when(integers.findById(2L)).thenReturn(Optional.of(2));
            assertThat(consumer.strings.findById(1L).orElseThrow()).isEqualTo("客户");
            assertThat(consumer.integers.findById(2L).orElseThrow()).isEqualTo(2);
            verify(factory).scoped(argThat(type -> type.getEntityClass().equals(String.class)
                && type.getIdClass().equals(Long.class)), any());
            verify(factory).scoped(argThat(type -> type.getEntityClass().equals(Integer.class)), any());
            assertThat(consumer.strings.toString()).contains("java.lang.String", "java.lang.Long");
        });
    }

    @Test
    void reuses_scanned_custom_dao_for_generic_injection() {
        runner().withUserConfiguration(CustomScan.class).withBean(Consumer.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TestCustomerDao.class);
            assertThat(context.getBean(Consumer.class).strings).isSameAs(context.getBean(TestCustomerDao.class));
            assertThat(context.getBeansOfType(EntityDao.class)).hasSize(2);
        });
    }

    @Test
    void respects_explicit_generic_bean_and_keeps_other_entities() {
        runner().withUserConfiguration(ExplicitDao.class).withBean(Consumer.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Consumer.class).strings).isSameAs(context.getBean("customStrings"));
            assertThat(context.getBeansOfType(EntityDao.class)).hasSize(2);
        });
    }

    @Test
    void leaves_multiple_explicit_candidates_to_spring_instead_of_creating_a_fallback() {
        runner().withUserConfiguration(ExplicitDao.class, AnotherExplicitDao.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(ResolvableType.forClassWithGenerics(
                EntityDao.class, String.class, Long.class))).hasSize(2);
        });
        runner().withUserConfiguration(ExplicitDao.class, AnotherExplicitDao.class).withBean(Consumer.class)
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void metadata_receives_bean_post_processing_before_registration_reads_it() {
        AtomicBoolean processed = new AtomicBoolean();
        runner().withBean("metadataProbe", BeanPostProcessor.class, () -> new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean == registry) {
                    processed.set(true);
                }
                return bean;
            }
        }).withInitializer(context -> when(registry.getEntityMetas()).thenAnswer(call -> {
            assertThat(processed).isTrue();
            return List.of(meta(String.class));
        })).withBean(InheritedConsumer.class).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void resolves_scope_per_call_and_keeps_authorization_guard() {
        runner().withBean(InheritedConsumer.class).run(context -> {
            assertThat(context).hasNotFailed();
            var dao = context.getBean(InheritedConsumer.class).dao;
            var first = EntityAccessScope.of(RowConstraint.eq("tenantId", 1L));
            var second = EntityAccessScope.of(RowConstraint.eq("tenantId", 2L));
            when(resolver.resolve(any())).thenReturn(first, second);
            dao.findById(1L);
            dao.findById(1L);
            verify(factory).scoped(any(), same(first));
            verify(factory).scoped(any(), same(second));
            clearInvocations(factory, resolver);
            try (var ignored = AccessContextScope.requireAuthorization()) {
                assertThatThrownBy(() -> dao.findById(1L)).hasMessageContaining("没有单次业务授权");
                assertThatThrownBy(() -> dao.insertAll(List.of("客户"))).hasMessageContaining("没有单次业务授权");
            }
            verifyNoInteractions(factory, resolver);
            when(resolver.resolve(any())).thenReturn(null);
            assertThatThrownBy(() -> dao.findById(1L)).hasMessage("DAO 范围解析结果不能为空");
            verifyNoInteractions(factory);
        });
    }

    @Test
    void wrong_id_generic_cannot_match_another_dao() {
        runner().withBean(WrongIdConsumer.class).run(context -> assertThat(context.getStartupFailure())
            .hasRootCauseExactlyInstanceOf(NoSuchBeanDefinitionException.class));
        runner().withInitializer(context -> {
            var metas = List.of(meta(String.class));
            when(registry.getEntityMetas()).thenReturn(metas);
        }).withBean(WrongIdConsumer.class).run(context -> assertThat(context.getStartupFailure())
            .hasRootCauseExactlyInstanceOf(NoSuchBeanDefinitionException.class));
    }

    @Test
    void no_scope_resolver_does_not_register_unrestricted_daos() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(EntDaoAutoConfiguration.class))
            .withBean(EntityMetaRegistry.class, () -> registry)
            .withBean(EntityDaoFactory.class, () -> factory)
            .run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(EntityDaoBeanRegistrar.class);
                assertThat(context.getBeansOfType(EntityDao.class)).isEmpty();
                verifyNoInteractions(factory, registry);
            });
    }

    @Test
    void skips_composite_keys_without_blocking_single_key_entities() {
        runner().withInitializer(context -> {
            var composite = meta(Integer.class);
            when(composite.getIdPolicy()).thenReturn(EntityIdPolicy.COMPOSITE);
            var metas = List.of(meta(String.class), composite);
            when(registry.getEntityMetas()).thenReturn(metas);
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(EntityDao.class)).hasSize(1);
        });
    }

    @Test
    void rejects_missing_id_metadata_and_bean_name_collision_at_startup() {
        runner().withInitializer(context -> when(registry.getEntityMeta(String.class).resolveFieldMeta("id"))
            .thenReturn(null)).run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("DAO 主键元数据缺失: java.lang.String"));
        runner().withBean(EntityDao.class.getName() + "#java.lang.String", Object.class, Object::new)
            .run(context -> assertThat(context.getStartupFailure()).hasMessageContaining("DAO Bean 名称冲突"));
    }

    static class Consumer {
        final EntityDao<String, Long> strings;
        final EntityDao<Integer, Long> integers;
        Consumer(EntityDao<String, Long> strings, EntityDao<Integer, Long> integers) {
            this.strings = strings;
            this.integers = integers;
        }
    }

    static class GenericConsumer<T> {
        @Autowired EntityDao<T, Long> dao;
    }

    static class InheritedConsumer extends GenericConsumer<String> {}

    static class WrongIdConsumer {
        WrongIdConsumer(EntityDao<String, Integer> dao) {}
    }

    @Configuration(proxyBeanMethods = false)
    @EntDaoScan(basePackageClasses = TestCustomerDao.class)
    static class CustomScan {}

    @Configuration(proxyBeanMethods = false)
    static class ExplicitDao {
        @Bean
        @SuppressWarnings("unchecked")
        EntityDao<String, Long> customStrings() { return mock(EntityDao.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnotherExplicitDao {
        @Bean
        @SuppressWarnings("unchecked")
        EntityDao<String, Long> anotherStrings() { return mock(EntityDao.class); }
    }
}
