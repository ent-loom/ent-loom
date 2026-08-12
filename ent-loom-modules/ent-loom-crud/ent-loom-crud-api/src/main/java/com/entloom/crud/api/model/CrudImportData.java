package com.entloom.crud.api.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 导入响应数据。
 */
@Getter
@Setter
public class CrudImportData {
    private boolean accepted;
    private boolean async;
    private CrudTaskData task;
    private CrudFileData errorFile;
    private int totalRows;
    private int validRows;
    private int failedRows;
    private int insertedRows;
    private int updatedRows;
    private List<CrudRowErrorData> rowErrors = new ArrayList<CrudRowErrorData>();
}
