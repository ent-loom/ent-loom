package com.entloom.crud.core.convention;

import com.entloom.meta.contract.contribution.Contribution;
import java.util.Collection;

/**
 * CRUD 约定扩展点。
 *
 * <p>约定只产生 Contribution，由公共属性级 Resolver 统一裁决。</p>
 */
public interface CrudConvention {
    Collection<? extends Contribution<?>> contribute(CrudConventionContext context);
}
