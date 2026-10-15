package com.bidsmart.platform.permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Request-scoped authorization result, never a long-lived session grant cache. */
public final class PermissionSnapshot {
    private final Set<String> authorities;
    private final List<Role> roles;
    private final List<MenuNode> menus;
    private final UserScope scope;
    private final Set<Long> effectiveResourceIds;
    private final Map<Long, Set<Long>> inheritedResourceIdsByRole;
    public PermissionSnapshot(Set<String> authorities, List<Role> roles, List<MenuNode> menus, UserScope scope,
            Set<Long> effectiveResourceIds, Map<Long, Set<Long>> inheritedResourceIdsByRole) {
        this.authorities = Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
        this.menus = Collections.unmodifiableList(new ArrayList<>(menus)); this.scope = scope;
        this.effectiveResourceIds = Collections.unmodifiableSet(new LinkedHashSet<>(effectiveResourceIds));
        Map<Long, Set<Long>> inherited = new LinkedHashMap<>();
        inheritedResourceIdsByRole.forEach((role, grants) -> inherited.put(role,
            Collections.unmodifiableSet(new LinkedHashSet<>(grants))));
        this.inheritedResourceIdsByRole = Collections.unmodifiableMap(inherited);
    }
    public Set<String> getAuthorities() { return authorities; }
    public List<Role> getRoles() { return roles; }
    public List<MenuNode> getMenus() { return menus; }
    public UserScope getScope() { return scope; }
    public Set<Long> getEffectiveResourceIds() { return effectiveResourceIds; }
    public Map<Long, Set<Long>> getInheritedResourceIdsByRole() { return inheritedResourceIdsByRole; }
    public boolean permits(String permission) {
        return permission != null && !permission.isEmpty() && authorities.contains(permission);
    }
}
