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
package org.keycloak.services.resources;

import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.http.HttpRequest;
import org.keycloak.http.HttpResponse;
import org.keycloak.common.util.PemUtils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocolService;
import org.keycloak.representations.idm.PublishedRealmRepresentation;
import org.keycloak.services.resources.account.AccountFormService;

import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * Resource class for public realm information
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class PublicRealmResource {
    protected static final Logger logger = Logger.getLogger(PublicRealmResource.class);

    protected final HttpRequest request;

    protected final HttpResponse response;

    protected final KeycloakSession session;

    protected final RealmModel realm;

    public PublicRealmResource(KeycloakSession session) {
        this.session = session;
        this.realm = session.getContext().getRealm();
        this.request = session.getContext().getHttpRequest();
        this.response = session.getContext().getHttpResponse();
    }

    /**
     * CORS preflight
     *
     * @return
     */
    @Path("/")
    @OPTIONS
    public Response accountPreflight() {
        return Cors.add(request, Response.ok()).auth().preflight().build();
    }

    /**
     * Public information about the realm.
     *
     * @return
     */
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public PublishedRealmRepresentation getRealm() {
        Cors.add(request).allowedOrigins(Cors.ACCESS_CONTROL_ALLOW_ORIGIN_WILDCARD).auth().build(response);
        return realmRep(session, realm, session.getContext().getUri());
    }

    public static PublishedRealmRepresentation realmRep(KeycloakSession session, RealmModel realm, UriInfo uriInfo) {
        PublishedRealmRepresentation rep = new PublishedRealmRepresentation();
        rep.setRealm(realm.getName());
        rep.setTokenServiceUrl(OIDCLoginProtocolService.tokenServiceBaseUrl(uriInfo).build(realm.getName()).toString());
        rep.setAccountServiceUrl(AccountFormService.accountServiceBaseUrl(uriInfo).build(realm.getName()).toString());
        rep.setPublicKeyPem(PemUtils.encodeKey(session.keys().getActiveRsaKey(realm).getPublicKey()));
        rep.setNotBefore(realm.getNotBefore());
        return rep;
    }


}
