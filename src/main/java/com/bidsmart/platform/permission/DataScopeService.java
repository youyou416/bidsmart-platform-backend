package com.bidsmart.platform.permission;

import java.util.Objects;
import org.springframework.stereotype.Service;

/** Composable checks for later adapters; no administrator or empty-list bypass. */
@Service
public class DataScopeService {
    public AuthorizationDecision canView(PermissionSnapshot snapshot, String viewPermission, BusinessContext context) {
        return base(snapshot, viewPermission, context);
    }
    public AuthorizationDecision canHandle(PermissionSnapshot snapshot, String operationPermission, BusinessContext context) {
        AuthorizationDecision common = base(snapshot, operationPermission, context);
        if (!common.isAllowed()) { return common; }
        UserScope scope = snapshot.getScope();
        if (scope.getPurchaseTypes().isEmpty()) {
            return decision(AuthorizationDecision.Reason.PURCHASE_TYPE_UNCONFIGURED);
        }
        if (!scope.getPurchaseTypes().contains(context.getPurchaseType())) {
            return decision(AuthorizationDecision.Reason.PURCHASE_TYPE_DENIED);
        }
        if (scope.getMaterialCategories().isEmpty()) {
            return decision(AuthorizationDecision.Reason.MATERIAL_CATEGORY_UNCONFIGURED);
        }
        if (!scope.getMaterialCategories().contains(context.getMaterialCategory())) {
            return decision(AuthorizationDecision.Reason.MATERIAL_CATEGORY_DENIED);
        }
        return decision(AuthorizationDecision.Reason.ALLOWED);
    }
    private AuthorizationDecision base(PermissionSnapshot snapshot, String permission, BusinessContext context) {
        UserScope scope = snapshot == null ? null : snapshot.getScope();
        if (scope == null || !scope.isValid() || context == null) {
            return decision(AuthorizationDecision.Reason.INVALID_SCOPE);
        }
        // Enterprise isolation is derived from authenticated identity, before functional grants.
        if ("SUPPLIER".equals(scope.getIdentityType())
                && !Objects.equals(scope.getEnterpriseId(), context.getEnterpriseId())) {
            return decision(AuthorizationDecision.Reason.ENTERPRISE_MISMATCH);
        }
        if (!snapshot.permits(permission)) { return decision(AuthorizationDecision.Reason.FUNCTION_PERMISSION_DENIED); }
        boolean owner = Objects.equals(scope.getUserId(), context.getCreatorUserId())
            || Objects.equals(scope.getUserId(), context.getDeclarerUserId());
        boolean inDepartment = context.getDepartmentId() != null
            && scope.getDepartmentIds().contains(context.getDepartmentId());
        if (!(inDepartment || (scope.getType() == DataScopeType.SELF_AND_SPECIFIED && owner))) {
            return decision(AuthorizationDecision.Reason.DATA_SCOPE_DENIED);
        }
        return decision(AuthorizationDecision.Reason.ALLOWED);
    }
    private static AuthorizationDecision decision(AuthorizationDecision.Reason reason) {
        return AuthorizationDecision.of(reason);
    }
}
