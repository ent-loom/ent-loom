package com.entloom.crud.core.operation;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.CrudOperationMatrix;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.StatsOperation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudOperationKeyTest {
    @Test
    void should_build_key_from_scoped_operation() {
        Assertions.assertEquals(CrudOperationKey.of(CrudOperationDomain.QUERY, "PAGE"), CrudOperationKey.of(QueryOperation.PAGE));
        Assertions.assertEquals(CrudOperationKey.of(CrudOperationDomain.COMMAND, "CREATE"), CrudOperationKey.of(CommandOperation.CREATE));
        Assertions.assertEquals(CrudOperationKey.of(CrudOperationDomain.STATS, "QUERY"), CrudOperationKey.of(StatsOperation.QUERY));
        Assertions.assertEquals(CrudOperationKey.of(CrudOperationDomain.STATS, "PREVIEW"), CrudOperationKey.of(StatsOperation.PREVIEW));
        Assertions.assertEquals(CrudOperationKey.of(CrudOperationDomain.IMPORT, "SUBMIT"), CrudOperationKey.of(ImportOperation.SUBMIT));
        Assertions.assertEquals(CrudOperationKey.of(CrudOperationDomain.EXPORT, "DOWNLOAD"), CrudOperationKey.of(ExportOperation.DOWNLOAD));
    }

    @Test
    void should_normalize_and_reject_illegal_matrix() {
        Assertions.assertEquals("QUERY/PAGE", CrudOperationKey.of(CrudOperationDomain.QUERY, " page ").toString());

        Assertions.assertThrows(IllegalArgumentException.class, () -> CrudOperationKey.of(CrudOperationDomain.COMMAND, "PAGE"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CrudOperationKey.of(CrudOperationDomain.QUERY, "DELETE"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CrudOperationKey.of(CrudOperationDomain.STATS, "PAGE"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CrudOperationKey.of(CrudOperationDomain.IMPORT, "DOWNLOAD"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> CrudOperationKey.of(CrudOperationDomain.EXPORT, "COMMIT"));
    }

    @Test
    void should_expose_p0_capability_operation_matrix() {
        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList("PAGE", "LIST", "FIND_ONE", "DETAIL")),
            CrudOperationMatrix.legalOperations(CrudOperationDomain.QUERY)
        );
        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList(
                "CREATE",
                "UPDATE",
                "DELETE",
                "SAVE_OR_UPDATE",
                "CREATE_BATCH",
                "UPDATE_BATCH",
                "DELETE_BATCH",
                "SAVE_OR_UPDATE_BATCH",
                "ACTION"
            )),
            CrudOperationMatrix.legalOperations(CrudOperationDomain.COMMAND)
        );
        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList("QUERY", "PREVIEW")),
            CrudOperationMatrix.legalOperations(CrudOperationDomain.STATS)
        );
        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList("VALIDATE", "SUBMIT", "COMMIT", "CANCEL", "STATUS", "DOWNLOAD_ERROR")),
            CrudOperationMatrix.legalOperations(CrudOperationDomain.IMPORT)
        );
        Assertions.assertEquals(
            new LinkedHashSet<String>(Arrays.asList("SUBMIT", "DOWNLOAD", "STATUS", "CANCEL", "PREVIEW")),
            CrudOperationMatrix.legalOperations(CrudOperationDomain.EXPORT)
        );
    }
}
