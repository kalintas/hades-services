package com.hades.services.model;

public enum Role {
    USER,
    ADMIN,
    ANALYST,
    MANAGER;

    public static org.springframework.security.access.hierarchicalroles.RoleHierarchy getRoleHierarchy() {
        return org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
                .fromHierarchy("ROLE_ADMIN > ROLE_MANAGER \n ROLE_MANAGER > ROLE_ANALYST \n ROLE_ANALYST > ROLE_USER");
    }
}
