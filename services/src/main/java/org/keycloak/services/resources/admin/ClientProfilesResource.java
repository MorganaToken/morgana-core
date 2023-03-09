/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.http.HttpRequest;
import org.keycloak.http.HttpResponse;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

public class ClientProfilesResource {
    protected static final Logger logger = Logger.getLogger(ClientProfilesResource.class);

    protected final HttpRequest request;

    protected final HttpResponse response;

    protected final KeycloakSession session;

    protected final RealmModel realm;
    private final AdminPermissionEvaluator auth;

    public ClientProfilesResource(KeycloakSession session, AdminPermissionEvaluator auth) {
        this.session = session;
        this.realm = session.getContext().getRealm();
        this.auth = auth;
        this.request = session.getContext().getHttpRequest();
        this.response = session.getContext().getHttpResponse();
    }

    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public ClientProfilesRepresentation getProfiles(@QueryParam("include-global-profiles") boolean includeGlobalProfiles) {
        auth.realm().requireViewRealm();

        try {
            return session.clientPolicy().getClientProfiles(realm, includeGlobalProfiles);
        } catch (ClientPolicyException e) {
            throw new BadRequestException(ErrorResponse.error(e.getError(), Response.Status.BAD_REQUEST));
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateProfiles(final ClientProfilesRepresentation clientProfiles) {
        auth.realm().requireManageRealm();

        try {
            session.clientPolicy().updateClientProfiles(realm, clientProfiles);
        } catch (ClientPolicyException e) {
            return ErrorResponse.error(e.getError(), Response.Status.BAD_REQUEST);
        }
        return Response.noContent().build();
    }
}
