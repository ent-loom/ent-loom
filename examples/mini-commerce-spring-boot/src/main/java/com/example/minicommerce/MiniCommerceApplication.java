package com.example.minicommerce;

import com.entloom.meta.starter.EntLoomMetaAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;

/** 最小商城示例启动入口。 */
@SpringBootApplication
@MapperScan("com.example.minicommerce.**.mapper")
@ImportAutoConfiguration(EntLoomMetaAutoConfiguration.class)
public class MiniCommerceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniCommerceApplication.class, args);
    }
}
