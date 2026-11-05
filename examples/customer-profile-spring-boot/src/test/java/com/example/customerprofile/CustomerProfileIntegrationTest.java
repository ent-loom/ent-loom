package com.example.customerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:profile;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@ActiveProfiles("example")
@AutoConfigureMockMvc
class CustomerProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS customer_profile (id BIGINT PRIMARY KEY, display_name VARCHAR(200), email VARCHAR(200))");
        jdbcTemplate.update("DELETE FROM customer_profile");
    }

    @Test
    void 实体注解驱动授权和输入契约() throws Exception {
        mvc.perform(post("/api/ent-crud/customer_profile/create")
                .header("Authorization", "Bearer demo-profile-admin")
                .contentType("application/json")
                .content("{\"payload\":{\"id\":1,\"displayName\":\"Ada\",\"email\":\"ada@example.com\"}}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/ent-crud/customer_profile/detail")
                .header("Authorization", "Bearer demo-profile-admin")
                .contentType("application/json").content("{\"id\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.displayName").value("Ada"));
        mvc.perform(post("/api/ent-crud/customer_profile/create")
                .header("Authorization", "Bearer demo-profile-admin")
                .contentType("application/json").content("{\"payload\":{\"id\":2,\"displayName\":\"Missing email\"}}"))
            .andExpect(status().isBadRequest());
    }
}
