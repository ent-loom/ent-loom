package com.entloom.meta.adapter.doc;

import com.entloom.declaration.EntityDeclarations;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import static org.junit.jupiter.api.Assertions.*;

/** 实体文档文件进入既有覆盖模型，非法字段和未消费实体不能静默丢失。 */
class DocEntityDeclarationsTest {
    private DocEntityDeclarations declarations(String content) {
        return new DocEntityDeclarations(new EntityDeclarations(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "order.yml"; }
        }));
    }

    @Test
    void 编译展示与字段示例并保留来源诊断() {
        var declarations = declarations("doc: {group: 交易, hidden: false, visible-for: [consumer], fields: {remark: {example: 工作日配送, examples: [工作日配送, 周末配送]}}}");
        declarations.validateResources(Set.of("order"));
        var model = declarations.overrideFor(Order.class, "order");
        assertEquals("交易", model.group());
        assertEquals(Boolean.FALSE, model.hidden());
        assertEquals("工作日配送", model.fields().get("remark").example());
        assertEquals(2, model.fields().get("remark").examples().size());
        assertNull(declarations.overrideFor(Order.class, "missing"));
    }

    @Test
    void 未知实体字段类型与节点拒绝() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> declarations("doc: {}").validateResources(Set.of())).getMessage().contains("未注册或未扫描"));
        for (String yaml : new String[]{"doc: {fields: {missing: {hidden: true}}}",
                "doc: {hidden: null}", "doc: {visible-for: [consumer, consumer]}",
                "doc: {fields: {remark: {typo: value}}}"}) {
            var exception = assertThrows(IllegalArgumentException.class, () -> declarations(yaml).overrideFor(Order.class, "order"));
            assertTrue(exception.getMessage().contains("实体=order"));
            assertTrue(exception.getMessage().contains("路径=doc"));
        }
        assertThrows(IllegalArgumentException.class, () -> declarations("doc: {typo: value}"));
    }

    private static class Order { String remark; }
}
