package com.entloom.crud.core.governance.identity;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.BusinessIdentity;

/**
 * 项目身份的可信事实解析 SPI。
 *
 * <p>必须核验身份属于实际主体且当前有效，返回不可变快照；无法确认时返回 null 或抛出异常。
 * identityId 只是待验证的选择，不能直接据此构造身份或授予范围。
 * 实现不得在单例成员保存请求主体或当前身份，也不得从客户端属性恢复认证结果。</p>
 */
@FunctionalInterface
public interface BusinessIdentityResolver<I extends BusinessIdentity> {
    I resolve(AccessSubject subject, String identityId);
}
