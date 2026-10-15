package com.entloom.crud.starter.dao;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.ResolvableType;

/** 自定义 DAO 接口的 Spring FactoryBean 适配。 */
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
        proxy = daoType.cast(EntDaoProxyFactory.create(ResolvableType.forClass(daoType), beanFactory));
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
