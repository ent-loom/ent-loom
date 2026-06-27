package com.entloom.ddl.bootstrap;

import java.util.List;

/**
 * 实体类解析器。
 */
public interface EntityClassResolver {
    List<Class<?>> resolve(List<String> basePackages);
}
