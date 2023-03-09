/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.protocol.oidc.endpoints;

import org.keycloak.common.util.UriUtils;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oidc.utils.WebOriginsUtils;
import org.keycloak.utils.MediaType;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Set;

import static org.keycloak.protocol.oidc.endpoints.IframeUtil.returnIframeFromResources;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class LoginStatusIframeEndpoint {

    private final KeycloakSession session;

    public LoginStatusIframeEndpoint(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Produces(MediaType.TEXT_HTML_UTF_8)
    public Response getLoginStatusIframe(@QueryParam("version") String version) {
        return returnIframeFromResources("login-status-iframe.html", version, session);
    }

    @GET
    @Path("init")
    public Response preCheck(@QueryParam("client_id") String clientId, @QueryParam("origin") String origin) {
        try {
            UriInfo uriInfo = session.getContext().getUri();
            RealmModel realm = session.getContext().getRealm();
            ClientModel client = session.clients().getClientByClientId(realm, clientId);
            if (client != null && client.isEnabled()) {
                Set<String> validWebOrigins = WebOriginsUtils.resolveValidWebOrigins(session, client);
                validWebOrigins.add(UriUtils.getOrigin(uriInfo.getRequestUri()));
                if (validWebOrigins.contains("*") || validWebOrigins.contains(origin)) {
                    return Response.noContent().build();
                }
            }
        } catch (Throwable t) {
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }

}
