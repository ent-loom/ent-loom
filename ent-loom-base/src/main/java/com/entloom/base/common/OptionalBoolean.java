package com.entloom.base.common;

/**
 * 三态开关，支持真/假/未设置。
 */
public enum OptionalBoolean {
    /** 未显式设置，交由默认策略处理。 */
    UNSET,
    /** 开启。 */
    TRUE,
    /** 关闭。 */
    FALSE
}
