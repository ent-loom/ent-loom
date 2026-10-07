package com.example.minicommerce.customer.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 用户端演示账号，客户归属来自可信配置。 */
@Validated
@ConfigurationProperties("demo.customer")
public record CustomerDemoProperties(@NotEmpty List<@NotNull @Valid Account> accounts) {
    public record Account(@NotBlank String token, @NotBlank String subjectId,
                          @NotNull @Positive Long customerId) {}
}
