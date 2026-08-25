package com.entloom.ddl.bootstrap;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.bootstrap.discoveryfixtures.AlphaEntity;
import com.entloom.ddl.bootstrap.discoveryfixtures.ZetaEntity;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DdlBootstrap 实体发现合同测试。
 */
class DdlBootstrapDiscoveryTest {
    private static final String FIXTURE_PACKAGE = "com.entloom.ddl.bootstrap.discoveryfixtures";

    @Test
    @DisplayName("空输入返回空实体请求且不产生错误")
    void shouldReturnEmptyEntitiesForEmptyInput() {
        CapturingDdlEngine engine = new CapturingDdlEngine();
        DdlBootstrap bootstrap = new DdlBootstrap(
                engine,
                new AnnotationMetadataLoader(new NoopEntityClassResolver()),
                null,
                null);

        DdlExecutionResult result = bootstrap.execute(new DdlBootstrapRequest(
                "",
                false,
                DdlExecutionMode.NONE,
                Collections.<String>emptyList(),
                Collections.<Class<?>>emptyList()));

        assertTrue(result.success());
        assertEquals(Collections.emptyList(), engine.requests.get(0).entities());
    }

    @Test
    @DisplayName("显式类和包扫描统一合并并按类名去重排序")
    void shouldMergeDeduplicateAndSortExplicitAndScannedClasses() {
        CapturingDdlEngine engine = new CapturingDdlEngine();
        DdlBootstrap bootstrap = new DdlBootstrap(
                engine,
                null,
                null,
                null);

        bootstrap.execute(request(Arrays.asList(ZetaEntity.class, AlphaEntity.class, ZetaEntity.class),
                Arrays.asList(FIXTURE_PACKAGE, " ", FIXTURE_PACKAGE)));

        assertEquals(Arrays.asList(AlphaEntity.class.getName(), ZetaEntity.class.getName()),
                entityClassNames(engine.requests.get(0)));
    }

    @Test
    @DisplayName("不可加载类被跳过且不会阻断包扫描")
    void shouldSkipUnloadableClass() throws Exception {
        Path root = Files.createTempDirectory("ddl-discovery-test");
        try {
            Path packageDirectory = root.resolve("unloadable");
            Files.createDirectories(packageDirectory);
            Files.createFile(packageDirectory.resolve("BrokenEntity.class"));

            ClassLoader classLoader = new UnloadableClassLoader(
                    packageDirectory.toUri().toURL(),
                    "unloadable.BrokenEntity");

            assertEquals(Collections.emptyList(),
                    new ClasspathEntityClassResolver(classLoader).resolve(
                            Collections.singletonList("unloadable")));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    @DisplayName("重复调用重新生成相同的稳定发现结果")
    void shouldProduceSameDiscoveryResultOnRepeatedCalls() {
        CapturingDdlEngine engine = new CapturingDdlEngine();
        DdlBootstrap bootstrap = new DdlBootstrap(
                engine,
                new AnnotationMetadataLoader(new NoopEntityClassResolver()),
                null,
                null);
        DdlBootstrapRequest request = request(
                Arrays.asList(ZetaEntity.class, AlphaEntity.class),
                Collections.<String>emptyList());

        bootstrap.execute(request);
        bootstrap.execute(request);

        assertEquals(2, engine.requests.size());
        assertEquals(entityClassNames(engine.requests.get(0)), entityClassNames(engine.requests.get(1)));
        assertEquals(Arrays.asList(AlphaEntity.class.getName(), ZetaEntity.class.getName()),
                entityClassNames(engine.requests.get(1)));
    }

    private static DdlBootstrapRequest request(List<Class<?>> entityClasses, List<String> basePackages) {
        return new DdlBootstrapRequest(
                "",
                false,
                DdlExecutionMode.NONE,
                basePackages,
                entityClasses);
    }

    private static List<String> entityClassNames(DdlExecutionRequest request) {
        return request.entities().stream()
                .map(entity -> entity.entityClassName())
                .collect(Collectors.toList());
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("测试临时目录清理失败", ex);
                        }
                    });
        }
    }

    private static final class CapturingDdlEngine implements DdlEngine {
        private final List<DdlExecutionRequest> requests = new ArrayList<DdlExecutionRequest>();

        @Override
        public DdlExecutionResult execute(DdlExecutionRequest request,
                                          QueryStrategy queryStrategy,
                                          SqlExecutor sqlExecutor) {
            requests.add(request);
            return new DdlExecutionResult(Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList());
        }
    }

    private static final class UnloadableClassLoader extends ClassLoader {
        private final URL packageResource;
        private final String unloadableClassName;

        private UnloadableClassLoader(URL packageResource, String unloadableClassName) {
            super(DdlBootstrapDiscoveryTest.class.getClassLoader());
            this.packageResource = packageResource;
            this.unloadableClassName = unloadableClassName;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if ("unloadable".equals(name)) {
                return Collections.enumeration(Collections.singleton(packageResource));
            }
            return super.getResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (unloadableClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
