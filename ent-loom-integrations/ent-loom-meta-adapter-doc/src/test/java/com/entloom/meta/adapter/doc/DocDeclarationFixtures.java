package com.entloom.meta.adapter.doc;

import com.entloom.declaration.EntityDeclarations;
import com.entloom.doc.core.spi.DocOverrideProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/** 按被测实体加载隔离的真实文档文件。 */
final class DocDeclarationFixtures {
    private DocDeclarationFixtures() { }
    static DocOverrideProvider provider(String suite) {
        return (type, resource) -> {
            var source = new ClassPathResource("declarations/" + suite + "/" + type.getSimpleName() + ".yml");
            if (!source.exists()) return null;
            try {
                return new DocEntityDeclarations(new EntityDeclarations(new ByteArrayResource(source.getInputStream().readAllBytes()) {
                    @Override public String getFilename() { return resource + ".yml"; }
                })).overrideFor(type, resource);
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        };
    }
}
