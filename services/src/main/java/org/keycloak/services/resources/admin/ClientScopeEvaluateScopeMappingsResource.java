/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.services.resources.admin;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.RoleContainerModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class ClientScopeEvaluateScopeMappingsResource {

    private final RoleContainerModel roleContainer;
    private final AdminPermissionEvaluator auth;
    private final ClientModel client;
    private final String scopeParam;

    public ClientScopeEvaluateScopeMappingsResource(RoleContainerModel roleContainer, AdminPermissionEvaluator auth, ClientModel client,
                                                    String scopeParam) {
        this.roleContainer = roleContainer;
        this.auth = auth;
        this.client = client;
        this.scopeParam = scopeParam;
    }


    /**
     * Get effective scope mapping of all roles of particular role container, which this client is defacto allowed to have in the accessToken issued for him.
     *
     * This contains scope mappings, which this client has directly, as well as scope mappings, which are granted to all client scopes,
     * which are linked with this client.
     *
     * @return
     */
    @Path("/granted")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public Stream<RoleRepresentation> getGrantedScopeMappings() {
        return getGrantedRoles().map(ModelToRepresentation::toBriefRepresentation);
    }


    /**
     * Get roles, which this client doesn't have scope for and can't have them in the accessToken issued for him. Defacto all the
     * other roles of particular role container, which are not in {@link #getGrantedScopeMappings()}
     *
     * @return
     */
    @Path("/not-granted")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public Stream<RoleRepresentation> getNotGrantedScopeMappings() {
        Set<RoleModel> grantedRoles = getGrantedRoles().collect(Collectors.toSet());

        return roleContainer.getRolesStream()
                .filter(((Predicate<RoleModel>) grantedRoles::contains).negate())
                .map(ModelToRepresentation::toBriefRepresentation);
    }




    private Stream<RoleModel> getGrantedRoles() {
        if (client.isFullScopeAllowed()) {
            return roleContainer.getRolesStream();
        }

        Set<ClientScopeModel> clientScopes = TokenManager.getRequestedClientScopes(scopeParam, client)
                .collect(Collectors.toSet());

        Predicate<RoleModel> hasClientScope = role ->
                clientScopes.stream().anyMatch(scopeContainer -> scopeContainer.hasScope(role));

        return roleContainer.getRolesStream()
                .filter(auth.roles()::canView)
                .filter(hasClientScope);
    }

}
