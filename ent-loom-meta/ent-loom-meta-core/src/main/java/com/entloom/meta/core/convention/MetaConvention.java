package com.entloom.meta.core.convention;

import com.entloom.meta.contract.contribution.Contribution;
import java.util.Collection;

/**
 * Meta Convention 扩展点。
 *
 * <p>Convention 只产生候选 Contribution，不直接修改 Descriptor。</p>
 */
public interface MetaConvention {
    Collection<? extends Contribution<?>> contribute(MetaConventionContext context);
}
