package com.entloom.ddl.spring.discoveryfixtures;

import com.entloom.ddl.annotations.EntDbEntity;

/**
 * 用于 Spring 包扫描合同测试的实体。
 */
@EntDbEntity(table = "spring_discovery_alpha")
public final class AlphaEntity {
    /**
     * 实体主键。
     */
    private Long id;
}
