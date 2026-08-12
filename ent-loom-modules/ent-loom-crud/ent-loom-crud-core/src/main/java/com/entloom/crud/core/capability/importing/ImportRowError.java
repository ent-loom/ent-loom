package com.entloom.crud.core.capability.importing;

/**
 * 导入错误行。
 */
public final class ImportRowError {
    private final int rowNumber;
    private final String field;
    private final String code;
    private final String message;

    public ImportRowError(int rowNumber, String field, String code, String message) {
        this.rowNumber = rowNumber;
        this.field = field;
        this.code = code;
        this.message = message;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getField() {
        return field;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
