package com.entloom.ddl.bootstrap.discoveryfixtures;

import com.entloom.ddl.annotations.EntDbEntity;

/**
 * 用于包扫描合同测试的实体。
 */
@EntDbEntity(table = "ddl_discovery_zeta")
public final class ZetaEntity {
    /**
     * 实体主键。
     */
    private Long id;
}
