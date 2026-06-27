package com.entloom.crud.api.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Import / Export 任务响应数据。
 */
@Getter
@Setter
public class CrudTaskData {
    private String taskId;
    private String status;
    private Integer progress;
    private String message;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant finishedAt;
    private CrudFileData sourceFile;
    private CrudFileData resultFile;
    private CrudFileData errorFile;
}
