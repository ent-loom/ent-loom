package com.entloom.crud.starter.web.access;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 项目的 HTTP 可信认证适配 SPI；在已有登录/来源认证机制之后、Controller 之前调用。
 *
 * <p>必须验证已提供的凭证，无效或过期返回 INVALID，只有无凭证才返回 ABSENT。
 * 不得把 body、身份 Header 或请求属性中的主体当作认证结果，入口元数据由服务端决定。
 * 实现负责对接真实会话、Token 或设备认证；框架不提供猜测主体的默认实现。</p>
 */
@FunctionalInterface
public interface CrudHttpAuthenticationSource {
    HttpAccessAuthentication authenticate(HttpServletRequest request);
}
