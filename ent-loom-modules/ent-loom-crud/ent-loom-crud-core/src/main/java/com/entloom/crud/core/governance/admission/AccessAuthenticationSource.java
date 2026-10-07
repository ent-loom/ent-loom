package com.entloom.crud.core.governance.admission;

/**
 * 可信接入层的按次凭证认证 SPI，不依赖 Servlet 或业务 payload。
 *
 * <p>只有未提供凭证才返回 ABSENT；伪造、过期、格式错误及其他验证失败必须返回 INVALID。
 * 已认证结果必须保留实际主体，身份选择仍交由注册解析器核验归属。
 * 实现不得依照认证模式跳过已提供的凭证，也不得在单例中保存当前请求认证结果。</p>
 */
@FunctionalInterface
public interface AccessAuthenticationSource {
    CredentialAuthentication authenticate();
}
