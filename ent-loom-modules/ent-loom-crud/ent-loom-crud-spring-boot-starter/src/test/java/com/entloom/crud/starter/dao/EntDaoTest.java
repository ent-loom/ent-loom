package com.entloom.crud.starter.dao;

import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.annotations.EntCommand;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.starter.config.EntDaoAutoConfiguration;
import com.entloom.crud.starter.daofixture.TestCustomerDao;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/** 验证真实 Spring 扫描注入、范围隔离和声明错误的启动失败。 */
class EntDaoTest {
    private final EntityMetaRegistry registry = mock(EntityMetaRegistry.class);
    private final EntityDaoFactory factory = mock(EntityDaoFactory.class);
    private final EntityDaoScopeResolver resolver = mock(EntityDaoScopeResolver.class);
    @SuppressWarnings("unchecked")
    private final EntityDao<String, Long> delegate = mock(EntityDao.class);

    private ApplicationContextRunner runner() {
        return runnerWithoutResolver().withBean(EntityDaoScopeResolver.class, () -> resolver);
    }

    private ApplicationContextRunner runnerWithoutResolver() {
        EntityMeta meta = mock(EntityMeta.class);
        EntityFieldMeta id = mock(EntityFieldMeta.class);
        when(registry.getEntityMeta(String.class)).thenReturn(meta);
        doReturn(String.class).when(meta).getEntityType();
        when(meta.getIdField()).thenReturn("id");
        when(meta.resolveFieldMeta("id")).thenReturn(id);
        doReturn(long.class).when(id).getJavaType();
        when(resolver.resolve(any())).thenReturn(EntityAccessScope.unrestricted());
        doReturn(delegate).when(factory).scoped(any(), any());
        return new ApplicationContextRunner()
            .withBean(EntityMetaRegistry.class, () -> registry)
            .withBean(EntityDaoFactory.class, () -> factory);
    }

    private ApplicationContextRunner direct(Class<?> daoType) {
        return runner().withBean("testDao", EntDaoFactoryBean.class, () -> new EntDaoFactoryBean<>(daoType));
    }

    @Test
    void scans_application_package_and_injects_interface() {
        runner().withConfiguration(AutoConfigurations.of(EntDaoAutoConfiguration.class))
            .withInitializer(context -> AutoConfigurationPackages.register(
                (BeanDefinitionRegistry) context.getBeanFactory(), TestCustomerDao.class.getPackageName()))
            .withBean(Consumer.class)
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(TestCustomerDao.class);
                assertThat(context.getBean(Consumer.class).dao).isSameAs(context.getBean(TestCustomerDao.class));
                verifyNoInteractions(factory, resolver);
            });
    }

    @Test
    void explicit_scan_replaces_default_and_deduplicates_packages() {
        runner().withConfiguration(AutoConfigurations.of(EntDaoAutoConfiguration.class))
            .withUserConfiguration(ExplicitScan.class, RepeatedScan.class)
            .withInitializer(context -> AutoConfigurationPackages.register(
                (BeanDefinitionRegistry) context.getBeanFactory(), "missing.application.package"))
            .run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(TestCustomerDao.class).hasSingleBean(EntDaoScannerConfigurer.class));
    }

    @Test
    void no_application_package_does_not_scan_entire_classpath() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(EntDaoAutoConfiguration.class))
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TestCustomerDao.class));
    }

    @Test
    void resolves_scope_for_each_call_and_dispatches_all_crud_methods() {
        direct(TestCustomerDao.class).run(context -> {
            TestCustomerDao dao = context.getBean(TestCustomerDao.class);
            var first = EntityAccessScope.of(RowConstraint.eq("tenantId", 1L));
            var second = EntityAccessScope.of(RowConstraint.eq("tenantId", 2L));
            var scope = new AtomicReference<>(first);
            when(resolver.resolve(any())).thenAnswer(invocation -> scope.get());
            when(delegate.findById(1L)).thenReturn(Optional.of("客户"));
            when(delegate.insert("客户")).thenReturn(1L);
            UpdatePatch<String> patch = mock(UpdatePatch.class);
            when(delegate.updateById(1L, patch)).thenReturn(1);
            when(delegate.deleteById(1L)).thenReturn(1);
            assertThat(dao.requireById(1L)).isEqualTo("客户");
            scope.set(second);
            assertThat(dao.insert("客户")).isEqualTo(1L);
            assertThat(dao.updateById(1L, patch)).isEqualTo(1);
            assertThat(dao.deleteById(1L)).isEqualTo(1);
            verify(factory).scoped(any(), same(first));
            verify(factory, times(3)).scoped(any(), same(second));
            verify(resolver, times(4)).resolve(argThat(type -> type.entityClass() == String.class
                && type.idClass() == Long.class));
        });
    }

    @Test
    void object_methods_do_not_resolve_scope() {
        direct(TestCustomerDao.class).run(context -> {
            var dao = context.getBean(TestCustomerDao.class);
            assertThat(dao.equals(dao)).isTrue();
            assertThat(dao.equals(new Object())).isFalse();
            assertThat(dao.hashCode()).isEqualTo(System.identityHashCode(dao));
            assertThat(dao.toString()).contains(TestCustomerDao.class.getName());
            verifyNoInteractions(factory, resolver);
        });
    }

    @Test
    void propagates_original_exception() {
        direct(TestCustomerDao.class).run(context -> {
            var failure = new IllegalStateException("业务失败");
            when(delegate.findById(1L)).thenThrow(failure);
            assertThatThrownBy(() -> context.getBean(TestCustomerDao.class).findById(1L)).isSameAs(failure);
        });
    }

    @Test
    void dispatches_annotated_custom_methods_through_factory() {
        direct(CustomDao.class).run(context -> {
            CustomDao dao = context.getBean(CustomDao.class);
            when(factory.invokeCustom(any(), any(), any(), any())).thenReturn("查询结果");
            assertThat(dao.findByName("客户")).isEqualTo("查询结果");
            verify(factory).validateCustomMethod(any(), argThat(method -> method.getName().equals("findByName")));
            verify(factory).invokeCustom(any(), argThat(scope -> scope.getRowConstraint().isUnrestricted()),
                argThat(method -> method.getName().equals("findByName")), any());
        });
    }

    @Test
    void null_scope_fails_before_factory_access() {
        direct(TestCustomerDao.class).run(context -> {
            when(resolver.resolve(any())).thenReturn(null);
            assertThatThrownBy(() -> context.getBean(TestCustomerDao.class).findById(1L))
                .hasMessage("DAO 范围解析结果不能为空");
            verifyNoInteractions(factory);
        });
    }

    @Test
    void missing_resolver_fails_at_startup_without_consumers() {
        runnerWithoutResolver()
            .withBean("testDao", EntDaoFactoryBean.class, () -> new EntDaoFactoryBean<>(TestCustomerDao.class))
            .run(context -> assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(
                org.springframework.beans.factory.NoSuchBeanDefinitionException.class));
    }

    @Test
    void missing_metadata_fails_at_startup() {
        direct(TestCustomerDao.class).withInitializer(context -> when(registry.getEntityMeta(String.class)).thenReturn(null))
            .run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(
                "DAO 实体元数据未注册或类型不匹配: " + TestCustomerDao.class.getName()));
    }

    @Test
    void wrong_id_type_fails_at_startup() {
        direct(WrongIdDao.class).run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(
            "DAO 主键类型与实体元数据不一致: " + WrongIdDao.class.getName()));
    }

    @Test
    void raw_generic_and_unsupported_method_fail_at_startup() {
        direct(RawDao.class).run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(
            "DAO 必须指定实体与主键类型: " + RawDao.class.getName()));
        direct(UnsupportedDao.class).run(context -> assertThat(context).hasFailed());
        direct(String.class).run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(
            "@EntDao 必须声明在继承 EntityDao 的接口上: java.lang.String"));
    }

    @Test
    void annotated_base_method_fails_at_startup_instead_of_bypassing_validation() {
        direct(AnnotatedBaseOverrideDao.class).run(context -> assertThat(context.getStartupFailure())
            .hasRootCauseMessage("DAO 自定义方法不能覆盖 EntityDao 基础方法: "
                + "public default java.util.Optional "
                + AnnotatedBaseOverrideDao.class.getName() + ".findById(java.lang.Object)"));
    }

    @Test
    void annotated_default_method_fails_at_startup_instead_of_executing_default_body() {
        direct(DefaultCustomDao.class).run(context -> assertThat(context.getStartupFailure())
            .hasRootCauseMessage("DAO 自定义方法不能声明为 default: "
                + "public default java.lang.String " + DefaultCustomDao.class.getName() + ".findByName()"));
    }

    @Test
    void resolves_generic_parent_interface() {
        direct(InheritedDao.class).run(context -> assertThat(context).hasNotFailed().hasSingleBean(InheritedDao.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EntDaoScan(basePackageClasses = TestCustomerDao.class)
    static class ExplicitScan {}

    @Configuration(proxyBeanMethods = false)
    @EntDaoScan(basePackages = "com.entloom.crud.starter.daofixture")
    static class RepeatedScan {}

    static class Consumer {
        final TestCustomerDao dao;
        Consumer(TestCustomerDao dao) { this.dao = dao; }
    }

    interface WrongIdDao extends EntityDao<String, Integer> {}
    interface RawDao extends EntityDao {}
    interface UnsupportedDao extends EntityDao<String, Long> { String findByName(String name); }
    interface CustomDao extends EntityDao<String, Long> {
        @EntQuery("select * from customer where name = :name")
        String findByName(String name);

        @EntCommand("update customer set name = :name where id = :id")
        int updateName(Long id, String name);
    }
    interface AnnotatedBaseOverrideDao extends EntityDao<String, Long> {
        @EntQuery("select * from customer where id = :id")
        Optional<String> findById(Long id);
    }
    interface DefaultCustomDao extends EntityDao<String, Long> {
        @EntQuery("select * from customer where name = :name")
        default String findByName() { return "不应执行"; }
    }
    interface GenericDao<T> extends EntityDao<T, Long> {}
    interface InheritedDao extends GenericDao<String> {}
}
