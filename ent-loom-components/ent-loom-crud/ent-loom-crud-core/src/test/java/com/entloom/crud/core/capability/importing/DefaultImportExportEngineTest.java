package com.entloom.crud.core.capability.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.exporting.DefaultExportEngine;
import com.entloom.crud.core.capability.exporting.ExportColumn;
import com.entloom.crud.core.capability.exporting.DefaultExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.ExportFileWriter;
import com.entloom.crud.core.capability.exporting.ExportFormatDescriptor;
import com.entloom.crud.core.capability.exporting.ExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.ExportResult;
import com.entloom.crud.core.capability.exporting.ExportRenderOptions;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.exporting.ExportTable;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import com.entloom.crud.core.foundation.taskfile.InMemoryFileService;
import com.entloom.crud.core.foundation.taskfile.InMemoryTaskService;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.time.Instant;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultImportExportEngineTest {
    @Test
    void exportSubmitReadsAuthorizedFieldsAndStoresFile() {
        FileService fileService = new InMemoryFileService();
        TaskService taskService = new InMemoryTaskService();
        ExportFormatRegistry formatRegistry = new DefaultExportFormatRegistry(Collections.singletonList(exportDescriptor()));
        DefaultExportEngine engine = new DefaultExportEngine(
            new StaticQueryEngine(),
            formatRegistry,
            fileService,
            taskService,
            new SingleMetaRegistry()
        );

        ExportResult result = engine.execute(ExportSpec.builder()
            .operation(ExportOperation.SUBMIT)
            .rootType(OrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(OrderEntity.class))
            .format("test")
            .fields(Arrays.asList("orderNo"))
            .limit(Integer.valueOf(5))
            .build());

        assertNotNull(result.getTask());
        assertNotNull(result.getFile());
        assertEquals(1, result.getTotalRows());
        assertEquals("orderNo\nORD-1", new String(fileService.read(result.getFile()), StandardCharsets.UTF_8));
    }

    @Test
    void exportPreviewUsesColumnContractAndDisplayRendering() {
        DefaultExportEngine engine = new DefaultExportEngine(
            new StaticQueryEngine(),
            new DefaultExportFormatRegistry(Collections.singletonList(exportDescriptor())),
            new InMemoryFileService(),
            new InMemoryTaskService(),
            new SingleMetaRegistry()
        );

        ExportResult result = engine.execute(ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(OrderEntity.class))
            .format("test")
            .renderOptions(new ExportRenderOptions("Asia/Shanghai"))
            .build());

        assertEquals(Arrays.asList("orderNo", "studentName", "enabled", "status", "createdAt"), columnKeys(result));
        Map<String, Object> row = result.getPreviewRows().get(0);
        assertEquals("ORD-1", row.get("orderNo"));
        assertEquals("Alice", row.get("studentName"));
        assertEquals("是", row.get("enabled"));
        assertEquals("表扬", row.get("status"));
        assertEquals("2026-05-02 08:00:00", row.get("createdAt"));
    }

    @Test
    void exportExplicitInternalAndLogicDeleteFieldsFailClosed() {
        DefaultExportEngine engine = new DefaultExportEngine(
            new StaticQueryEngine(),
            new DefaultExportFormatRegistry(Collections.singletonList(exportDescriptor())),
            new InMemoryFileService(),
            new InMemoryTaskService(),
            new SingleMetaRegistry()
        );

        assertThrows(CrudException.class, () -> engine.execute(ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .format("test")
            .fields(Arrays.asList("createdBy"))
            .build()));
        assertThrows(CrudException.class, () -> engine.execute(ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .format("test")
            .fields(Arrays.asList("deleted"))
            .build()));
        assertThrows(CrudException.class, () -> engine.execute(ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .format("test")
            .fields(Arrays.asList("id"))
            .build()));
        assertThrows(CrudException.class, () -> engine.execute(ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .format("test")
            .fields(Arrays.asList("studentId"))
            .build()));
    }

    @Test
    void exportInvalidRenderTimezoneFailsClosed() {
        DefaultExportEngine engine = new DefaultExportEngine(
            new StaticQueryEngine(),
            new DefaultExportFormatRegistry(Collections.singletonList(exportDescriptor())),
            new InMemoryFileService(),
            new InMemoryTaskService(),
            new SingleMetaRegistry()
        );

        assertThrows(ValidationException.class, () -> engine.execute(ExportSpec.builder()
            .operation(ExportOperation.PREVIEW)
            .rootType(OrderEntity.class)
            .format("test")
            .renderOptions(new ExportRenderOptions("Mars/Base"))
            .build()));
    }

    @Test
    void importSubmitParsesAndWritesRowsThroughCommandEngine() {
        FileService fileService = new InMemoryFileService();
        TaskService taskService = new InMemoryTaskService();
        RecordingCommandEngine commandEngine = new RecordingCommandEngine();
        DefaultImportEngine engine = new DefaultImportEngine(
            importRegistry(parsedTable("id", "orderNo", "1", "ORD-1")),
            fileService,
            taskService,
            commandEngine,
            new SingleMetaRegistry()
        );
        FileRef source = fileService.save(FileWriteRequest.builder()
            .fileName("orders.xlsx")
            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .content(new byte[] {1})
            .build());

        ImportResult result = engine.execute(ImportSpec.builder()
            .operation(com.entloom.crud.api.enums.ImportOperation.SUBMIT)
            .mode(ImportMode.INSERT)
            .rootType(OrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(OrderEntity.class))
            .format("test")
            .sourceFile(source)
            .build());

        assertEquals(1, result.getInsertedRows());
        assertEquals(0, result.getFailedRows());
        assertEquals(CommandOperation.CREATE_BATCH, commandEngine.lastOperation);
        assertEquals("ORD-1", commandEngine.lastPayload.get("orderNo"));
    }

    @Test
    void importValidationWritesErrorFileWhenFieldIsNotAllowed() {
        FileService fileService = new InMemoryFileService();
        TaskService taskService = new InMemoryTaskService();
        DefaultImportEngine engine = new DefaultImportEngine(
            importRegistry(parsedTable("badField", "value", "x", "1")),
            fileService,
            taskService,
            new RecordingCommandEngine(),
            new SingleMetaRegistry()
        );
        FileRef source = fileService.save(FileWriteRequest.builder()
            .fileName("orders.xlsx")
            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .content(new byte[] {1})
            .build());

        ImportResult result = engine.execute(ImportSpec.builder()
            .operation(com.entloom.crud.api.enums.ImportOperation.VALIDATE)
            .rootType(OrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(OrderEntity.class))
            .format("test")
            .sourceFile(source)
            .build());

        assertEquals(0, result.getValidRows());
        assertEquals(2, result.getRowErrors().size());
        assertEquals(CrudTaskStatus.SUCCEEDED, result.getTask().getStatus());
        assertNotNull(result.getErrorFile());
        assertEquals("errors=2", new String(fileService.read(result.getErrorFile()), StandardCharsets.UTF_8));
    }

    private static ExportFormatDescriptor exportDescriptor() {
        return new ExportFormatDescriptor("test", "Test", "text/plain", "txt", new CsvExportWriter());
    }

    private static ImportFormatRegistry importRegistry(final ImportParsedTable table) {
        return new DefaultImportFormatRegistry(Collections.singletonList(new ImportFormatDescriptor(
            "test",
            "Test",
            "application/octet-stream",
            "bin",
            (spec, content) -> table,
            (spec, result) -> FileWriteRequest.builder()
                .fileName("errors.txt")
                .contentType("text/plain")
                .content(("errors=" + result.getRowErrors().size()).getBytes(StandardCharsets.UTF_8))
                .build()
        )));
    }

    private static ImportParsedTable parsedTable(String firstHeader, String secondHeader, String firstValue, String secondValue) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(firstHeader, firstValue);
        values.put(secondHeader, secondValue);
        return new ImportParsedTable(
            Arrays.asList(firstHeader, secondHeader),
            Collections.singletonList(new ImportParsedTable.ImportParsedRow(2, values))
        );
    }

    private static final class StaticQueryEngine implements QueryEngine {
        @Override
        public <R> PageResult<R> page(QuerySpec<R> spec) {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> List<R> list(QuerySpec<R> spec) {
            assertEquals(QueryOperation.LIST, spec.getOp());
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", Long.valueOf(1));
            row.put("orderNo", "ORD-1");
            row.put("studentId", Long.valueOf(10));
            row.put("studentName", "Alice");
            row.put("enabled", Integer.valueOf(1));
            row.put("status", "PRAISE");
            row.put("createdAt", Instant.parse("2026-05-02T00:00:00Z"));
            row.put("createdBy", "admin");
            return (List<R>) Collections.singletonList(CrudRecord.copyOf(row));
        }

        @Override
        public <R> R detail(QuerySpec<R> spec) {
            return null;
        }
    }

    private static final class CsvExportWriter implements ExportFileWriter {
        @Override
        public FileWriteRequest write(ExportSpec spec, ExportTable table) {
            StringBuilder builder = new StringBuilder();
            List<ExportColumn> columns = table.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(columns.get(i).getHeader());
            }
            builder.append("\n");
            Map<String, Object> row = table.getRows().get(0);
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(row.get(columns.get(i).getKey()));
            }
            return FileWriteRequest.builder()
                .fileName("orders.txt")
                .contentType("text/plain")
                .content(builder.toString().getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    private static final class RecordingCommandEngine implements CommandEngine {
        private CommandOperation lastOperation;
        private Map<String, Object> lastPayload;

        @SuppressWarnings("unchecked")
        @Override
        public <P, R> R action(CommandSpec<P> spec) {
            lastOperation = spec.getOp();
            BatchCommand<Map<String, Object>> batch = (BatchCommand<Map<String, Object>>) spec.getPayload();
            WriteCommand<Map<String, Object>> item = batch.getItems().get(0);
            lastPayload = item.getValues();
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("operation", "CREATE");
            result.put("rows", Integer.valueOf(1));
            Map<String, Object> itemResult = new LinkedHashMap<String, Object>();
            itemResult.put("operation", "CREATE");
            itemResult.put("rows", Integer.valueOf(1));
            result.put("items", Collections.singletonList(itemResult));
            return (R) result;
        }
    }

    private static final class SingleMetaRegistry implements EntityMetaRegistry {
        private final EntityMeta meta = meta();
        private final RelationGraph relationGraph = relationGraph();

        @Override
        public EntityMeta getEntityMeta(Class<?> entityType) {
            return meta;
        }

        @Override
        public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
            return meta.getResourceDescriptor();
        }

        @Override
        public RelationGraph getRelationGraph(Class<?> rootType) {
            return relationGraph;
        }

        @Override
        public void validateOrThrow() {
        }

        private static EntityMeta meta() {
            Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
            fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
            fields.put("orderNo", new EntityFieldMeta("orderNo", String.class, "order_no", false, false, true, true));
            fields.put("studentId", new EntityFieldMeta("studentId", Long.class, "student_id", true, false, true, true));
            fields.put("studentName", new EntityFieldMeta("studentName", String.class, "student_name", true, false, true, true));
            fields.put("enabled", new EntityFieldMeta("enabled", Boolean.class, "enabled", true, false, true, true));
            fields.put("status", new EntityFieldMeta("status", Status.class, "status", true, false, true, true));
            fields.put("createdAt", new EntityFieldMeta("createdAt", Instant.class, "created_at", true, false, true, true));
            fields.put("createdBy", new EntityFieldMeta("createdBy", String.class, "created_by", true, false, true, true));
            fields.put("version", new EntityFieldMeta("version", Integer.class, "version", true, false, true, true));
            fields.put("deleted", new EntityFieldMeta("deleted", Boolean.class, "deleted", true, false, true, true));
            return new EntityMeta(
                OrderEntity.class,
                new ResourceDescriptor(OrderEntity.class, "OrderEntity", "test", null),
                "test_order",
                "id",
                "deleted",
                fields
            );
        }

        private static RelationGraph relationGraph() {
            RelationEdge edge = new RelationEdge();
            edge.setFromEntity(OrderEntity.class);
            edge.setToEntity(StudentEntity.class);
            edge.setRelationField("student");
            edge.setFromField("studentId");
            edge.setToField("id");
            return RelationGraph.of(Collections.singletonList(edge));
        }
    }

    private static final class OrderEntity {
    }

    private static final class StudentEntity {
    }

    private enum Status {
        PRAISE("表扬");

        private final String desc;

        Status(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    private List<String> columnKeys(ExportResult result) {
        List<String> keys = new ArrayList<String>();
        for (ExportColumn column : result.getColumns()) {
            keys.add(column.getKey());
        }
        return keys;
    }
}
