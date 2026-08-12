package com.entloom.ddl.bootstrap;

import java.util.Collections;
import java.util.List;

/**
 * 默认实体类解析器：不做包扫描。
 */
public final class NoopEntityClassResolver implements EntityClassResolver {
    @Override
    public List<Class<?>> resolve(List<String> basePackages) {
        return Collections.emptyList();
    }
}
