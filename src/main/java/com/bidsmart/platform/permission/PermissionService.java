package com.bidsmart.platform.permission;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Re-reads grants for every protected request: no permissions stored in the session. */
@Service
public class PermissionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionCalculator permissions = new PermissionCalculator();
    private final DepartmentScopeCalculator scopes = new DepartmentScopeCalculator();
    public PermissionService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public PermissionSnapshot loadForUser(long userId) {
        Map<String, Object> parameter = Collections.singletonMap("userId", userId);
        List<ScopeSeed> users = jdbc.query("SELECT dept_id,identity_type,enterprise_id,data_scope_type "
            + "FROM sys_user WHERE id=:userId AND status='ENABLED'", parameter,
            (rs, row) -> new ScopeSeed(nullableLong(rs, "dept_id"), rs.getString("identity_type"),
                nullableLong(rs, "enterprise_id"), scopeType(rs.getString("data_scope_type"))));
        if (users.size() != 1) { return denied(userId); }
        ScopeSeed user = users.get(0);
        List<Department> departments = jdbc.query("SELECT id,parent_id,status FROM sys_dept", Collections.emptyMap(),
            (rs, row) -> new Department(rs.getLong("id"), nullableLong(rs, "parent_id"), "ENABLED".equals(rs.getString("status"))));
        Set<Long> specified = new LinkedHashSet<>(jdbc.query("SELECT dept_id FROM sys_user_scope_dept WHERE user_id=:userId",
            parameter, (rs, row) -> rs.getLong("dept_id")));
        Set<String> purchases = new LinkedHashSet<>(jdbc.query("SELECT purchase_type_code FROM sys_user_purchase_type WHERE user_id=:userId",
            parameter, (rs, row) -> rs.getString("purchase_type_code")));
        Set<String> materials = new LinkedHashSet<>(jdbc.query("SELECT material_category_code FROM sys_user_material_scope WHERE user_id=:userId",
            parameter, (rs, row) -> rs.getString("material_category_code")));
        UserScope scope = scopes.calculate(userId, user.departmentId, user.identityType, user.enterpriseId,
            user.type, specified, departments, purchases, materials);
        Set<Long> assigned = new LinkedHashSet<>(jdbc.query("SELECT role_id FROM sys_user_role WHERE user_id=:userId",
            parameter, (rs, row) -> rs.getLong("role_id")));
        Map<Long, Set<Long>> grants = new HashMap<>();
        jdbc.query("SELECT role_id,resource_id FROM sys_role_resource", Collections.emptyMap(), rs -> {
            grants.computeIfAbsent(rs.getLong("role_id"), ignored -> new LinkedHashSet<>()).add(rs.getLong("resource_id"));
        });
        List<Role> roles = jdbc.query("SELECT id,parent_id,name,status FROM sys_role", Collections.emptyMap(),
            (rs, row) -> new Role(rs.getLong("id"), nullableLong(rs, "parent_id"), rs.getString("name"),
                "ENABLED".equals(rs.getString("status")), grants.getOrDefault(rs.getLong("id"), Collections.emptySet())));
        List<Resource> resources = jdbc.query("SELECT id,parent_id,name,resource_type,sort_order,route_name,route_path,component,icon,visible,status,permission_code "
            + "FROM sys_resource", Collections.emptyMap(), (rs, row) -> new Resource(rs.getLong("id"), nullableLong(rs, "parent_id"),
                rs.getString("name"), resourceType(rs.getString("resource_type")), rs.getInt("sort_order"), rs.getString("route_name"),
                rs.getString("route_path"), rs.getString("component"), rs.getString("icon"), rs.getBoolean("visible"),
                "ENABLED".equals(rs.getString("status")), rs.getString("permission_code")));
        return permissions.calculate(assigned, roles, resources, scope);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }
    private static DataScopeType scopeType(String value) {
        try { return DataScopeType.valueOf(value); } catch (RuntimeException invalid) { return null; }
    }
    private static Resource.Type resourceType(String value) {
        try { return Resource.Type.valueOf(value); } catch (RuntimeException invalid) { return null; }
    }
    private static PermissionSnapshot denied(long userId) {
        UserScope scope = new UserScope(userId, null, null, null, null, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), false);
        return new PermissionSnapshot(Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), scope, Collections.emptySet(), Collections.emptyMap());
    }
    private static final class ScopeSeed {
        private final Long departmentId;
        private final String identityType;
        private final Long enterpriseId;
        private final DataScopeType type;
        private ScopeSeed(Long departmentId, String identityType, Long enterpriseId, DataScopeType type) {
            this.departmentId = departmentId; this.identityType = identityType; this.enterpriseId = enterpriseId; this.type = type;
        }
    }
}
