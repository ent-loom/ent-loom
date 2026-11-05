package com.entloom.declaration;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;

class EntityDeclarationsTest {
    private Resource yaml(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), filename) {
            @Override public String getFilename() { return filename; }
        };
    }

    @Test
    void 关联资源码并冻结配置与来源() {
        var catalog = new EntityDeclarations(yaml("order.yml", "crud: {operations: {update: [UPDATE, UPDATE_BATCH]}}"));
        catalog.validateComponents(Set.of("crud"));
        catalog.validateResources(Set.of("order"));
        var node = catalog.entities().get("order").members().get("crud").members().get("operations").members().get("update");
        assertThat(node.strings()).containsExactly("UPDATE", "UPDATE_BATCH");
        assertThat(node.source()).contains("order.yml", "实体=order", "路径=crud.operations.update");
        assertThatThrownBy(() -> node.strings().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 重复实体和重复配置键及危险标签均拒绝() {
        assertThatThrownBy(() -> new EntityDeclarations(yaml("order.yml", "crud: {}"), yaml("order.yaml", "crud: {}")))
            .hasMessageContaining("重复实体").hasMessageContaining("order.yaml");
        for (String content : new String[]{"crud: {}\ncrud: {}", "!!java.net.URL ['https://example.com']", "crud: &c {copy: *c}"}) {
            assertThatThrownBy(() -> new EntityDeclarations(yaml("order.yml", content))).hasMessageContaining("order.yml");
        }
    }

    @Test
    void 缺失组件和未注册实体不会被忽略() {
        var catalog = new EntityDeclarations(yaml("order.yml", "ddl: {}"));
        assertThatThrownBy(() -> catalog.validateComponents(Set.of("crud"))).hasMessageContaining("路径=ddl").hasMessageContaining("未启用");
        assertThatThrownBy(() -> catalog.validateResources(Set.of("customer"))).hasMessageContaining("未注册或未扫描");
    }

    @Test
    void 从标准类路径发现文件() throws Exception {
        var catalog = EntityDeclarations.discover(new PathMatchingResourcePatternResolver());
        assertThat(catalog.entities()).containsOnlyKeys("testOrder");
    }
}
