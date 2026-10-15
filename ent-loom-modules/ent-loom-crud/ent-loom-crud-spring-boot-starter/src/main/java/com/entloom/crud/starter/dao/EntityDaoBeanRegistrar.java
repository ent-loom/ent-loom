package com.entloom.crud.starter.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import org.springframework.beans.factory.BeanFactoryInitializer;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/**
 * 按已注册实体补齐基础 DAO；在所有 Bean 后处理器就绪、业务单例创建前执行。
 * 元数据经过正常 Bean 生命周期后再读取；已有 DAO 沿用 Spring 的泛型匹配和歧义处理。
 */
public final class EntityDaoBeanRegistrar implements BeanFactoryInitializer<ConfigurableListableBeanFactory> {
    @Override
    public void initialize(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            throw new IllegalStateException("基础 DAO 自动注册需要支持 BeanDefinitionRegistry 的 BeanFactory");
        }
        for (var meta : beanFactory.getBean(EntityMetaRegistry.class).getEntityMetas()) {
            // 联合主键尚无 EntityDao 合同，不影响其他实体的自动注册。
            if (meta.getIdPolicy() == EntityIdPolicy.COMPOSITE || meta.getIdField().contains(",")) {
                continue;
            }
            var id = meta.resolveFieldMeta(meta.getIdField());
            if (id == null || id.getJavaType() == null) {
                throw new IllegalArgumentException("DAO 主键元数据缺失: " + meta.getEntityType().getName());
            }
            ResolvableType daoType = ResolvableType.forClassWithGenerics(EntityDao.class,
                meta.getEntityType(), ClassUtils.resolvePrimitiveIfNecessary(id.getJavaType()));
            if (beanFactory.getBeanNamesForType(daoType, true, false).length > 0) {
                continue;
            }
            String name = EntityDao.class.getName() + "#" + meta.getEntityType().getName();
            if (beanFactory.containsBean(name)) {
                throw new IllegalStateException("DAO Bean 名称冲突: " + name);
            }
            RootBeanDefinition definition = new RootBeanDefinition(EntityDao.class);
            definition.setTargetType(daoType);
            definition.setInstanceSupplier(() -> EntDaoProxyFactory.create(daoType, beanFactory));
            registry.registerBeanDefinition(name, definition);
        }
    }
}
