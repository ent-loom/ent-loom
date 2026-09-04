package com.entloom.crud.runtime.adapter;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.exporting.DefaultExportEngine;
import com.entloom.crud.core.capability.exporting.DefaultExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.ExportColumn;
import com.entloom.crud.core.capability.exporting.ExportFileWriter;
import com.entloom.crud.core.capability.exporting.ExportFormatDescriptor;
import com.entloom.crud.core.capability.exporting.ExportResult;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.exporting.ExportTable;
import com.entloom.crud.core.capability.importing.DefaultImportEngine;
import com.entloom.crud.core.capability.importing.DefaultImportFormatRegistry;
import com.entloom.crud.core.capability.importing.ImportErrorFileWriter;
import com.entloom.crud.core.capability.importing.ImportFileParser;
import com.entloom.crud.core.capability.importing.ImportFormatDescriptor;
import com.entloom.crud.core.capability.importing.ImportFormatRegistry;
import com.entloom.crud.core.capability.importing.ImportMode;
import com.entloom.crud.core.capability.importing.ImportParsedTable;
import com.entloom.crud.core.capability.importing.ImportResult;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.runtime.inmemory.file.InMemoryFileStore;
import com.entloom.runtime.inmemory.task.InMemoryTaskStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证 CRUD 实际导入/导出引擎经过 runtime adapter 的最小闭环。 */
class RuntimeAdapterEngineContractTest {
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void importEngineCompletesSuccessfullyWithoutResultFile() {
        InMemoryFileStore runtimeFiles = new InMemoryFileStore(CLOCK);
        FileService fileService = new RuntimeFileServiceAdapter(runtimeFiles);
        TaskService taskService = new RuntimeTaskServiceAdapter(
            new InMemoryTaskStore(), runtimeFiles, new RuntimeSubjectContextMapper(), CLOCK
        );
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
            .contentType("application/octet-stream")
            .content(new byte[] {1})
            .attributes(fileAttributes("IMPORT_SOURCE"))
            .build());

        ImportResult result = engine.execute(ImportSpec.builder()
            .operation(ImportOperation.SUBMIT)
            .mode(ImportMode.INSERT)
            .rootType(OrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(OrderEntity.class))
            .format("test")
            .sourceFile(source)
            .subject(subject())
            .transactionPolicy(com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy.NONE)
            .build());

        assertEquals(1, result.getInsertedRows());
        assertEquals(CrudTaskStatus.SUCCEEDED, result.getTask().getStatus());
        assertEquals(null, result.getTask().getResultFile());
        assertEquals(CommandOperation.CREATE_BATCH, commandEngine.lastOperation);
        assertEquals("ORD-1", commandEngine.lastPayload.get("orderNo"));
    }

    @Test
    void importEngineMapsErrorFileThroughRuntimeAdapter() throws Exception {
        InMemoryFileStore runtimeFiles = new InMemoryFileStore(CLOCK);
        FileService fileService = new RuntimeFileServiceAdapter(runtimeFiles);
        TaskService taskService = new RuntimeTaskServiceAdapter(
            new InMemoryTaskStore(), runtimeFiles, new RuntimeSubjectContextMapper(), CLOCK
        );
        DefaultImportEngine engine = new DefaultImportEngine(
            importRegistry(parsedTable("unknown", "orderNo", "x", "ORD-1")),
            fileService,
            taskService,
            new RecordingCommandEngine(),
            new SingleMetaRegistry()
        );
        FileRef source = fileService.save(FileWriteRequest.builder()
            .fileName("orders.xlsx")
            .contentType("application/octet-stream")
            .content(new byte[] {1})
            .attributes(fileAttributes("IMPORT_SOURCE"))
            .build());

        ImportResult result = engine.execute(ImportSpec.builder()
            .operation(ImportOperation.VALIDATE)
            .rootType(OrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(OrderEntity.class))
            .format("test")
            .sourceFile(source)
            .subject(subject())
            .build());

        assertEquals(CrudTaskStatus.SUCCEEDED, result.getTask().getStatus());
        assertNotNull(result.getErrorFile());
        assertEquals(result.getErrorFile().getFileId(), result.getTask().getErrorFile().getFileId());
        assertEquals("errors=1", new String(fileService.read(result.getErrorFile()), StandardCharsets.UTF_8));
    }

    @Test
    void exportEngineStoresAndReadsResultThroughRuntimeAdapter() throws Exception {
        InMemoryFileStore runtimeFiles = new InMemoryFileStore(CLOCK);
        FileService fileService = new RuntimeFileServiceAdapter(runtimeFiles);
        TaskService taskService = new RuntimeTaskServiceAdapter(
            new InMemoryTaskStore(), runtimeFiles, new RuntimeSubjectContextMapper(), CLOCK
        );
        DefaultExportEngine engine = new DefaultExportEngine(
            new StaticQueryEngine(),
            new DefaultExportFormatRegistry(Collections.singletonList(exportDescriptor())),
            fileService,
            taskService,
            new SingleMetaRegistry()
        );

        ExportResult result = engine.execute(ExportSpec.builder()
            .operation(ExportOperation.SUBMIT)
            .scene("order.export")
            .rootType(OrderEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(OrderEntity.class))
            .format("test")
            .fields(Collections.singletonList("orderNo"))
            .subject(subject())
            .build());

        assertEquals(CrudTaskStatus.SUCCEEDED, result.getTask().getStatus());
        assertNotNull(result.getFile());
        assertEquals(result.getFile().getFileId(), result.getTask().getResultFile().getFileId());
        assertEquals("orderNo\nORD-1", new String(fileService.read(result.getFile()), StandardCharsets.UTF_8));
    }

    private static Map<String, Object> fileAttributes(String purpose) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("purpose", purpose);
        attributes.put("subjectId", "tester");
        attributes.put("tenantId", "tenant-a");
        return attributes;
    }

    private static SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        subject.setTenantId("tenant-a");
        return subject;
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
            new ImportFileParser() {
                @Override
                public ImportParsedTable parse(ImportSpec spec, byte[] content) {
                    return table;
                }
            },
            new ImportErrorFileWriter() {
                @Override
                public com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest writeErrorFile(
                    ImportSpec spec, ImportResult result
                ) {
                    byte[] content = ("errors=" + result.getRowErrors().size()).getBytes(StandardCharsets.UTF_8);
                    return com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest.builder()
                        .fileName("errors.txt")
                        .contentType("text/plain")
                        .inputStream(new ByteArrayInputStream(content))
                        .size(Long.valueOf(content.length))
                        .build();
                }
            }
        )));
    }

    private static ImportParsedTable parsedTable(
        String firstHeader, String secondHeader, String firstValue, String secondValue
    ) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(firstHeader, firstValue);
        values.put(secondHeader, secondValue);
        return new ImportParsedTable(
            Arrays.asList(firstHeader, secondHeader),
            Collections.singletonList(new ImportParsedTable.ImportParsedRow(2, values))
        );
    }

    private static final class StaticQueryEngine implements com.entloom.crud.core.capability.query.engine.QueryEngine {
        @Override
        public <R> PageResult<R> page(com.entloom.crud.core.capability.query.spec.QuerySpec<R> spec) {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> List<R> list(com.entloom.crud.core.capability.query.spec.QuerySpec<R> spec) {
            assertEquals(QueryOperation.LIST, spec.getOp());
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", Long.valueOf(1));
            row.put("orderNo", "ORD-1");
            return (List<R>) Collections.singletonList(CrudRecord.copyOf(row));
        }

        @Override
        public <R> R detail(com.entloom.crud.core.capability.query.spec.QuerySpec<R> spec) {
            return null;
        }
    }

    private static final class CsvExportWriter implements ExportFileWriter {
        @Override
        public com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest write(
            ExportSpec spec, ExportTable table
        ) {
            List<ExportColumn> columns = table.getColumns();
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    content.append(',');
                }
                content.append(columns.get(i).getHeader());
            }
            content.append('\n').append(table.getRows().get(0).get(columns.get(0).getKey()));
            byte[] bytes = content.toString().getBytes(StandardCharsets.UTF_8);
            return com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest.builder()
                .fileName("orders.txt")
                .contentType("text/plain")
                .inputStream(new ByteArrayInputStream(bytes))
                .size(Long.valueOf(bytes.length))
                .build();
        }
    }

    private static final class RecordingCommandEngine implements CommandEngine {
        private CommandOperation lastOperation;
        private Map<String, Object> lastPayload;

        @Override
        @SuppressWarnings("unchecked")
        public <P, R> R action(CommandSpec<P> spec) {
            lastOperation = spec.getOp();
            BatchCommand<Map<String, Object>> batch = (BatchCommand<Map<String, Object>>) spec.getPayload();
            WriteCommand<Map<String, Object>> item = batch.getItems().get(0);
            lastPayload = item.getValues();
            Map<String, Object> itemResult = new LinkedHashMap<String, Object>();
            itemResult.put("operation", "CREATE");
            itemResult.put("rows", Integer.valueOf(1));
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("items", Collections.singletonList(itemResult));
            return (R) result;
        }
    }

    private static final class SingleMetaRegistry implements EntityMetaRegistry {
        private final EntityMeta meta = meta();

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
            return RelationGraph.empty();
        }

        @Override
        public void validateOrThrow() {
        }

        private static EntityMeta meta() {
            Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
            fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
            fields.put("orderNo", new EntityFieldMeta("orderNo", String.class, "order_no", false, false, true, true));
            return new EntityMeta(
                OrderEntity.class,
                new ResourceDescriptor(OrderEntity.class, "OrderEntity", "test", null),
                "test_order",
                "id",
                "deleted",
                fields
            );
        }
    }

    private static final class OrderEntity {
    }
}
