package com.entloom.meta.starter.scanfixture.nested;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.doc.annotations.EntDocEntity;

public class ScannedEntities {
    @EntEntity(entity = "scanned_entity", value = "扫描实体")
    public static class MetaEntity {
        private Long id;
    }

    @EntCrudEntity(name = "scanned_crud_entity")
    public static class CrudEntity {
        private Long id;
    }

    @EntDocEntity(name = "扫描文档实体")
    public static class DocEntity {
        private Long id;
    }

    public static class PlainClass {
        private Long id;
    }
}
