package com.entloom.meta.starter.doc;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntityDocumentationContractControllerTest {
    @Test
    void shouldExposeContractFromServiceWithoutSecondDto() throws Exception {
        EntityDocumentationContractService service = Mockito.mock(EntityDocumentationContractService.class);
        Map<String, Object> contract = new LinkedHashMap<String, Object>();
        contract.put("contractVersion", "1.0.0");
        contract.put("entities", java.util.List.of());
        when(service.build()).thenReturn(contract);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new EntityDocumentationContractController(service)
            )
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();

        mockMvc.perform(get("/api/ent-doc/contract"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.contractVersion", equalTo("1.0.0")))
            .andExpect(jsonPath("$.entities").isArray());

        verify(service).build();
    }
}
