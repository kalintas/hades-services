package com.hades.services.model;

public enum Role {
    USER,
    ADMIN,
    PERSONNEL,
    MANAGER;

    public static org.springframework.security.access.hierarchicalroles.RoleHierarchy getRoleHierarchy() {
        return org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
                .fromHierarchy(
                        "ROLE_ADMIN > ROLE_MANAGER \n ROLE_MANAGER > ROLE_PERSONNEL \n ROLE_PERSONNEL > ROLE_USER");
    }
}
