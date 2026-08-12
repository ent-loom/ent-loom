package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.model.CrudExportColumnData;
import com.entloom.crud.api.model.CrudExportData;
import com.entloom.crud.api.model.CrudFileData;
import com.entloom.crud.api.model.CrudImportData;
import com.entloom.crud.api.model.CrudRowErrorData;
import com.entloom.crud.api.model.CrudTaskData;
import com.entloom.crud.core.capability.exporting.ExportColumn;
import com.entloom.crud.core.capability.exporting.ExportResult;
import com.entloom.crud.core.capability.importing.ImportResult;
import com.entloom.crud.core.capability.importing.ImportRowError;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Import / Export 响应装配器。
 */
public class CrudImportExportResponseAssembler {
    public CrudExportData exportData(ExportResult result) {
        CrudExportData data = new CrudExportData();
        if (result == null) {
            return data;
        }
        data.setAccepted(result.isAccepted());
        data.setAsync(result.isAsync());
        data.setTask(taskData(result.getTask()));
        data.setFile(fileData(result.getFile()));
        data.setTotalRows(result.getTotalRows());
        data.setColumns(exportColumns(result.getColumns()));
        data.setPreviewRows(result.getPreviewRows());
        return data;
    }

    private List<CrudExportColumnData> exportColumns(List<ExportColumn> columns) {
        List<CrudExportColumnData> data = new ArrayList<CrudExportColumnData>();
        if (columns == null) {
            return data;
        }
        for (ExportColumn column : columns) {
            CrudExportColumnData item = new CrudExportColumnData();
            item.setKey(column.getKey());
            item.setHeader(column.getHeader());
            data.add(item);
        }
        return data;
    }

    public CrudImportData importData(ImportResult result) {
        CrudImportData data = new CrudImportData();
        if (result == null) {
            return data;
        }
        data.setAccepted(result.isAccepted());
        data.setAsync(result.isAsync());
        data.setTask(taskData(result.getTask()));
        data.setErrorFile(fileData(result.getErrorFile()));
        data.setTotalRows(result.getTotalRows());
        data.setValidRows(result.getValidRows());
        data.setFailedRows(result.getFailedRows());
        data.setInsertedRows(result.getInsertedRows());
        data.setUpdatedRows(result.getUpdatedRows());
        data.setRowErrors(rowErrors(result.getRowErrors()));
        return data;
    }

    public CrudTaskData taskData(CrudTask task) {
        if (task == null) {
            return null;
        }
        CrudTaskData data = new CrudTaskData();
        data.setTaskId(task.getTaskId());
        data.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        data.setProgress(task.getProgress());
        data.setMessage(task.getMessage());
        data.setCreatedAt(task.getCreatedAt());
        data.setUpdatedAt(task.getUpdatedAt());
        data.setFinishedAt(task.getFinishedAt());
        data.setSourceFile(fileData(task.getSourceFile()));
        data.setResultFile(fileData(task.getResultFile()));
        data.setErrorFile(fileData(task.getErrorFile()));
        return data;
    }

    public CrudFileData fileData(FileRef file) {
        if (file == null) {
            return null;
        }
        CrudFileData data = new CrudFileData();
        data.setFileId(file.getFileId());
        data.setFileName(file.getFileName());
        data.setContentType(file.getContentType());
        data.setSize(file.getSize());
        data.setExpiresAt(file.getExpiresAt());
        data.setAttributes(publicFileAttributes(file.getAttributes()));
        return data;
    }

    private Map<String, Object> publicFileAttributes(Map<String, Object> attributes) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (attributes == null || attributes.isEmpty()) {
            return result;
        }
        copyIfPresent(result, attributes, "purpose");
        copyIfPresent(result, attributes, "format");
        return result;
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private List<CrudRowErrorData> rowErrors(List<ImportRowError> errors) {
        List<CrudRowErrorData> data = new ArrayList<CrudRowErrorData>();
        if (errors == null) {
            return data;
        }
        for (ImportRowError error : errors) {
            CrudRowErrorData item = new CrudRowErrorData();
            item.setRowNumber(error.getRowNumber());
            item.setField(error.getField());
            item.setCode(error.getCode());
            item.setMessage(error.getMessage());
            data.add(item);
        }
        return data;
    }
}
