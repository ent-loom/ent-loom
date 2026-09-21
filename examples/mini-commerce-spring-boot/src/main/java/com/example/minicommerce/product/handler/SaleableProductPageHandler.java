package com.example.minicommerce.product.handler;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.annotations.EntCrudQueryHandler;
import com.entloom.crud.core.capability.query.scene.QueryPageSceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.example.minicommerce.product.entity.Product;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 可售商品场景：追加业务约束，过滤、排序和分页仍委托默认查询引擎。 */
@Component
@EntCrudQueryHandler(entityClasses = Product.class, scenes = SaleableProductPageHandler.SCENE)
public class SaleableProductPageHandler implements QueryPageSceneHandler<Product> {
    public static final String SCENE = "saleable";

    @Override
    public Set<CrudRouteKey> routeKeys() {
        return Set.of(new CrudRouteKey(List.of(Product.class.getName()), CrudOperationKey.of(QueryOperation.PAGE), SCENE));
    }

    @Override
    public PageResult<Product> handle(QuerySpec<Product> spec,
        SceneDelegate<QuerySpec<Product>, PageResult<Product>> delegate) {
        var filters = spec.getFilters();
        // 与调用方条件取交集；即使传入 active=false，也不能查出停用商品。
        filters.add(new QueryFilter("active", FilterOperator.EQ, true));
        return delegate.invoke(spec.toBuilder().filters(filters).build());
    }
}
