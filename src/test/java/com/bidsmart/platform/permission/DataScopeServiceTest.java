package com.bidsmart.platform.permission;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataScopeServiceTest {
    private final DepartmentScopeCalculator calculator=new DepartmentScopeCalculator();
    private final DataScopeService authorization=new DataScopeService();
    private static final List<Department> DEPARTMENTS=Arrays.asList(new Department(1,null,true),new Department(2,1L,true),
        new Department(3,2L,true),new Department(4,null,true),new Department(5,4L,true),new Department(6,1L,false));
    private static Set<Long> ids(Long... ids) { return new HashSet<>(Arrays.asList(ids)); }
    private static Set<String> codes(String... codes) { return new HashSet<>(Arrays.asList(codes)); }
    private UserScope scope(DataScopeType type, Set<Long> specified) {
        return calculator.calculate(100,1L,"BUYER",null,type,specified,DEPARTMENTS,codes("P1"),codes("M1"));
    }
    private static PermissionSnapshot granted(UserScope scope) {
        return new PermissionSnapshot(codes("view","handle"),Collections.emptyList(),Collections.emptyList(),scope,Collections.emptySet(),Collections.emptyMap());
    }
    private static BusinessContext data(Long creator,Long declarer,Long department,Long enterprise,String purchase,String material) {
        return new BusinessContext(creator,declarer,department,enterprise,purchase,material);
    }

    @Test void fourScopesIncludeAllDescendantsAndOnlyConfiguredDepartments() {
        assertEquals(ids(4L,5L),scope(DataScopeType.SELF_AND_SPECIFIED,ids(4L)).getDepartmentIds());
        assertEquals(ids(1L,2L,3L),scope(DataScopeType.DEPARTMENT,ids(4L)).getDepartmentIds());
        assertEquals(ids(1L,2L,3L,4L,5L),scope(DataScopeType.ALL_DEPARTMENTS,Collections.emptySet()).getDepartmentIds());
        assertEquals(ids(1L,2L,3L,4L,5L),scope(DataScopeType.DEPARTMENT_AND_SPECIFIED,ids(4L)).getDepartmentIds());
        assertEquals(ids(3L),scope(DataScopeType.SELF_AND_SPECIFIED,ids(3L)).getDepartmentIds());
    }

    @Test void selfDataMatchesCreatorOrDeclarerNotAnArbitraryBuyerField() {
        PermissionSnapshot user=granted(scope(DataScopeType.SELF_AND_SPECIFIED,Collections.emptySet()));
        assertTrue(authorization.canView(user,"view",data(100L,null,4L,null,null,null)).isAllowed());
        assertTrue(authorization.canView(user,"view",data(999L,100L,4L,null,null,null)).isAllowed());
        assertFalse(authorization.canView(user,"view",data(999L,998L,4L,null,null,null)).isAllowed());
        assertTrue(authorization.canView(granted(scope(DataScopeType.SELF_AND_SPECIFIED,ids(4L))),"view",data(999L,null,5L,null,null,null)).isAllowed());
    }

    @Test void viewingDoesNotRequirePurchaseOrMaterialButHandlingRequiresBoth() {
        UserScope constrained=calculator.calculate(100,1L,"BUYER",null,DataScopeType.ALL_DEPARTMENTS,
            Collections.emptySet(),DEPARTMENTS,Collections.emptySet(),Collections.emptySet());
        PermissionSnapshot user=granted(constrained);
        BusinessContext context=data(999L,null,2L,null,"P1","M1");
        assertTrue(authorization.canView(user,"view",context).isAllowed());
        assertEquals(AuthorizationDecision.Reason.PURCHASE_TYPE_UNCONFIGURED,authorization.canHandle(user,"handle",context).getReason());
        UserScope noMaterial=calculator.calculate(100,1L,"BUYER",null,DataScopeType.ALL_DEPARTMENTS,
            Collections.emptySet(),DEPARTMENTS,codes("P1"),Collections.emptySet());
        assertEquals(AuthorizationDecision.Reason.MATERIAL_CATEGORY_UNCONFIGURED,authorization.canHandle(granted(noMaterial),"handle",context).getReason());
        assertTrue(authorization.canHandle(granted(scope(DataScopeType.ALL_DEPARTMENTS,Collections.emptySet())),"handle",context).isAllowed());
    }

    @Test void administratorObeysSameFunctionalScopePurchaseAndMaterialRules() {
        UserScope scope=scope(DataScopeType.DEPARTMENT,Collections.emptySet());
        Role admin=new Role(1,null,"平台管理员",true,ids(1L));
        PermissionSnapshot administrator=new PermissionSnapshot(codes("view","handle"),Collections.singletonList(admin),
            Collections.emptyList(),scope,ids(1L),Collections.emptyMap());
        assertEquals(AuthorizationDecision.Reason.DATA_SCOPE_DENIED,authorization.canHandle(administrator,"handle",data(999L,null,4L,null,"P1","M1")).getReason());
        assertEquals(AuthorizationDecision.Reason.FUNCTION_PERMISSION_DENIED,authorization.canView(administrator,"ungranted",data(999L,null,2L,null,"P1","M1")).getReason());
        assertEquals(AuthorizationDecision.Reason.PURCHASE_TYPE_DENIED,authorization.canHandle(administrator,"handle",data(999L,null,2L,null,"P2","M1")).getReason());
        assertEquals(AuthorizationDecision.Reason.MATERIAL_CATEGORY_DENIED,authorization.canHandle(administrator,"handle",data(999L,null,2L,null,"P1","M2")).getReason());
    }

    @Test void supplierEnterpriseBoundToIdentityCannotBeExpandedWithRolesOrContext() {
        UserScope supplier=calculator.calculate(100,1L,"SUPPLIER",77L,DataScopeType.ALL_DEPARTMENTS,
            Collections.emptySet(),DEPARTMENTS,codes("P1"),codes("M1"));
        PermissionSnapshot granted=granted(supplier);
        assertTrue(authorization.canView(granted,"view",data(999L,null,2L,77L,null,null)).isAllowed());
        assertEquals(AuthorizationDecision.Reason.ENTERPRISE_MISMATCH,authorization.canView(granted,"view",data(100L,null,2L,78L,null,null)).getReason());
        assertEquals(AuthorizationDecision.Reason.ENTERPRISE_MISMATCH,authorization.canView(granted,"view",data(100L,null,2L,null,null,null)).getReason());
        UserScope noEnterprise=calculator.calculate(100,1L,"SUPPLIER",null,DataScopeType.ALL_DEPARTMENTS,
            Collections.emptySet(),DEPARTMENTS,codes("P1"),codes("M1"));
        assertFalse(noEnterprise.isValid());
        assertFalse(authorization.canView(granted(noEnterprise),"view",data(100L,null,2L,null,null,null)).isAllowed());
    }

    @Test void cyclicDanglingDisabledAndUnknownConfigurationFailClosed() {
        List<Department> cyclic=Arrays.asList(new Department(1,2L,true),new Department(2,1L,true));
        assertFalse(calculator.calculate(100,1L,"BUYER",null,DataScopeType.ALL_DEPARTMENTS,Collections.emptySet(),cyclic,codes("P1"),codes("M1")).isValid());
        assertFalse(calculator.calculate(100,1L,"BUYER",null,DataScopeType.DEPARTMENT,Collections.emptySet(),
            Collections.singletonList(new Department(1,999L,true)),codes("P1"),codes("M1")).isValid());
        assertFalse(scope(DataScopeType.SELF_AND_SPECIFIED,ids(999L)).isValid());
        assertFalse(scope(DataScopeType.SELF_AND_SPECIFIED,ids(6L)).isValid());
        assertFalse(calculator.calculate(100,1L,"UNKNOWN",null,DataScopeType.ALL_DEPARTMENTS,Collections.emptySet(),DEPARTMENTS,codes("P1"),codes("M1")).isValid());
        assertFalse(calculator.calculate(100,1L,"BUYER",null,null,Collections.emptySet(),DEPARTMENTS,codes("P1"),codes("M1")).isValid());
        assertFalse(authorization.canView(null,"view",null).isAllowed());
    }

    @Test void rolesDoNotExpandUserDepartmentOrPurchaseOrMaterialScope() {
        UserScope scope=scope(DataScopeType.DEPARTMENT,Collections.emptySet());
        List<Role> roles=Arrays.asList(new Role(1,null,"RoleA",true,ids(1L)),new Role(2,null,"RoleB",true,ids(2L)));
        List<Resource> resources=Arrays.asList(new Resource(1,null,"View",Resource.Type.BUTTON,0,null,null,null,null,true,true,"view"),
            new Resource(2,null,"Handle",Resource.Type.BUTTON,0,null,null,null,null,true,true,"handle"));
        PermissionSnapshot combined=new PermissionCalculator().calculate(ids(1L,2L),roles,resources,scope);
        assertSame(scope,combined.getScope());
        assertFalse(authorization.canHandle(combined,"handle",data(100L,null,4L,null,"P1","M1")).isAllowed());
    }
}
