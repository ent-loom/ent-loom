package com.entloom.crud.api.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 导出响应数据。
 */
@Getter
@Setter
public class CrudExportData {
    private boolean accepted;
    private boolean async;
    private CrudTaskData task;
    private CrudFileData file;
    private int totalRows;
    private List<CrudExportColumnData> columns = new ArrayList<CrudExportColumnData>();
    private List<Map<String, Object>> previewRows = new ArrayList<Map<String, Object>>();

    public void setColumns(List<CrudExportColumnData> columns) {
        this.columns = columns == null ? new ArrayList<CrudExportColumnData>() : columns;
    }

    public void setPreviewRows(List<Map<String, Object>> previewRows) {
        this.previewRows = previewRows == null ? new ArrayList<Map<String, Object>>() : previewRows;
    }
}
