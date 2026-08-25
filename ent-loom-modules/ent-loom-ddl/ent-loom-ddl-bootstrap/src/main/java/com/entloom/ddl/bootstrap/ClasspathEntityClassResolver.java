package com.entloom.ddl.bootstrap;

import com.entloom.ddl.annotations.EntDbEntity;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 基于 JDK 类路径的实体类包扫描器。
 *
 * <p>扫描只负责发现带有 {@link EntDbEntity} 的可加载类。目录、Jar、包名、类名
 * 和最终结果均按稳定顺序处理；重复类只保留一份。类文件无法加载时跳过该类，
 * 不影响同一包中其他实体继续发现。</p>
 */
public final class ClasspathEntityClassResolver implements EntityClassResolver {
    private final ClassLoader classLoader;

    public ClasspathEntityClassResolver(ClassLoader classLoader) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        this.classLoader = classLoader != null
                ? classLoader
                : (contextClassLoader != null ? contextClassLoader : ClasspathEntityClassResolver.class.getClassLoader());
    }

    @Override
    public List<Class<?>> resolve(List<String> basePackages) {
        List<String> packages = normalizePackages(basePackages);
        if (packages.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Class<?>> entities = new LinkedHashMap<String, Class<?>>();
        for (String basePackage : packages) {
            scanPackage(basePackage, entities);
        }
        return new ArrayList<Class<?>>(entities.values());
    }

    private void scanPackage(String basePackage, Map<String, Class<?>> entities) {
        String packagePath = basePackage.replace('.', '/');
        List<URL> resources = new ArrayList<URL>();
        try {
            Enumeration<URL> resourceEnumeration = classLoader.getResources(packagePath);
            while (resourceEnumeration.hasMoreElements()) {
                resources.add(resourceEnumeration.nextElement());
            }
        } catch (IOException ignored) {
            return;
        }
        resources.sort(Comparator.comparing(URL::toExternalForm));
        for (URL resource : resources) {
            scanResource(basePackage, packagePath, resource, entities);
        }
    }

    private void scanResource(String basePackage,
                              String packagePath,
                              URL resource,
                              Map<String, Class<?>> entities) {
        try {
            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                scanDirectory(basePackage, Path.of(new URI(resource.toString())), entities);
                return;
            }
            if ("jar".equalsIgnoreCase(resource.getProtocol())) {
                scanJar(basePackage, packagePath, resource, entities);
            }
        } catch (IOException | URISyntaxException | RuntimeException ignored) {
            // 单个类路径资源不可读时继续处理其他资源。
        }
    }

    private void scanDirectory(String basePackage,
                               Path packageDirectory,
                               Map<String, Class<?>> entities) throws IOException {
        if (!Files.isDirectory(packageDirectory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(packageDirectory)) {
            List<Path> classFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
            for (Path classFile : classFiles) {
                addIfEntity(basePackage, packageDirectory.relativize(classFile).toString(), entities);
            }
        }
    }

    private void scanJar(String basePackage,
                         String packagePath,
                         URL resource,
                         Map<String, Class<?>> entities) throws IOException {
        URLConnection connection = resource.openConnection();
        connection.setUseCaches(false);
        if (!(connection instanceof JarURLConnection)) {
            return;
        }
        JarURLConnection jarConnection = (JarURLConnection) connection;
        try (JarFile jarFile = jarConnection.getJarFile()) {
            List<String> classEntries = new ArrayList<String>();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                if (entryName.startsWith(packagePath + "/") && entryName.endsWith(".class")) {
                    classEntries.add(entryName.substring(packagePath.length() + 1));
                }
            }
            Collections.sort(classEntries);
            for (String classEntry : classEntries) {
                addIfEntity(basePackage, classEntry, entities);
            }
        }
    }

    private void addIfEntity(String basePackage,
                             String relativeClassPath,
                             Map<String, Class<?>> entities) {
        String relativeClassName = relativeClassPath
                .replace('/', '.')
                .replace('\\', '.');
        if (!relativeClassName.endsWith(".class")) {
            return;
        }
        String simpleClassName = relativeClassName.substring(0, relativeClassName.length() - ".class".length());
        if (simpleClassName.endsWith("module-info") || simpleClassName.endsWith("package-info")) {
            return;
        }
        String className = basePackage + "." + simpleClassName;
        try {
            Class<?> candidate = Class.forName(className, false, classLoader);
            if (candidate.getAnnotation(EntDbEntity.class) != null) {
                entities.putIfAbsent(candidate.getName(), candidate);
            }
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            // 不可加载类不应阻断同一包的其他实体发现。
        }
    }

    private static List<String> normalizePackages(List<String> basePackages) {
        if (basePackages == null || basePackages.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> packages = new ArrayList<String>();
        for (String basePackage : basePackages) {
            if (basePackage != null && !basePackage.trim().isEmpty()) {
                packages.add(basePackage.trim());
            }
        }
        if (packages.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(packages);
        List<String> distinctPackages = new ArrayList<String>();
        String previous = null;
        for (String basePackage : packages) {
            if (!basePackage.equals(previous)) {
                distinctPackages.add(basePackage);
                previous = basePackage;
            }
        }
        return distinctPackages;
    }
}
