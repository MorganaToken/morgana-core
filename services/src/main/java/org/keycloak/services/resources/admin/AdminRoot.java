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
package org.keycloak.services.resources.admin;

import org.jboss.logging.Logger;
import org.keycloak.http.HttpRequest;
import org.keycloak.http.HttpResponse;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.NotAuthorizedException;
import org.keycloak.common.Profile;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.ForbiddenException;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.Cors;
import org.keycloak.services.resources.admin.info.ServerInfoAdminResource;
import org.keycloak.services.resources.admin.permissions.AdminPermissions;
import org.keycloak.theme.Theme;
import org.keycloak.urls.UrlType;

import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

/**
 * Root resource for admin console and admin REST API
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@Path("/admin")
public class AdminRoot {
    protected static final Logger logger = Logger.getLogger(AdminRoot.class);

    protected TokenManager tokenManager;

    @Context
    protected KeycloakSession session;

    public AdminRoot() {
        this.tokenManager = new TokenManager();
    }

    public static UriBuilder adminBaseUrl(UriInfo uriInfo) {
        return adminBaseUrl(uriInfo.getBaseUriBuilder());
    }

    public static UriBuilder adminBaseUrl(UriBuilder base) {
        return base.path(AdminRoot.class);
    }

    /**
     * Convenience path to master realm admin console
     *
     * @exclude
     * @return
     */
    @GET
    public Response masterRealmAdminConsoleRedirect() {

        if (!isAdminConsoleEnabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        RealmModel master = new RealmManager(session).getKeycloakAdminstrationRealm();
        return Response.status(302).location(
                session.getContext().getUri(UrlType.ADMIN).getBaseUriBuilder().path(AdminRoot.class).path(AdminRoot.class, "getAdminConsole").path("/").build(master.getName())
        ).build();
    }

    /**
     * Convenience path to master realm admin console
     *
     * @exclude
     * @return
     */
    @Path("index.{html:html}") // expression is actually "index.html" but this is a hack to get around jax-doclet bug
    @GET
    public Response masterRealmAdminConsoleRedirectHtml() {

        if (!isAdminConsoleEnabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return masterRealmAdminConsoleRedirect();
    }

    protected void resolveRealmAndUpdateSession(String name, KeycloakSession session) {
        RealmManager realmManager = new RealmManager(session);
        RealmModel realm = realmManager.getRealmByName(name);
        if (realm == null) {
            throw new NotFoundException("Realm not found.  Did you type in a bad URL?");
        }
        session.getContext().setRealm(realm);
    }


    public static UriBuilder adminConsoleUrl(UriInfo uriInfo) {
        return adminConsoleUrl(uriInfo.getBaseUriBuilder());
    }

    public static UriBuilder adminConsoleUrl(UriBuilder base) {
        return adminBaseUrl(base).path(AdminRoot.class, "getAdminConsole");
    }

    /**
     * path to realm admin console ui
     *
     * @exclude
     * @param name Realm name (not id!)
     * @return
     */
    @Path("{realm}/console")
    public AdminConsole getAdminConsole(final @PathParam("realm") String name) {

        if (!isAdminConsoleEnabled()) {
            throw new NotFoundException();
        }

        resolveRealmAndUpdateSession(name, session);

        return new AdminConsole(session);
    }


    protected AdminAuth authenticateRealmAdminRequest(HttpHeaders headers) {
        String tokenString = AppAuthManager.extractAuthorizationHeaderToken(headers);
        if (tokenString == null) throw new NotAuthorizedException("Bearer");
        AccessToken token;
        try {
            JWSInput input = new JWSInput(tokenString);
            token = input.readJsonContent(AccessToken.class);
        } catch (JWSInputException e) {
            throw new NotAuthorizedException("Bearer token format error");
        }
        String realmName = token.getIssuer().substring(token.getIssuer().lastIndexOf('/') + 1);
        RealmManager realmManager = new RealmManager(session);
        RealmModel realm = realmManager.getRealmByName(realmName);
        if (realm == null) {
            throw new NotAuthorizedException("Unknown realm in token");
        }
        session.getContext().setRealm(realm);

        AuthenticationManager.AuthResult authResult = new AppAuthManager.BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setConnection(session.getContext().getConnection())
                .setHeaders(headers)
                .authenticate();

        if (authResult == null) {
            logger.debug("Token not valid");
            throw new NotAuthorizedException("Bearer");
        }

        return new AdminAuth(realm, authResult.getToken(), authResult.getUser(), authResult.getClient());
    }

    public static UriBuilder realmsUrl(UriInfo uriInfo) {
        return realmsUrl(uriInfo.getBaseUriBuilder());
    }

    public static UriBuilder realmsUrl(UriBuilder base) {
        return adminBaseUrl(base).path(AdminRoot.class, "getRealmsAdmin");
    }

    /**
     * Base Path to realm admin REST interface
     *
     * @param headers
     * @return
     */
    @Path("realms")
    public Object getRealmsAdmin() {
        HttpRequest request = getHttpRequest();

        if (!isAdminApiEnabled()) {
            throw new NotFoundException();
        }

        if (request.getHttpMethod().equals(HttpMethod.OPTIONS)) {
            return new AdminCorsPreflightService(request);
        }

        AdminAuth auth = authenticateRealmAdminRequest(session.getContext().getRequestHeaders());
        if (auth != null) {
            logger.debug("authenticated admin access for: " + auth.getUser().getUsername());
        }

        HttpResponse response = getHttpResponse();

        Cors.add(request).allowedOrigins(auth.getToken()).allowedMethods("GET", "PUT", "POST", "DELETE").exposedHeaders("Location").auth().build(
                response);

        return new RealmsAdminResource(session, auth, tokenManager);
    }

    @Path("{any:.*}")
    @OPTIONS
    public Object preFlight() {
        HttpRequest request = getHttpRequest();

        if (!isAdminApiEnabled()) {
            throw new NotFoundException();
        }

        return new AdminCorsPreflightService(request);
    }

    /**
     * General information about the server
     *
     * @param headers
     * @return
     */
    @Path("serverinfo")
    public Object getServerInfo() {

        if (!isAdminApiEnabled()) {
            throw new NotFoundException();
        }

        HttpRequest request = getHttpRequest();

        if (request.getHttpMethod().equals(HttpMethod.OPTIONS)) {
            return new AdminCorsPreflightService(request);
        }

        AdminAuth auth = authenticateRealmAdminRequest(session.getContext().getRequestHeaders());
        if (!AdminPermissions.realms(session, auth).isAdmin()) {
            throw new ForbiddenException();
        }

        if (auth != null) {
            logger.debug("authenticated admin access for: " + auth.getUser().getUsername());
        }

        Cors.add(request).allowedOrigins(auth.getToken()).allowedMethods("GET", "PUT", "POST", "DELETE").auth().build(
                getHttpResponse());

        return new ServerInfoAdminResource(session);
    }

    private HttpResponse getHttpResponse() {
        return session.getContext().getHttpResponse();
    }

    private HttpRequest getHttpRequest() {
        return session.getContext().getHttpRequest();
    }

    public static Theme getTheme(KeycloakSession session, RealmModel realm) throws IOException {
        return session.theme().getTheme(Theme.Type.ADMIN);
    }

    public static Properties getMessages(KeycloakSession session, RealmModel realm, String lang) {
        try {
            Theme theme = getTheme(session, realm);
            Locale locale = lang != null ? Locale.forLanguageTag(lang) : Locale.ENGLISH;
            return theme.getMessages(locale);
        } catch (IOException e) {
            logger.error("Failed to load messages from theme", e);
            return new Properties();
        }
    }

    public static Properties getMessages(KeycloakSession session, RealmModel realm, String lang, String... bundles) {
        Properties compound = new Properties();
        for (String bundle : bundles) {
            Properties current = getMessages(session, realm, lang, bundle);
            compound.putAll(current);
        }
        return compound;
    }

    private static Properties getMessages(KeycloakSession session, RealmModel realm, String lang, String bundle) {
        try {
            Theme theme = getTheme(session, realm);
            Locale locale = lang != null ? Locale.forLanguageTag(lang) : Locale.ENGLISH;
            return theme.getMessages(bundle, locale);
        } catch (IOException e) {
            logger.error("Failed to load messages from theme", e);
            return new Properties();
        }
    }

    private static boolean isAdminApiEnabled() {
        return Profile.isFeatureEnabled(Profile.Feature.ADMIN_API);
    }

    private static boolean isAdminConsoleEnabled() {
        return Profile.isFeatureEnabled(Profile.Feature.ADMIN2);
    }
}
