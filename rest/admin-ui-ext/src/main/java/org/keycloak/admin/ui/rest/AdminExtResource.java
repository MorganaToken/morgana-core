package org.keycloak.admin.ui.rest;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

import javax.ws.rs.Path;

public final class AdminExtResource {
    private KeycloakSession session;
    private RealmModel realm;
    private AdminPermissionEvaluator auth;
    private AdminEventBuilder adminEvent;

    public AdminExtResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.adminEvent = adminEvent;
    }

    @Path("/authentication-management")
    public AuthenticationManagementResource authenticationManagement() {
        return new AuthenticationManagementResource(session, realm, auth);
    }

    @Path("/brute-force-user")
    public BruteForceUsersResource bruteForceUsers() {
        return new BruteForceUsersResource(session, realm, auth);
    }

    @Path("/available-roles")
    public AvailableRoleMappingResource availableRoles() {
        return new AvailableRoleMappingResource(session, realm, auth);
    }

    @Path("/effective-roles")
    public EffectiveRoleMappingResource effectiveRoles() {
        return new EffectiveRoleMappingResource(session, realm, auth);
    }

    @Path("/groups")
    public GroupsResource groups() {
        return new GroupsResource(session, realm, auth);
    }

    @Path("/sessions")
    public SessionsResource sessions() {
        return new SessionsResource(session, realm, auth);
    }

}
