package com.entloom.ddl.spring.discoveryfixtures;

import com.entloom.ddl.annotations.EntDdlEntity;

/**
 * 用于 Spring 包扫描合同测试的实体。
 */
@EntDdlEntity(table = "spring_discovery_alpha")
public final class AlphaEntity {
    /**
     * 实体主键。
     */
    private Long id;
}
