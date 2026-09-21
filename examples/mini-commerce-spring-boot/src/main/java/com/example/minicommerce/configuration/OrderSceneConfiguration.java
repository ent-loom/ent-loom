package com.example.minicommerce.configuration;

import com.example.minicommerce.order.handler.PlaceOrderHandler;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.governance.policy.ScenePolicy;
import com.entloom.crud.core.governance.policy.ScenePolicyKey;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 声明下单 ACTION 的准入场景；主体是否有权执行仍由统一权限服务决定。 */
@Configuration(proxyBeanMethods = false)
public class OrderSceneConfiguration {
    @Bean
    ScenePolicy placeOrderScenePolicy() {
        return new ScenePolicy(new ScenePolicyKey("base", "order",
            CrudOperationKey.of(CommandOperation.ACTION), PlaceOrderHandler.SCENE), "place-order", Set.of());
    }
}
