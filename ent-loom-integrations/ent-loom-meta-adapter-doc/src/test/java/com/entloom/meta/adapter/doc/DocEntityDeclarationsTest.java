package com.entloom.meta.adapter.doc;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocEntityDeclarationsTest {
    @Test
    void 注解声明实体与字段展示() {
        var declaration = new AnnotationDocOverrides().overrideFor(Order.class, "order");
        assertEquals("交易", declaration.group());
        assertEquals(Boolean.TRUE, declaration.hidden());
        assertEquals("工作日配送", declaration.fields().get("remark").example());
        assertEquals(2, declaration.fields().get("remark").examples().size());
        assertEquals("客户", declaration.fields().get("remark").targetEntityLabel());
    }

    @EntDocEntity(group = "交易", hidden = OptionalBoolean.TRUE)
    private static class Order {
        @EntDocField(example = "工作日配送", examples = {"工作日配送", "周末配送"}, targetEntityLabel = "客户")
        String remark;
    }
}
