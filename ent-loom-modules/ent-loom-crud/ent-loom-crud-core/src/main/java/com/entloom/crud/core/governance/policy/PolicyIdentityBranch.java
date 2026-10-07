package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.AccessBranch;
import java.util.Objects;

/** 模板规则的公共分支或单一项目身份分支，不合并多个身份。 */
public final class PolicyIdentityBranch {
    private static final PolicyIdentityBranch PUBLIC = new PolicyIdentityBranch(AccessBranch.PUBLIC, null);

    private final AccessBranch branch;
    private final String identityCode;

    private PolicyIdentityBranch(AccessBranch branch, String identityCode) {
        this.branch = branch;
        this.identityCode = identityCode;
    }

    public static PolicyIdentityBranch publicBranch() {
        return PUBLIC;
    }

    public static PolicyIdentityBranch identity(String identityCode) {
        return new PolicyIdentityBranch(AccessBranch.AUTHENTICATED, requireCode(identityCode, "身份 code"));
    }

    public AccessBranch getBranch() {
        return branch;
    }

    public String getIdentityCode() {
        return identityCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PolicyIdentityBranch)) {
            return false;
        }
        PolicyIdentityBranch that = (PolicyIdentityBranch) other;
        return branch == that.branch && Objects.equals(identityCode, that.identityCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(branch, identityCode);
    }

    @Override
    public String toString() {
        return branch == AccessBranch.PUBLIC ? "PUBLIC" : "IDENTITY:" + identityCode;
    }

    static String requireCode(String value, String name) {
        if (value == null || value.trim().isEmpty() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " 不能为空或包含首尾空格");
        }
        return value;
    }
}
