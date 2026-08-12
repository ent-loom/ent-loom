package com.entloom.crud.api.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Import / Export 文件响应数据。
 */
@Getter
@Setter
public class CrudFileData {
    private String fileId;
    private String fileName;
    private String contentType;
    private Long size;
    private Instant expiresAt;
    private Map<String, Object> attributes = new LinkedHashMap<String, Object>();
}
