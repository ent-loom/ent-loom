package com.entloom.meta.contract.value;

/**
 * 元数据值来源。
 */
public enum MetaValueSource {
    /** 业务显式 Registry / Provider 覆盖。 */
    BUSINESS_EXPLICIT_OVERRIDE,
    /** Meta 注解或 Meta 描述显式声明。 */
    META_EXPLICIT,
    /** 子框架原生注解显式声明。 */
    NATIVE_EXPLICIT,
    /** 业务默认配置或 SPI 补充值。 */
    BUSINESS_DEFAULT_CONFIG,
    /** 框架按命名、Java 类型或上下文推断。 */
    INFERRED,
    /** 框架默认值。 */
    DEFAULT,
    /** Java 注解默认值导致无法区分显式默认值和未设置。 */
    DEFAULT_OR_EXPLICIT_UNKNOWN
}
