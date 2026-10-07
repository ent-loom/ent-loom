package com.example.minicommerce.merchant.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 仅用于本地演示的令牌账号表；正式接入替换认证来源和身份归属解析器。 */
@Validated
@ConfigurationProperties("demo.merchant")
public record MerchantDemoProperties(@NotEmpty List<@Valid Account> accounts) {
    public record Account(@NotBlank String token, @NotBlank String subjectId,
                          @NotNull @Positive Long merchantId) {}
}
