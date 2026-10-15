package com.bidsmart.platform.auth;

import com.bidsmart.platform.permission.PermissionSnapshot;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CurrentUser {
    private final long id;
    private final String username;
    private final String displayName;
    private final DepartmentSummary department;
    private final String identityType;
    private final Long enterpriseId;
    private final List<?> roles;
    private final Set<String> permissions;
    private final Object scope;
    private final boolean firstPasswordChangeRequired;

    public CurrentUser(UserAccount account, PermissionSnapshot permissions) {
        this.id = account.getId(); this.username = account.getUsername(); this.displayName = account.getDisplayName();
        this.department = account.getDepartmentId() == null ? null
                : new DepartmentSummary(account.getDepartmentId(), account.getDepartmentName());
        this.identityType = account.getIdentityType(); this.enterpriseId = account.getEnterpriseId();
        this.firstPasswordChangeRequired = account.isPasswordChangeRequired();
        this.roles = firstPasswordChangeRequired || permissions == null ? Collections.emptyList()
                : permissions.getRoles().stream().map(role -> new RoleSummary(role.getId(), role.getName()))
                        .collect(Collectors.toList());
        this.permissions = firstPasswordChangeRequired || permissions == null
                ? Collections.emptySet() : permissions.getAuthorities();
        this.scope = firstPasswordChangeRequired || permissions == null ? null : permissions.getScope();
    }
    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public DepartmentSummary getDepartment() { return department; }
    public String getIdentityType() { return identityType; }
    public Long getEnterpriseId() { return enterpriseId; }
    public List<?> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }
    public Object getScope() { return scope; }
    public boolean isFirstPasswordChangeRequired() { return firstPasswordChangeRequired; }

    public static final class DepartmentSummary {
        private final long id;
        private final String name;
        DepartmentSummary(long id, String name) { this.id = id; this.name = name; }
        public long getId() { return id; }
        public String getName() { return name; }
    }
    public static final class RoleSummary {
        private final long id;
        private final String name;
        RoleSummary(long id, String name) { this.id = id; this.name = name; }
        public long getId() { return id; }
        public String getName() { return name; }
    }
}
