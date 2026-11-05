package com.entloom.ddl.api;

/**
 * 文本业务容量与显式物理长度，均按字符计数。
 *
 * <p>-1 表示未声明；物理类型的字节容量由方言结合有效字符集判断。
 * 来源记录声明路径，不依赖通用 Meta 模型，也不把默认长度当成业务约束。</p>
 */
public final class DdlTextCapacity {
    private final int businessMaxLength;
    private final String businessSource;
    private final int physicalLength;
    private final String physicalSource;

    public DdlTextCapacity(int businessMaxLength, String businessSource,
                           int physicalLength, String physicalSource) {
        if (businessMaxLength < -1 || businessMaxLength == 0) {
            throw new IllegalArgumentException("业务文本最大字符数必须为 -1 或正整数");
        }
        if (physicalLength < -1 || physicalLength == 0) {
            throw new IllegalArgumentException("显式物理文本长度必须为 -1 或正整数");
        }
        this.businessMaxLength = businessMaxLength;
        this.businessSource = source(businessMaxLength, businessSource);
        this.physicalLength = physicalLength;
        this.physicalSource = source(physicalLength, physicalSource);
    }

    public static DdlTextCapacity unspecified() {
        return new DdlTextCapacity(-1, "", -1, "");
    }

    public int businessMaxLength() {
        return businessMaxLength;
    }

    public String businessSource() {
        return businessSource;
    }

    public int physicalLength() {
        return physicalLength;
    }

    public String physicalSource() {
        return physicalSource;
    }

    private static String source(int length, String source) {
        if (length == -1) {
            return "";
        }
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("已声明文本长度必须携带来源");
        }
        return source.trim();
    }
}
