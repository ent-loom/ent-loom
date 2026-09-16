package com.entloom.crud.starter.web.dto;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudNullFieldMode;
import com.entloom.crud.api.enums.CrudReadResultMode;
import com.entloom.crud.api.enums.PageCountMode;
import com.entloom.crud.core.exception.CrudException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * page/list/detail 专属查询选项。
 */
@Getter
@Setter
public class CrudReadOptions extends CrudQueryOptions {
    /** 结果模式。 */
    private CrudReadResultMode resultMode;
    /** 少量定制 viewType。 */
    private String viewType;
    /** 分页总数策略。 */
    private PageCountMode countMode = PageCountMode.EXACT;
    /** 空字段响应模式；不传时使用全局默认值。 */
    private CrudNullFieldMode nullFieldMode;
    /** resultMode 原始字符串。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String rawResultMode;
    /** countMode 原始字符串。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String rawCountMode;
    /** nullFieldMode 原始字符串。 */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String rawNullFieldMode;

    @JsonSetter("resultMode")
    public void setRawResultMode(String resultMode) {
        this.rawResultMode = trimToNull(resultMode);
        this.resultMode = CrudReadResultMode.from(this.rawResultMode);
    }

    @JsonSetter("countMode")
    public void setRawCountMode(String countMode) {
        this.rawCountMode = trimToNull(countMode);
        this.countMode = this.rawCountMode == null ? PageCountMode.EXACT : PageCountMode.from(this.rawCountMode);
    }

    @JsonSetter("nullFieldMode")
    public void setRawNullFieldMode(String nullFieldMode) {
        this.rawNullFieldMode = trimToNull(nullFieldMode);
        this.nullFieldMode = CrudNullFieldMode.from(this.rawNullFieldMode);
    }

    @JsonIgnore
    public CrudReadResultMode resolveResultMode() {
        if (rawResultMode != null && resultMode == null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "不支持 options.resultMode: " + rawResultMode + "，仅支持 ENTITY 或 MAP");
        }
        return resultMode;
    }

    @JsonIgnore
    public PageCountMode resolveCountMode() {
        if (rawCountMode != null && countMode == null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "不支持 options.countMode: " + rawCountMode + "，仅支持 EXACT 或 NONE");
        }
        return countMode == null ? PageCountMode.EXACT : countMode;
    }

    @JsonIgnore
    public CrudNullFieldMode resolveNullFieldMode() {
        if (rawNullFieldMode != null && nullFieldMode == null) {
            throw new CrudException(
                CrudErrorCode.VALIDATION_ERROR,
                "不支持 options.nullFieldMode: " + rawNullFieldMode + "，仅支持 INCLUDE 或 OMIT"
            );
        }
        return nullFieldMode;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
