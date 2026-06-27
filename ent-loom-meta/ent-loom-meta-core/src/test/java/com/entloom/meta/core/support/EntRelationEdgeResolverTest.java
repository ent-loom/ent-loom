package com.entloom.meta.core.support;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.core.model.EntRelationEdgeModel;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.core.spi.EntRelationEntityResolver;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EntRelationEdgeResolverTest {

    @Test
    void shouldResolveRelationEdgesFromEntRelation() {
        EntRelationEdgeResolver resolver = new EntRelationEdgeResolver(new ReflectiveEntMetaParser());

        List<EntRelationEdgeModel> edges = resolver.resolve(
            Arrays.<Class<?>>asList(ParentEntity.class, ChildEntity.class),
            new FixtureEntityResolver()
        );

        Assertions.assertEquals(1, edges.size());
        EntRelationEdgeModel edge = edges.get(0);
        Assertions.assertSame(ParentEntity.class, edge.fromEntity());
        Assertions.assertSame(ChildEntity.class, edge.toEntity());
        Assertions.assertEquals("childEntityList", edge.relationField());
        Assertions.assertEquals("id", edge.fromField());
        Assertions.assertEquals("parentId", edge.toField());
    }

    @Test
    void shouldFailWhenOneToManyCollectionFieldIsNotCanonical() {
        EntRelationEdgeResolver resolver = new EntRelationEdgeResolver(new ReflectiveEntMetaParser());

        IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> resolver.resolve(
                Arrays.<Class<?>>asList(LegacyParentEntity.class, LegacyChildEntity.class),
                new FixtureEntityResolver()
            )
        );

        Assertions.assertTrue(exception.getMessage().contains("LegacyParentEntity.legacyChildren"));
        Assertions.assertTrue(exception.getMessage().contains("legacyChildEntityList"));
    }

    @EntEntity(entity = "ParentEntity")
    private static class ParentEntity {
        private Long id;
        private List<ChildEntity> childEntityList;
    }

    @EntEntity(entity = "ChildEntity")
    private static class ChildEntity {
        private Long id;
        @EntRelation(targetEntity = "ParentEntity")
        private Long parentId;
    }

    @EntEntity(entity = "LegacyParentEntity")
    private static class LegacyParentEntity {
        private Long id;
        private List<LegacyChildEntity> legacyChildren;
    }

    @EntEntity(entity = "LegacyChildEntity")
    private static class LegacyChildEntity {
        private Long id;
        @EntRelation(targetEntity = "LegacyParentEntity")
        private Long parentId;
    }

    private static class FixtureEntityResolver implements EntRelationEntityResolver {
        @Override
        public Class<?> resolveEntityClass(String code) {
            if ("ParentEntity".equals(code)) {
                return ParentEntity.class;
            }
            if ("LegacyParentEntity".equals(code)) {
                return LegacyParentEntity.class;
            }
            return null;
        }

        @Override
        public String resolveIdField(Class<?> entityClass) {
            return entityClass == null ? null : "id";
        }

        @Override
        public boolean isAllowedField(Class<?> entityClass, String fieldName) {
            return fieldName != null && Collections.singleton("parentId").contains(fieldName);
        }
    }
}
