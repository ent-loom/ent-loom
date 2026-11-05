package com.example.minicommerce.merchant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=jdbc:h2:mem:commerce-merchant-access;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class MerchantAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        jdbc.update("delete from merchant");
        jdbc.update("insert into merchant(id, name, legal_representative_name, legal_representative_phone) values "
            + "(1001, '商家甲', '法人甲', '13800000001'), (2001, '商家乙', '法人乙', '13800000002')");
    }

    @Test
    void 匿名和顾客仅能查看公开字段且不能写入() throws Exception {
        for (String token : new String[] {"", "demo-customer-a"}) {
            var list = post("/api/ent-crud/merchant/list").contentType(MediaType.APPLICATION_JSON).content("{}");
            if (!token.isEmpty()) list.header("Authorization", "Bearer " + token);
            mvc.perform(list).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").exists())
                .andExpect(jsonPath("$.data.items[0].name").exists())
                .andExpect(jsonPath("$.data.items[*].legalRepresentativeName").isEmpty())
                .andExpect(jsonPath("$.data.items[*].legalRepresentativePhone").isEmpty());
            var update = post("/api/ent-crud/merchant/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":1001,\"name\":\"越权\"}}");
            if (!token.isEmpty()) update.header("Authorization", "Bearer " + token);
            mvc.perform(update).andExpect(status().isForbidden());
        }
    }

    @Test
    void 商家仅能查看和修改自己的法人资料() throws Exception {
        mvc.perform(post("/api/ent-crud/merchant/list").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(1001))
            .andExpect(jsonPath("$.data.items[0].legalRepresentativeName").value("法人甲"))
            .andExpect(jsonPath("$.data.items[0].legalRepresentativePhone").value("13800000001"));
        mvc.perform(post("/api/ent-crud/merchant/detail").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content("{\"id\":2001}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/ent-crud/merchant/update").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":1001,\"legalRepresentativePhone\":\"13900000001\"}}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/ent-crud/merchant/update").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":2001,\"legalRepresentativePhone\":\"13900000001\"}}"))
            .andExpect(status().isConflict());
    }

    @Test
    void 超级管理员可查看全部商家的完整资料() throws Exception {
        mvc.perform(post("/api/ent-crud/merchant/list").header("Authorization", "Bearer demo-platform-admin")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].legalRepresentativeName").exists())
            .andExpect(jsonPath("$.data.items[1].legalRepresentativeName").exists())
            .andExpect(jsonPath("$.data.items[0].legalRepresentativePhone").exists())
            .andExpect(jsonPath("$.data.items[1].legalRepresentativePhone").exists());
    }

}
