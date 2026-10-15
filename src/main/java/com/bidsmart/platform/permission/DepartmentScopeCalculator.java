package com.bidsmart.platform.permission;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DepartmentScopeCalculator {
    public UserScope calculate(long userId, Long departmentId, String identityType, Long enterpriseId,
            DataScopeType type, Set<Long> specifiedDepartmentIds, Collection<Department> departments,
            Set<String> purchaseTypes, Set<String> materialCategories) {
        Map<Long, Department> index = new HashMap<>();
        boolean valid = type != null && ("BUYER".equals(identityType) || "SUPPLIER".equals(identityType));
        for (Department department : departments) {
            if (index.put(department.getId(), department) != null) { valid = false; }
        }
        // Reject cyclic/dangling department configuration, rather than treating it as a root.
        for (Department department : departments) {
            if (!structurallyValid(department.getId(), index)) { valid = false; }
        }
        if ("SUPPLIER".equals(identityType) && (enterpriseId == null || enterpriseId <= 0)) { valid = false; }
        Set<Long> included = new LinkedHashSet<>();
        if (valid) {
            Set<Long> roots = new HashSet<>();
            if (type == DataScopeType.DEPARTMENT || type == DataScopeType.DEPARTMENT_AND_SPECIFIED) {
                if (departmentId == null || !enabledChain(departmentId, index)) { valid = false; }
                else { roots.add(departmentId); }
            }
            if (type == DataScopeType.SELF_AND_SPECIFIED || type == DataScopeType.DEPARTMENT_AND_SPECIFIED) {
                for (Long specified : specifiedDepartmentIds) {
                    if (!enabledChain(specified, index)) { valid = false; }
                    else { roots.add(specified); }
                }
            }
            for (Department department : departments) {
                if (!enabledChain(department.getId(), index)) { continue; }
                if (type == DataScopeType.ALL_DEPARTMENTS || hasAncestor(department.getId(), roots, index)) {
                    included.add(department.getId());
                }
            }
        }
        if (!valid) { included.clear(); }
        return new UserScope(userId, departmentId, identityType, enterpriseId, type, included,
            purchaseTypes, materialCategories, valid);
    }

    private static boolean structurallyValid(Long id, Map<Long, Department> index) {
        Set<Long> seen = new HashSet<>();
        while (id != null) {
            Department department = index.get(id);
            if (!seen.add(id) || department == null) { return false; }
            id = department.getParentId();
        }
        return true;
    }
    private static boolean enabledChain(Long id, Map<Long, Department> index) {
        if (id == null) { return false; }
        Set<Long> seen = new HashSet<>();
        while (id != null) {
            Department department = index.get(id);
            if (!seen.add(id) || department == null || !department.isEnabled()) { return false; }
            id = department.getParentId();
        }
        return true;
    }
    private static boolean hasAncestor(Long id, Set<Long> roots, Map<Long, Department> index) {
        while (id != null) {
            if (roots.contains(id)) { return true; }
            id = index.get(id).getParentId();
        }
        return false;
    }
}
