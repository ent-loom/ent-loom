package com.entloom.crud.core.governance.service;

import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * 统一治理主链服务。
 */
public interface CrudGovernanceService {
    <R> CrudGovernanceResult<QueryExecutionSpec<R>> governQuery(QuerySpec<R> spec);

    <P> CrudGovernanceResult<CommandExecutionSpec<P>> governCommand(CommandSpec<P> spec);

    <S extends BaseSpec & GovernableSpec<S>> CrudGovernanceResult<S> governStats(S spec);

    CrudGovernanceResult<ImportSpec> governImport(ImportSpec spec);

    CrudGovernanceResult<ExportSpec> governExport(ExportSpec spec);

    void recordAllow(CrudGovernanceResult<?> result);

    void recordExecutionFailure(CrudGovernanceResult<?> result, Throwable throwable);
}
