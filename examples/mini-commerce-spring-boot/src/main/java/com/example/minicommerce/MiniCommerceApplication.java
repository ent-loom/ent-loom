package com.example.minicommerce;

import com.entloom.meta.starter.EntLoomMetaAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/** 最小商城示例启动入口。 */
@SpringBootApplication
@ImportAutoConfiguration(EntLoomMetaAutoConfiguration.class)
public class MiniCommerceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniCommerceApplication.class, args);
    }
}
