package com.entloom.meta.contract.convention;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 创建时间的全局命名约定；名称与时间类型同时匹配，不根据中文说明猜测语义。 */
public final class CreatedTimeFields {
    private final List<String> fieldNames;

    public CreatedTimeFields() {
        this(Arrays.asList("createTime", "createdAt"));
    }

    /** 显式列表整体替换缺省；空列表关闭按名称推导。 */
    public CreatedTimeFields(Collection<String> fieldNames) {
        Objects.requireNonNull(fieldNames, "创建时间字段约定不能为空");
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (String name : fieldNames) {
            if (name == null || name.trim().isEmpty() || !name.equals(name.trim())) {
                throw new IllegalArgumentException("创建时间字段名称不能为空或包含首尾空格");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("创建时间字段名称重复: " + name);
            }
        }
        this.fieldNames = Collections.unmodifiableList(new ArrayList<String>(names));
    }

    public List<String> fieldNames() {
        return fieldNames;
    }

    public boolean matches(String fieldName, Class<?> javaType) {
        return fieldNames.contains(fieldName) && (LocalDate.class.equals(javaType)
            || LocalDateTime.class.equals(javaType) || Instant.class.equals(javaType)
            || Date.class.equals(javaType));
    }
}
