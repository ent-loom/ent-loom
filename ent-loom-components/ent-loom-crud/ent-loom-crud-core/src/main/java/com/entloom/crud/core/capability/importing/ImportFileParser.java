package com.entloom.crud.core.capability.importing;

/**
 * 导入格式解析器。
 */
public interface ImportFileParser {
    ImportParsedTable parse(ImportSpec spec, byte[] content);
}
