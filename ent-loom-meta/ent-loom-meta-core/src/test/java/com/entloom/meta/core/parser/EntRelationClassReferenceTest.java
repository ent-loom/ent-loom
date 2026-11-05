package com.entloom.meta.core.parser;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.descriptor.EntEntityDescriptor;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.descriptor.MetaDescriptorProperties;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticException;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import com.entloom.meta.contract.enums.RelationResolutionStatus;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.core.model.MetaEntityDefaults;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntRelationClassReferenceTest {
    private final ReflectiveEntMetaParser parser = new ReflectiveEntMetaParser();

    @Test
    void 类引用复用目标实体的显式标识与服务并保留声明来源() {
        EntRelationDescriptor relation = parser.parse(ProductOrder.class).relations().get(0);

        assertEquals("catalog_product", relation.targetEntity());
        assertSame(Product.class, relation.targetClass());
        assertEquals("catalog", relation.targetService());
        assertEquals("productId", relation.sourceField());
        assertEquals("id", relation.targetField());
        assertEquals(MetaValueSource.META_EXPLICIT,
            relation.sourcedValue(MetaDescriptorProperties.TARGET_ENTITY).source());
        assertEquals(MetaValueSource.INFERRED,
            relation.sourcedValue(MetaDescriptorProperties.TARGET_SERVICE).source());
        assertEquals(RelationResolutionStatus.PARTIALLY_RESOLVED, relation.resolutionStatus());
    }

    @Test
    void 类引用与实体本身使用相同的名称及应用默认服务规则() {
        ReflectiveEntMetaParser defaultParser = new ReflectiveEntMetaParser(
            Collections.emptyList(), new MetaEntityDefaults("commerce"));
        EntEntityDescriptor target = defaultParser.parse(URLProduct.class);
        EntRelationDescriptor relation = defaultParser.parse(DefaultProductOrder.class).relations().get(0);

        assertEquals("urlProduct", relation.targetEntity());
        assertEquals(target.entityName(), relation.targetEntity());
        assertEquals(target.serviceName(), relation.targetService());
        assertEquals("commerce", relation.targetService());
        assertNull(parser.parse(DefaultProductOrder.class).relations().get(0).targetService());
    }

    @Test
    void 无本地类时可显式声明目标标识及服务() {
        EntRelationDescriptor relation = parser.parse(RemoteOrder.class).relations().get(0);

        assertEquals("remote_product", relation.targetEntity());
        assertNull(relation.targetClass());
        assertEquals("remote-catalog", relation.targetService());
        assertEquals("code", relation.targetField());
        assertEquals(MetaValueSource.META_EXPLICIT,
            relation.sourcedValue(MetaDescriptorProperties.TARGET_SERVICE).source());
    }

    @Test
    void 目标标识相同也不允许同时声明类和字符串() {
        assertInvalid(ConflictingTargetOrder.class, MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT,
            MetaDescriptorProperties.TARGET_ENTITY);
    }

    @Test
    void 目标类必须具有实体注解() {
        assertInvalid(InvalidTargetOrder.class, MetaDiagnosticCode.RELATION_TARGET_CLASS_INVALID,
            MetaDescriptorProperties.TARGET_ENTITY);
    }

    @Test
    void 目标类服务与显式服务不一致时拒绝解析() {
        assertInvalid(ConflictingServiceOrder.class, MetaDiagnosticCode.EXPLICIT_VALUE_CONFLICT,
            MetaDescriptorProperties.TARGET_SERVICE);
    }

    @Test
    void 显式服务与目标类一致时保留显式来源() {
        EntRelationDescriptor relation = parser.parse(ExplicitServiceOrder.class).relations().get(0);

        assertEquals("catalog", relation.targetService());
        assertEquals(MetaValueSource.META_EXPLICIT,
            relation.sourcedValue(MetaDescriptorProperties.TARGET_SERVICE).source());
    }

    @Test
    void 未声明目标时拒绝解析() {
        assertInvalid(MissingTargetOrder.class, MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND,
            MetaDescriptorProperties.TARGET_ENTITY);
    }

    @Test
    void 双向及自引用关系仅解析目标身份() {
        assertEquals("cyclicChild", parser.parse(CyclicParent.class).relations().get(0).targetEntity());
        assertEquals("cyclicParent", parser.parse(CyclicChild.class).relations().get(0).targetEntity());
        assertEquals("treeNode", parser.parse(TreeNode.class).relations().get(0).targetEntity());
    }

    private void assertInvalid(Class<?> entityClass, MetaDiagnosticCode code, String property) {
        MetaDiagnosticResult<EntEntityDescriptor> result = parser.parseWithDiagnostics(entityClass);

        assertTrue(result.hasErrors());
        assertEquals(RelationResolutionStatus.INVALID, result.value().relations().get(0).resolutionStatus());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
            diagnostic.code() == code && property.equals(diagnostic.property())));
        assertThrows(MetaDiagnosticException.class, () -> parser.parse(entityClass));
    }

    @EntEntity(entity = "catalog_product", service = "catalog")
    private static class Product {
        Long id;
    }

    @EntEntity
    private static class ProductOrder {
        @EntRelation(Product.class)
        Long productId;
    }

    @EntEntity
    private static class URLProduct {
        Long id;
    }

    @EntEntity
    private static class DefaultProductOrder {
        @EntRelation(URLProduct.class)
        Long productId;
    }

    @EntEntity
    private static class RemoteOrder {
        @EntRelation(targetEntity = "remote_product", targetService = "remote-catalog", targetField = "code")
        String productCode;
    }

    @EntEntity
    private static class ConflictingTargetOrder {
        @EntRelation(value = Product.class, targetEntity = "catalog_product")
        Long productId;
    }

    @EntEntity
    private static class InvalidTargetOrder {
        @EntRelation(String.class)
        Long productId;
    }

    @EntEntity
    private static class ConflictingServiceOrder {
        @EntRelation(value = Product.class, targetService = "other-catalog")
        Long productId;
    }

    @EntEntity
    private static class ExplicitServiceOrder {
        @EntRelation(value = Product.class, targetService = "catalog")
        Long productId;
    }

    @EntEntity
    private static class MissingTargetOrder {
        @EntRelation
        Long productId;
    }

    @EntEntity
    private static class CyclicParent {
        Long id;
        @EntRelation(CyclicChild.class)
        Long childId;
    }

    @EntEntity
    private static class CyclicChild {
        Long id;
        @EntRelation(CyclicParent.class)
        Long parentId;
    }

    @EntEntity
    private static class TreeNode {
        Long id;
        @EntRelation(TreeNode.class)
        Long parentId;
    }
}
