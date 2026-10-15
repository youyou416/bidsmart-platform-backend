package com.bidsmart.platform.permission;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Scope belongs to the user, and never grows as a consequence of role merging. */
public final class UserScope {
    private final long userId;
    private final Long departmentId;
    private final String identityType;
    private final Long enterpriseId;
    private final DataScopeType type;
    private final Set<Long> departmentIds;
    private final Set<String> purchaseTypes;
    private final Set<String> materialCategories;
    private final boolean valid;
    public UserScope(long userId, Long departmentId, String identityType, Long enterpriseId, DataScopeType type,
            Set<Long> departmentIds, Set<String> purchaseTypes, Set<String> materialCategories, boolean valid) {
        this.userId = userId; this.departmentId = departmentId; this.identityType = identityType;
        this.enterpriseId = enterpriseId; this.type = type;
        this.departmentIds = immutable(departmentIds); this.purchaseTypes = immutable(purchaseTypes);
        this.materialCategories = immutable(materialCategories); this.valid = valid;
    }
    private static <T> Set<T> immutable(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
    public long getUserId() { return userId; }
    public Long getDepartmentId() { return departmentId; }
    public String getIdentityType() { return identityType; }
    public Long getEnterpriseId() { return enterpriseId; }
    public DataScopeType getType() { return type; }
    public Set<Long> getDepartmentIds() { return departmentIds; }
    public Set<String> getPurchaseTypes() { return purchaseTypes; }
    public Set<String> getMaterialCategories() { return materialCategories; }
    public boolean isValid() { return valid; }
}
