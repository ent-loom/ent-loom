package com.example.minicommerce.product;

import com.entloom.crud.core.adapter.AccessEntryResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 YAML、Gateway 和 JDBC，仅替换可信入口识别。 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:commerce-visibility;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.sql.init.mode=always"
})
@ActiveProfiles({"example", "visibility"})
@AutoConfigureMockMvc
class ProductVisibilityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AccessEntryResolver accessEntryResolver;

    @BeforeEach
    void seed() {
        when(accessEntryResolver.resolveAccessEntry(any())).thenReturn("consumer");
        jdbc.update("delete from product");
        jdbc.update("insert into product values (10, '启用商品', 10, true), (30, '停用商品', 30, false)");
    }

    @Test
    void 用户端不传条件自动过滤且反向筛选无法绕过() throws Exception {
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(10));
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"filter\":{\"active\":false}}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(0));
        mvc.perform(post("/api/ent-crud/product/detail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"filter\":{\"id\":30}}}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void 已授权管理入口允许全部状态但未知入口拒绝() throws Exception {
        when(accessEntryResolver.resolveAccessEntry(any())).thenReturn("management");
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(2));
        when(accessEntryResolver.resolveAccessEntry(any())).thenReturn("unknown");
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DATA_SCOPE_DENIED"));
    }

    @Test
    void 请求不能伪造可信业务入口() throws Exception {
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"crudAccessEntry\":\"management\"}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
