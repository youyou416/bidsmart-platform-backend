package com.bidsmart.platform.permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Roles and resource-parent trees are deliberately independent. */
public final class PermissionCalculator {
    private static final Map<String, String> COMPONENT_PATHS = Collections.singletonMap("Home", "/home");

    public PermissionSnapshot calculate(Set<Long> userRoleIds, Collection<Role> allRoles,
            Collection<Resource> allResources, UserScope scope) {
        Map<Long, Role> roles = uniqueRoles(allRoles);
        Map<Long, Resource> resources = uniqueResources(allResources);
        Set<Long> granted = new LinkedHashSet<>();
        List<Role> activeRoles = new ArrayList<>();
        Map<Long, Set<Long>> inherited = new LinkedHashMap<>();
        for (Long id : userRoleIds) {
            Set<Long> effective = roleGrants(id, roles);
            if (effective == null) { continue; }
            Role role = roles.get(id);
            activeRoles.add(role); granted.addAll(effective);
            Set<Long> fromAncestors = role.getParentId() == null ? Collections.emptySet()
                : roleGrants(role.getParentId(), roles);
            inherited.put(id, fromAncestors);
        }
        Set<Long> allowed = new LinkedHashSet<>();
        Set<String> authorities = new LinkedHashSet<>();
        for (Long id : granted) {
            if (!validResourceChain(id, resources)) { continue; }
            Resource resource = resources.get(id); allowed.add(id);
            String permission = resource.getPermission();
            if (permission != null && permission.matches("[a-zA-Z0-9][a-zA-Z0-9:_.-]{0,127}")) {
                authorities.add(permission);
            }
        }
        return new PermissionSnapshot(authorities, activeRoles, menus(allowed, resources), scope, allowed, inherited);
    }

    private static Set<Long> roleGrants(Long id, Map<Long, Role> roles) {
        Set<Long> visited = new HashSet<>();
        Set<Long> result = new LinkedHashSet<>();
        while (id != null) {
            Role role = roles.get(id);
            if (!visited.add(id) || role == null || !role.isEnabled()) { return null; }
            result.addAll(role.getOwnResourceIds()); id = role.getParentId();
        }
        return result;
    }

    private static boolean validResourceChain(Long id, Map<Long, Resource> resources) {
        Set<Long> visited = new HashSet<>();
        while (id != null) {
            Resource resource = resources.get(id);
            if (!visited.add(id) || resource == null || !resource.isEnabled() || resource.getType() == null) { return false; }
            if (resource.getType() == Resource.Type.MENU) {
                if (!COMPONENT_PATHS.containsKey(resource.getComponent())
                        || !COMPONENT_PATHS.get(resource.getComponent()).equals(resource.getPath())
                        || resource.getRouteName() == null
                        || !resource.getRouteName().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) { return false; }
            } else if (resource.getType() == Resource.Type.DIRECTORY) {
                if (resource.getPath() == null || !resource.getPath().matches("/[A-Za-z0-9/_-]*")) { return false; }
            }
            Resource parent = resources.get(resource.getParentId());
            if (parent != null && ((resource.getType() == Resource.Type.DIRECTORY && parent.getType() != Resource.Type.DIRECTORY)
                    || (resource.getType() == Resource.Type.MENU && parent.getType() != Resource.Type.DIRECTORY)
                    || (resource.getType() == Resource.Type.BUTTON && parent.getType() != Resource.Type.MENU))) { return false; }
            id = resource.getParentId();
        }
        return true;
    }

    private static List<MenuNode> menus(Set<Long> granted, Map<Long, Resource> resources) {
        Set<Long> included = new HashSet<>();
        for (Long id : granted) {
            Resource resource = resources.get(id);
            if (resource.getType() != Resource.Type.MENU) { continue; }
            // A hidden menu remains a permitted route; visible only controls navigation.
            while (resource != null) {
                included.add(resource.getId());
                resource = resource.getParentId() == null ? null : resources.get(resource.getParentId());
            }
        }
        List<Resource> sorted = new ArrayList<>();
        for (Long id : included) { sorted.add(resources.get(id)); }
        sorted.sort(Comparator.comparingInt(Resource::getSortOrder).thenComparingLong(Resource::getId));
        Map<Long, MenuNode> nodes = new LinkedHashMap<>();
        for (Resource resource : sorted) { nodes.put(resource.getId(), new MenuNode(resource)); }
        List<MenuNode> roots = new ArrayList<>();
        for (Resource resource : sorted) {
            MenuNode node = nodes.get(resource.getId());
            MenuNode parent = nodes.get(resource.getParentId());
            if (parent == null) { roots.add(node); } else { parent.addChild(node); }
        }
        return roots;
    }

    private static Map<Long, Role> uniqueRoles(Collection<Role> items) {
        Map<Long, Role> result = new HashMap<>(); Set<Long> duplicates = new HashSet<>();
        for (Role item : items) { if (result.put(item.getId(), item) != null) { duplicates.add(item.getId()); } }
        duplicates.forEach(result::remove); return result;
    }
    private static Map<Long, Resource> uniqueResources(Collection<Resource> items) {
        Map<Long, Resource> result = new HashMap<>(); Set<Long> duplicates = new HashSet<>();
        for (Resource item : items) { if (result.put(item.getId(), item) != null) { duplicates.add(item.getId()); } }
        duplicates.forEach(result::remove); return result;
    }
}
