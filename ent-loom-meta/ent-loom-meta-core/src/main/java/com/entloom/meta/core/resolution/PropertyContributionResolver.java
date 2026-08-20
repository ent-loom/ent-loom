package com.entloom.meta.core.resolution;

import com.entloom.meta.contract.contribution.Contribution;
import com.entloom.meta.contract.contribution.MetaConflictPolicy;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticResult;
import java.util.Collection;
import java.util.Map;

/**
 * Meta Core 对公共属性级裁决器的兼容入口。
 */
public final class PropertyContributionResolver {
    private final com.entloom.meta.contract.contribution.PropertyContributionResolver delegate;

    public PropertyContributionResolver() {
        this(MetaConflictPolicy.FAIL);
    }

    public PropertyContributionResolver(MetaConflictPolicy conflictPolicy) {
        this.delegate = new com.entloom.meta.contract.contribution.PropertyContributionResolver(conflictPolicy);
    }

    public MetaDiagnosticResult<Map<String, Contribution<?>>> resolve(
        Collection<? extends Contribution<?>> contributions
    ) {
        return delegate.resolve(contributions);
    }
}
