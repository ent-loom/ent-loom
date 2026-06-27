package com.entloom.meta.core.spi;

/**
 * Business boundary used by meta-core when turning EntRelation descriptors into runtime relation edges.
 */
public interface EntRelationEntityResolver {

    Class<?> resolveEntityClass(String code);

    String resolveIdField(Class<?> entityClass);

    boolean isAllowedField(Class<?> entityClass, String fieldName);
}
