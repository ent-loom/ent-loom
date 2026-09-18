package com.entloom.crud.starter.daofixture;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.starter.dao.EntDao;

/** 扫描测试接口，使用字符串作为代理合同的测试实体。 */
@EntDao
public interface TestCustomerDao extends EntityDao<String, Long> {
    /** 验证 default 方法经代理回调基础 CRUD。 */
    default String requireById(Long id) {
        return findById(id).orElseThrow();
    }
}
