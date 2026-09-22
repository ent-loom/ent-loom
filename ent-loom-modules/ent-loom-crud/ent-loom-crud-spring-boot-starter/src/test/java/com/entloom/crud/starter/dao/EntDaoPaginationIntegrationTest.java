package com.entloom.crud.starter.dao;

import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.api.enums.CountMode;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.PageQuery;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoScopeResolver;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.support.TestOrderEntity;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Spring EntDao 代理真实调用 JDBC 自定义分页方法。 */
class EntDaoPaginationIntegrationTest {
    @Test
    void spring_proxy_should_return_page_result_from_jdbc_dao() {
        new ApplicationContextRunner()
            .withUserConfiguration(StarterJdbcTestSupportConfiguration.class, CrudAutoConfiguration.class)
            .withPropertyValues(
                "ent.loom.crud.dao.pagination.max-page-size=3",
                "ent.loom.crud.dao.pagination.max-offset=100"
            )
            .withBean(EntityDaoScopeResolver.class, () -> entityType -> EntityAccessScope.unrestricted())
            .withBean("pageDao", EntDaoFactoryBean.class, () -> new EntDaoFactoryBean<>(PageDao.class))
            .run(context -> {
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                jdbcTemplate.update(
                    "update test_order set order_no = ? where id in (?, ?, ?)",
                    "CUSTOM-PAGE", 2L, 4L, 6L
                );
                PageDao dao = context.getBean(PageDao.class);
                PageResult<TestOrderEntity> result = dao.findPaid(
                    "CUSTOM-PAGE",
                    new PageQuery(
                        1,
                        2,
                        Collections.singletonList(new QuerySort("id", SortDirection.DESC)),
                        CountMode.ALWAYS
                    )
                );

                assertThat(result.getTotal()).isEqualTo(3L);
                assertThat(result.getItems()).hasSize(2);
                assertThat(result.getItems().get(0).getId()).isEqualTo(6L);
                assertThat(result.getHasNext()).isTrue();
                assertThatThrownBy(() -> dao.findPaid(
                    "CUSTOM-PAGE",
                    new PageQuery(1, 4)
                )).isInstanceOf(ValidationException.class);
            });
    }

    @EntDao
    interface PageDao extends EntityDao<TestOrderEntity, Long> {
        @EntQuery("select * from test_order where order_no = :orderNo")
        PageResult<TestOrderEntity> findPaid(String orderNo, PageQuery pageQuery);
    }
}
