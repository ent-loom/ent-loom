package com.entloom.ddl.spring.discoveryfixtures;

import com.entloom.ddl.annotations.EntDbEntity;

/**
 * 用于 Spring 包扫描合同测试的实体。
 */
@EntDbEntity(table = "spring_discovery_zeta")
public final class ZetaEntity {
    /**
     * 实体主键。
     */
    private Long id;
}
