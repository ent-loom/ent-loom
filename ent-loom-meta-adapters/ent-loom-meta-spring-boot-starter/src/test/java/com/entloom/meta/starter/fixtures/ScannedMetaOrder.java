package com.entloom.meta.starter.fixtures;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.EntFieldKind;

@EntEntity(entity = "p1_scanned_meta_order", label = "Scanned Meta Order", service = "order-service")
public class ScannedMetaOrder {
    @EntField(value = EntFieldKind.ID, required = OptionalBoolean.TRUE)
    private Long id;

    @EntField(value = EntFieldKind.TEXT, label = "Order No", required = OptionalBoolean.TRUE)
    private String orderNo;
}
