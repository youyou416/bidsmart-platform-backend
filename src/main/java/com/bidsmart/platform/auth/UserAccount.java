package com.bidsmart.platform.auth;

public final class UserAccount {
    private final long id;
    private final String username;
    private final String displayName;
    private final Long departmentId;
    private final String departmentName;
    private final String identityType;
    private final Long enterpriseId;
    private final String dataScopeType;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean passwordChangeRequired;
    private final long passwordVersion;

    public UserAccount(long id, String username, String displayName, Long departmentId,
                       String departmentName, String identityType, Long enterpriseId,
                       String dataScopeType, String passwordHash, boolean enabled,
                       boolean passwordChangeRequired, long passwordVersion) {
        this.id = id; this.username = username; this.displayName = displayName;
        this.departmentId = departmentId; this.departmentName = departmentName;
        this.identityType = identityType; this.enterpriseId = enterpriseId;
        this.dataScopeType = dataScopeType; this.passwordHash = passwordHash;
        this.enabled = enabled; this.passwordChangeRequired = passwordChangeRequired;
        this.passwordVersion = passwordVersion;
    }
    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public Long getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
    public String getIdentityType() { return identityType; }
    public Long getEnterpriseId() { return enterpriseId; }
    public String getDataScopeType() { return dataScopeType; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public long getPasswordVersion() { return passwordVersion; }
}
