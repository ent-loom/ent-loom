package com.entloom.crud.core.governance.policy;

/** Scene Policy 结构化匹配结果。 */
public final class ScenePolicyMatch {
    private final boolean required;
    private final boolean matched;
    private final String accessEntry;
    private final String portal;
    private final String capability;
    private final String rejectionReason;

    private ScenePolicyMatch(boolean required, boolean matched, String accessEntry, String portal, String capability, String rejectionReason) {
        this.required = required;
        this.matched = matched;
        this.accessEntry = accessEntry;
        this.portal = portal;
        this.capability = capability;
        this.rejectionReason = rejectionReason;
    }

    public static ScenePolicyMatch skipped(String accessEntry, String portal) { return new ScenePolicyMatch(false, false, accessEntry, portal, null, null); }
    public static ScenePolicyMatch matched(String accessEntry, String portal, String capability) { return new ScenePolicyMatch(true, true, accessEntry, portal, capability, null); }
    public static ScenePolicyMatch rejected(String accessEntry, String portal, String reason) { return new ScenePolicyMatch(true, false, accessEntry, portal, null, reason); }
    public boolean isRequired() { return required; }
    public boolean isMatched() { return matched; }
    public String getAccessEntry() { return accessEntry; }
    public String getPortal() { return portal; }
    public String getCapability() { return capability; }
    public String getRejectionReason() { return rejectionReason; }
}
