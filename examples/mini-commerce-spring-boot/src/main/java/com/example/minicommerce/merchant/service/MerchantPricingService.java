package com.example.minicommerce.merchant.service;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.capability.command.patch.CommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationProvider;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWriteRequest;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.api.access.BusinessOperation;
import com.example.minicommerce.common.exception.BusinessException;
import com.example.minicommerce.merchant.enums.MerchantError;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.merchant.dto.ChangePriceItem;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 商品价格业务层只组装显式改价 Patch，归属、字段、版本和事务由框架执行。 */
@Service
@RequiredArgsConstructor
public class MerchantPricingService {
    private final AuthorizedQueryExecutor queries;
    private final EntityDaoAuthorizationProvider daos;
    private final CommandPayloadBinder patches = new DefaultCommandPayloadBinder();
    private final EntityMetaRegistry entities;

    public List<CrudRecord> list(VisitorContext visitor) {
        return queries.list(visitor, QuerySpec.<CrudRecord>builder().rootType(MerchantProduct.class)
            .resultType(CrudRecord.class).op(QueryOperation.LIST).limit(100).build());
    }

    // 此方法不加入外层事务；框架必须拥有最终提交边界，才能返回成功并记录成功审计。
    public List<Long> changePrices(VisitorContext visitor, List<ChangePriceItem> changes) {
        var type = EntityType.of(MerchantProduct.class, Long.class);
        var writer = daos.openBatchWrite(type, visitor, new BusinessOperation("merchantProduct", "update"),
            changes.stream().map(change -> change.id().toString()).toList());
        var meta = entities.getEntityMeta(MerchantProduct.class);
        var items = changes.stream().map(change -> EntityDaoBatchWriteRequest.Item.update(
            change.id().toString(), change.id(), patches.bindUpdatePatch(
                Map.of("id", change.id(), "price", change.price(), "version", change.version()),
                MerchantProduct.class, meta))).toList();
        try {
            writer.execute(EntityDaoBatchWriteRequest.of(writer.getAuthorization(), items));
        } catch (EntityDaoWriteMissException cause) {
            // 不区分不存在、跨商家或版本过期，避免泄露其他商家的数据存在性。
            throw new BusinessException(MerchantError.PRICE_UPDATE_REJECTED, cause);
        }
        return changes.stream().map(ChangePriceItem::id).toList();
    }
}
