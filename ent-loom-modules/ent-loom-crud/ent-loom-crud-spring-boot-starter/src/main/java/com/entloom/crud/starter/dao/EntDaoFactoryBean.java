package com.entloom.crud.starter.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Objects;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/** 创建无请求状态的单例代理，启动期校验声明，调用期绑定可信数据范围。 */
public final class EntDaoFactoryBean<T> implements SmartFactoryBean<T>, BeanFactoryAware, InitializingBean {
    private final Class<T> daoType;
    private BeanFactory beanFactory;
    private T proxy;

    public EntDaoFactoryBean(Class<T> daoType) {
        this.daoType = daoType;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        if (!daoType.isInterface() || !EntityDao.class.isAssignableFrom(daoType)) {
            throw new IllegalArgumentException("@EntDao 必须声明在继承 EntityDao 的接口上: " + daoType.getName());
        }
        ResolvableType type = ResolvableType.forClass(daoType).as(EntityDao.class);
        if (type.hasUnresolvableGenerics()) {
            throw new IllegalArgumentException("DAO 必须指定实体与主键类型: " + daoType.getName());
        }
        EntityType<?, ?> entityType = EntityType.of(type.getGeneric(0).resolve(), type.getGeneric(1).resolve());
        for (var method : daoType.getMethods()) {
            if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                try {
                    EntityDao.class.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException exception) {
                    throw new IllegalArgumentException("DAO 暂不支持自定义抽象方法，请使用 default 方法: " + method, exception);
                }
            }
        }
        var meta = beanFactory.getBean(EntityMetaRegistry.class).getEntityMeta(entityType.getEntityClass());
        if (meta == null || !entityType.getEntityClass().equals(meta.getEntityType())) {
            throw new IllegalArgumentException("DAO 实体元数据未注册或类型不匹配: " + daoType.getName());
        }
        var id = meta.resolveFieldMeta(meta.getIdField());
        if (id == null || id.getJavaType() == null
            || !ClassUtils.resolvePrimitiveIfNecessary(id.getJavaType()).equals(entityType.getIdClass())) {
            throw new IllegalArgumentException("DAO 主键类型与实体元数据不一致: " + daoType.getName());
        }
        EntityDaoFactory factory = beanFactory.getBean(EntityDaoFactory.class);
        EntityDaoScopeResolver scopeResolver = beanFactory.getBean(EntityDaoScopeResolver.class);
        proxy = daoType.cast(Proxy.newProxyInstance(daoType.getClassLoader(), new Class<?>[]{daoType},
            (instance, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> instance == args[0];
                        case "hashCode" -> System.identityHashCode(instance);
                        case "toString" -> "EntDao[" + daoType.getName() + "]";
                        default -> throw new IllegalStateException("不支持的 Object 方法: " + method);
                    };
                }
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(instance, method, args);
                }
                var scope = Objects.requireNonNull(scopeResolver.resolve(entityType), "DAO 范围解析结果不能为空");
                try {
                    return EntityDao.class.getMethod(method.getName(), method.getParameterTypes())
                        .invoke(factory.scoped(entityType, scope), args);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }));
    }

    @Override
    public T getObject() {
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return daoType;
    }

    @Override
    public boolean isEagerInit() {
        return true;
    }
}
