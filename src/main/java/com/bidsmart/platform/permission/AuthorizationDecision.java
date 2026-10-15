package com.bidsmart.platform.permission;

public final class AuthorizationDecision {
    public enum Reason { ALLOWED, INVALID_SCOPE, ENTERPRISE_MISMATCH, FUNCTION_PERMISSION_DENIED,
        DATA_SCOPE_DENIED, PURCHASE_TYPE_UNCONFIGURED, PURCHASE_TYPE_DENIED,
        MATERIAL_CATEGORY_UNCONFIGURED, MATERIAL_CATEGORY_DENIED }
    private final Reason reason;
    private AuthorizationDecision(Reason reason) { this.reason = reason; }
    public static AuthorizationDecision of(Reason reason) { return new AuthorizationDecision(reason); }
    public boolean isAllowed() { return reason == Reason.ALLOWED; }
    public Reason getReason() { return reason; }
}
