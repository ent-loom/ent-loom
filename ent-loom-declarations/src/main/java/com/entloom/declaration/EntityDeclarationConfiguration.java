package com.entloom.declaration;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** 所有组件共享一份资源目录；未安装或未启用的组件节点拒绝启动。 */
@AutoConfiguration
public class EntityDeclarationConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public EntityDeclarations entityDeclarations(ResourceLoader loader) throws IOException {
        return EntityDeclarations.discover(new PathMatchingResourcePatternResolver(loader));
    }

    @Bean
    public SmartInitializingSingleton entityDeclarationComponentsGuard(EntityDeclarations declarations,
        ObjectProvider<EntityDeclarationComponent> components) {
        return () -> {
            Set<String> enabled = new LinkedHashSet<>();
            components.orderedStream().forEach(component -> enabled.add(component.componentCode()));
            declarations.validateComponents(enabled);
        };
    }
}
