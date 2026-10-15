package com.bidsmart.platform.permission;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionCalculatorTest {
    private final PermissionCalculator calculator = new PermissionCalculator();
    private static Set<Long> ids(Long... ids) { return new HashSet<>(Arrays.asList(ids)); }
    private static Set<String> strings(String... strings) { return new HashSet<>(Arrays.asList(strings)); }
    private static Role role(long id, Long parent, boolean enabled, Long... grants) {
        return new Role(id, parent, "synthetic-role-"+id, enabled, ids(grants));
    }
    private static Resource operation(long id, String permission) {
        return new Resource(id, null, permission, Resource.Type.BUTTON, 0, null, null, null, null, true, true, permission);
    }
    private static List<Resource> operations() {
        return Arrays.asList(operation(1,"A"),operation(2,"B"),operation(3,"C"),operation(4,"D"),operation(5,"E"));
    }
    private static Resource home(long id, Long parent, boolean visible, boolean enabled) {
        return new Resource(id,parent,"Framework",Resource.Type.MENU,1,"FrameworkHome","/home","Home","HomeFilled",visible,enabled,"framework:home:view");
    }
    private static Resource directory(long id, Long parent, boolean enabled) {
        return new Resource(id,parent,"Directory",Resource.Type.DIRECTORY,0,"Directory","/framework",null,null,true,enabled,null);
    }
    private PermissionSnapshot snapshot(Set<Long> roleIds, List<Role> roles, List<Resource> resources) {
        return calculator.calculate(roleIds,roles,resources,null);
    }

    @Test void multipleRolesUnionAndDynamicParentChangesWithGrantProvenance() {
        Role child=role(20,10L,true,1L,2L,3L);
        Role parent=role(10,null,true,2L,3L,4L);
        PermissionSnapshot before=snapshot(ids(20L),Arrays.asList(child,parent),operations());
        assertEquals(strings("A","B","C","D"),before.getAuthorities());
        assertEquals(ids(1L,2L,3L),before.getRoles().get(0).getOwnResourceIds());
        assertEquals(ids(2L,3L,4L),before.getInheritedResourceIdsByRole().get(20L));
        Role changedParent=role(10,null,true,3L,5L);
        PermissionSnapshot after=snapshot(ids(20L),Arrays.asList(child,changedParent),operations());
        assertEquals(strings("A","B","C","E"),after.getAuthorities());
        PermissionSnapshot multiple=snapshot(ids(20L,30L),Arrays.asList(child,changedParent,role(30,null,true,4L)),operations());
        assertEquals(strings("A","B","C","D","E"),multiple.getAuthorities());
        assertFalse(after.permits("*"));
    }

    @Test void ancestorChangesAndRevocationAreRecalculatedWithoutCache() {
        assertTrue(snapshot(ids(2L),Arrays.asList(role(1,null,true,1L),role(2,1L,true)),operations()).permits("A"));
        assertFalse(snapshot(ids(2L),Arrays.asList(role(1,null,true),role(2,1L,true)),operations()).permits("A"));
        assertFalse(snapshot(ids(2L),Arrays.asList(role(1,null,false,1L),role(2,1L,true,2L)),operations()).permits("B"));
    }

    @Test void cyclesDanglingParentsAndDuplicateIdsFailClosed() {
        assertTrue(snapshot(ids(1L),Arrays.asList(role(1,2L,true,1L),role(2,1L,true,2L)),operations()).getAuthorities().isEmpty());
        assertTrue(snapshot(ids(1L),Collections.singletonList(role(1,999L,true,1L)),operations()).getAuthorities().isEmpty());
        assertTrue(snapshot(ids(1L),Arrays.asList(role(1,null,true,1L),role(1,null,true,2L)),operations()).getAuthorities().isEmpty());
        assertTrue(snapshot(ids(9L),Collections.singletonList(role(1,null,true,1L)),operations()).getAuthorities().isEmpty());
    }

    @Test void resourceAncestorsDisabledCyclicOrMissingDenyRoutesAndActions() {
        Role role=role(1,null,true,2L,3L);
        Resource button=new Resource(3,2L,"Edit",Resource.Type.BUTTON,0,null,null,null,null,true,true,"framework:edit");
        assertTrue(snapshot(ids(1L),Collections.singletonList(role),Arrays.asList(directory(1,null,false),home(2,1L,true,true),button)).getAuthorities().isEmpty());
        assertTrue(snapshot(ids(1L),Collections.singletonList(role),Arrays.asList(directory(1,2L,true),home(2,1L,true,true),button)).getAuthorities().isEmpty());
        assertTrue(snapshot(ids(1L),Collections.singletonList(role),Arrays.asList(home(2,999L,true,true),button)).getAuthorities().isEmpty());
        assertTrue(snapshot(ids(1L),Collections.singletonList(role),Arrays.asList(home(2,null,true,false),button)).getMenus().isEmpty());
    }

    @Test void directoriesAllowTreeRenderingWithoutExpandingOperationGrants() {
        List<Resource> resources=Arrays.asList(directory(1,null,true),home(2,1L,true,true),
            new Resource(3,2L,"Edit",Resource.Type.BUTTON,0,null,null,null,null,true,true,"framework:edit"));
        PermissionSnapshot result=snapshot(ids(1L),Collections.singletonList(role(1,null,true,2L)),resources);
        assertEquals(strings("framework:home:view"),result.getAuthorities());
        assertFalse(result.permits("framework:edit"));
        assertEquals(1,result.getMenus().size()); assertEquals(1,result.getMenus().get(0).getChildren().size());
        assertEquals(2,result.getMenus().get(0).getChildren().get(0).getId());
        assertEquals(ids(2L),result.getEffectiveResourceIds());
    }

    @Test void hidingMenuDoesNotRevokeItsRouteButUnregisteredComponentsDo() {
        PermissionSnapshot hidden=snapshot(ids(1L),Collections.singletonList(role(1,null,true,2L)),Collections.singletonList(home(2,null,false,true)));
        assertTrue(hidden.permits("framework:home:view")); assertFalse(hidden.getMenus().get(0).isVisible());
        Resource injected=new Resource(2,null,"Injected",Resource.Type.MENU,0,"UnsafeRoute","/home","https://malicious.test/script.js",null,true,true,"framework:home:view");
        assertTrue(snapshot(ids(1L),Collections.singletonList(role(1,null,true,2L)),Collections.singletonList(injected)).getAuthorities().isEmpty());
        Resource unsafePath=new Resource(2,null,"Unsafe",Resource.Type.MENU,0,"Home","//outside.test","Home",null,true,true,"framework:home:view");
        assertTrue(snapshot(ids(1L),Collections.singletonList(role(1,null,true,2L)),Collections.singletonList(unsafePath)).getMenus().isEmpty());
    }

    @Test void invalidTypesCannotAcquireAuthoritiesAndMenuParentsCannotExpandGrants() {
        Resource invalid=new Resource(2,null,"Unknown",null,0,null,null,null,null,true,true,"framework:edit");
        assertTrue(snapshot(ids(1L),Collections.singletonList(role(1,null,true,2L)),Collections.singletonList(invalid)).getAuthorities().isEmpty());
        assertTrue(snapshot(ids(1L),Collections.singletonList(role(1,null,true,2L)),Arrays.asList(home(1,null,true,true),home(2,1L,true,true))).getAuthorities().isEmpty());
    }
}
