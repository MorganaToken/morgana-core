package org.keycloak.protocol.docker;

import org.keycloak.common.Profile;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resources.RealmsResource;
import org.keycloak.utils.ProfileHelper;

import javax.ws.rs.Path;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

public class DockerV2LoginProtocolService {

    private final EventBuilder event;

    private final KeycloakSession session;

    public DockerV2LoginProtocolService(final KeycloakSession session, final EventBuilder event) {
        this.session = session;
        this.event = event;
    }

    public static UriBuilder authProtocolBaseUrl(final UriInfo uriInfo) {
        final UriBuilder baseUriBuilder = uriInfo.getBaseUriBuilder();
        return authProtocolBaseUrl(baseUriBuilder);
    }

    public static UriBuilder authProtocolBaseUrl(final UriBuilder baseUriBuilder) {
        return baseUriBuilder.path(RealmsResource.class).path("{realm}/protocol/" + DockerAuthV2Protocol.LOGIN_PROTOCOL);
    }

    public static UriBuilder authUrl(final UriInfo uriInfo) {
        final UriBuilder baseUriBuilder = uriInfo.getBaseUriBuilder();
        return authUrl(baseUriBuilder);
    }

    public static UriBuilder authUrl(final UriBuilder baseUriBuilder) {
        final UriBuilder uriBuilder = authProtocolBaseUrl(baseUriBuilder);
        return uriBuilder.path(DockerV2LoginProtocolService.class, "auth");
    }

    /**
     * Authorization endpoint
     */
    @Path("auth")
    public Object auth() {
        ProfileHelper.requireFeature(Profile.Feature.DOCKER);

        return new DockerEndpoint(session, event, EventType.LOGIN);
    }
}
