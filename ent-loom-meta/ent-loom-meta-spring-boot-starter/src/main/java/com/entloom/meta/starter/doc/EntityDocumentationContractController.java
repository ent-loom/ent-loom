package com.entloom.meta.starter.doc;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实体文档契约只读 HTTP 入口。
 */
@RestController
@RequestMapping("/api/ent-doc")
public class EntityDocumentationContractController {
    private final EntityDocumentationContractService contractService;

    public EntityDocumentationContractController(EntityDocumentationContractService contractService) {
        if (contractService == null) {
            throw new IllegalArgumentException("实体文档契约服务不能为空");
        }
        this.contractService = contractService;
    }

    /** 返回当前主体可见的实体文档契约。 */
    @GetMapping(value = "/contract", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> contract() {
        return contractService.build();
    }
}
