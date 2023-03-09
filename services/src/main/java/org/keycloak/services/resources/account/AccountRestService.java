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
package org.keycloak.services.resources.account;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.http.HttpRequest;
import org.keycloak.common.ClientConnection;
import org.keycloak.common.Profile;
import org.keycloak.common.enums.AccountRestApiVersion;
import org.keycloak.common.util.StringPropertyReplacer;
import org.keycloak.events.Details;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.AccountRoles;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserConsentModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.representations.account.ClientRepresentation;
import org.keycloak.representations.account.ConsentRepresentation;
import org.keycloak.representations.account.ConsentScopeRepresentation;
import org.keycloak.representations.account.UserProfileAttributeMetadata;
import org.keycloak.representations.account.UserProfileMetadata;
import org.keycloak.representations.account.UserRepresentation;
import org.keycloak.representations.idm.ErrorRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.managers.Auth;
import org.keycloak.services.managers.UserConsentManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.resources.account.resources.ResourcesService;
import org.keycloak.services.util.ResolveRelative;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.theme.Theme;
import org.keycloak.userprofile.AttributeMetadata;
import org.keycloak.userprofile.AttributeValidatorMetadata;
import org.keycloak.userprofile.Attributes;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.EventAuditingAttributeChangeListener;
import org.keycloak.userprofile.ValidationException;
import org.keycloak.userprofile.ValidationException.Error;
import org.keycloak.validate.Validators;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class AccountRestService {

    private final HttpRequest request;

    protected final HttpHeaders headers;

    protected final ClientConnection clientConnection;

    private final KeycloakSession session;
    private final EventBuilder event;
    private final Auth auth;
    
    private final RealmModel realm;
    private final UserModel user;
    private final Locale locale;
    private final AccountRestApiVersion version;

    public AccountRestService(KeycloakSession session, Auth auth, EventBuilder event, AccountRestApiVersion version) {
        this.session = session;
        this.clientConnection = session.getContext().getConnection();
        this.auth = auth;
        this.realm = auth.getRealm();
        this.user = auth.getUser();
        this.event = event;
        this.locale = session.getContext().resolveLocale(user);
        this.version = version;
        event.client(auth.getClient()).user(auth.getUser());
        this.request = session.getContext().getHttpRequest();
        this.headers = session.getContext().getRequestHeaders();
    }
    
    /**
     * Get account information.
     *
     * @return
     */
    @Path("/")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public UserRepresentation account(final @QueryParam("userProfileMetadata") Boolean userProfileMetadata) {
        auth.requireOneOf(AccountRoles.MANAGE_ACCOUNT, AccountRoles.VIEW_PROFILE);

        UserModel user = auth.getUser();

        UserRepresentation rep = new UserRepresentation();
        rep.setId(user.getId());

        UserProfileProvider provider = session.getProvider(UserProfileProvider.class);
        UserProfile profile = provider.create(UserProfileContext.ACCOUNT, user);

        rep.setAttributes(profile.getAttributes().getReadable(false));

        addReadableBuiltinAttributes(user, rep, profile.getAttributes().getReadable(true).keySet());

        if(userProfileMetadata == null || userProfileMetadata.booleanValue())
            rep.setUserProfileMetadata(createUserProfileMetadata(profile));
        
        return rep;
    }

    private void addReadableBuiltinAttributes(UserModel user, UserRepresentation rep, Set<String> readableAttributes) {
        setIfReadable(UserModel.USERNAME, readableAttributes, rep::setUsername, user::getUsername);
        setIfReadable(UserModel.FIRST_NAME, readableAttributes, rep::setFirstName, user::getFirstName);
        setIfReadable(UserModel.LAST_NAME, readableAttributes, rep::setLastName, user::getLastName);
        setIfReadable(UserModel.EMAIL, readableAttributes, rep::setEmail, user::getEmail);
        // emailVerified is readable when email is readable
        setIfReadable(UserModel.EMAIL, readableAttributes, rep::setEmailVerified, user::isEmailVerified);
    }

    private <T> void setIfReadable(String attributeName, Set<String> readableAttributes, Consumer<T> setter, Supplier<T> getter) {
        if (readableAttributes.contains(attributeName)) {
            setter.accept(getter.get());
        }
    }

    private UserProfileMetadata createUserProfileMetadata(final UserProfile profile) {
        Map<String, List<String>> am = profile.getAttributes().getReadable();
        
        if(am == null)
            return null;
        
        List<UserProfileAttributeMetadata> attributes = am.keySet().stream()
                                                          .map(name -> profile.getAttributes().getMetadata(name))
                                                          .filter(Objects::nonNull)
                                                          .sorted((a,b) -> Integer.compare(a.getGuiOrder(), b.getGuiOrder()))
                                                          .map(sam -> toRestMetadata(sam, profile))
                                                          .collect(Collectors.toList());  
        return new UserProfileMetadata(attributes);
    }

    private UserProfileAttributeMetadata toRestMetadata(AttributeMetadata am, UserProfile profile) {
        return new UserProfileAttributeMetadata(am.getName(), 
                                                am.getAttributeDisplayName(), 
                                                profile.getAttributes().isRequired(am.getName()), 
                                                profile.getAttributes().isReadOnly(am.getName()), 
                                                am.getAnnotations(), 
                                                toValidatorMetadata(am));
    }
    
    private Map<String, Map<String, Object>> toValidatorMetadata(AttributeMetadata am){
        // we return only validators which are instance of ConfiguredProvider. Others are expected as internal.
        return am.getValidators() == null ? null : am.getValidators().stream()
                .filter(avm -> (Validators.validator(session, avm.getValidatorId()) instanceof ConfiguredProvider))
                .collect(Collectors.toMap(AttributeValidatorMetadata::getValidatorId, AttributeValidatorMetadata::getValidatorConfig));
    }
    
    @Path("/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public Response updateAccount(UserRepresentation rep) {
        auth.require(AccountRoles.MANAGE_ACCOUNT);

        event.event(EventType.UPDATE_PROFILE).detail(Details.CONTEXT, UserProfileContext.ACCOUNT.name());

        UserProfileProvider profileProvider = session.getProvider(UserProfileProvider.class);
        UserProfile profile = profileProvider.create(UserProfileContext.ACCOUNT, rep.toAttributes(), auth.getUser());

        try {

            profile.update(new EventAuditingAttributeChangeListener(profile, event));

            event.success();

            return Response.noContent().build();
        } catch (ValidationException pve) {
            List<ErrorRepresentation> errors = new ArrayList<>();
            for(Error err: pve.getErrors()) {
                errors.add(new ErrorRepresentation(err.getAttribute(), err.getMessage(), validationErrorParamsToString(err.getMessageParameters(), profile.getAttributes())));
            }
            return ErrorResponse.errors(errors, pve.getStatusCode(), false);
        } catch (ReadOnlyException e) {
            return ErrorResponse.error(Messages.READ_ONLY_USER, Response.Status.BAD_REQUEST);
        }
    }

    private String[] validationErrorParamsToString(Object[] messageParameters, Attributes userProfileAttributes) {
        if(messageParameters == null)
            return null;
        String[] ret = new String[messageParameters.length];
        int i = 0;
        for(Object p: messageParameters) {
            if(p != null) {
                //first parameter is user profile attribute name, we have to take Display Name for it
                if(i==0) {
                    AttributeMetadata am = userProfileAttributes.getMetadata(p.toString());
                    if(am != null)
                        ret[i++] = am.getAttributeDisplayName();
                    else 
                        ret[i++] = p.toString();
                } else {
                    ret[i++] = p.toString();
                }
            } else {
                i++;
            }
        }
        return ret;
    }

    /**
     * Get session information.
     *
     * @return
     */
    @Path("/sessions")
    public SessionResource sessions() {
        checkAccountApiEnabled();
        auth.requireOneOf(AccountRoles.MANAGE_ACCOUNT, AccountRoles.VIEW_PROFILE);
        return new SessionResource(session, auth);
    }

    @Path("/credentials")
    public AccountCredentialResource credentials() {
        checkAccountApiEnabled();
        return new AccountCredentialResource(session, user, auth);
    }

    @Path("/resources")
    public ResourcesService resources() {
        checkAccountApiEnabled();
        auth.requireOneOf(AccountRoles.MANAGE_ACCOUNT, AccountRoles.VIEW_PROFILE);
        return new ResourcesService(session, user, auth, request);
    }

    private ClientRepresentation modelToRepresentation(ClientModel model, List<String> inUseClients, List<String> offlineClients, Map<String, UserConsentModel> consents) {
        ClientRepresentation representation = new ClientRepresentation();
        representation.setClientId(model.getClientId());
        representation.setClientName(StringPropertyReplacer.replaceProperties(model.getName(), getProperties()));
        representation.setDescription(model.getDescription());
        representation.setUserConsentRequired(model.isConsentRequired());
        representation.setInUse(inUseClients.contains(model.getClientId()));
        representation.setOfflineAccess(offlineClients.contains(model.getClientId()));
        representation.setRootUrl(model.getRootUrl());
        representation.setBaseUrl(model.getBaseUrl());
        representation.setEffectiveUrl(ResolveRelative.resolveRelativeUri(session, model.getRootUrl(), model.getBaseUrl()));
        UserConsentModel consentModel = consents.get(model.getClientId());
        if(consentModel != null) {
            representation.setConsent(modelToRepresentation(consentModel));
            representation.setLogoUri(model.getAttribute(ClientModel.LOGO_URI));
            representation.setPolicyUri(model.getAttribute(ClientModel.POLICY_URI));
            representation.setTosUri(model.getAttribute(ClientModel.TOS_URI));
        }
        return representation;
    }

    private ConsentRepresentation modelToRepresentation(UserConsentModel model) {
        List<ConsentScopeRepresentation> grantedScopes = model.getGrantedClientScopes().stream()
                .map(m -> new ConsentScopeRepresentation(m.getId(), m.getConsentScreenText()!= null ? m.getConsentScreenText() : m.getName(), StringPropertyReplacer.replaceProperties(m.getConsentScreenText(), getProperties())))
                .collect(Collectors.toList());
        return new ConsentRepresentation(grantedScopes, model.getCreatedDate(), model.getLastUpdatedDate());
    }

    private Properties getProperties() {
        try {
            return session.theme().getTheme(Theme.Type.ACCOUNT).getMessages(locale);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the consent for the client with the given client id.
     *
     * @param clientId client id to return the consent for
     * @return consent of the client
     */
    @Path("/applications/{clientId}/consent")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConsent(final @PathParam("clientId") String clientId) {
        checkAccountApiEnabled();
        auth.requireOneOf(AccountRoles.MANAGE_ACCOUNT, AccountRoles.VIEW_CONSENT, AccountRoles.MANAGE_CONSENT);

        ClientModel client = realm.getClientByClientId(clientId);
        if (client == null) {
            return ErrorResponse.error("No client with clientId: " + clientId + " found.", Response.Status.NOT_FOUND);
        }

        UserConsentModel consent = session.users().getConsentByClient(realm, user.getId(), client.getId());
        if (consent == null) {
            return Response.noContent().build();
        }

        return Response.ok(modelToRepresentation(consent)).build();
    }

    /**
     * Deletes the consent for the client with the given client id.
     *
     * @param clientId client id to delete a consent for
     * @return returns 202 if deleted
     */
    @Path("/applications/{clientId}/consent")
    @DELETE
    public Response revokeConsent(final @PathParam("clientId") String clientId) {
        checkAccountApiEnabled();
        auth.requireOneOf(AccountRoles.MANAGE_ACCOUNT, AccountRoles.MANAGE_CONSENT);

        event.event(EventType.REVOKE_GRANT);
        ClientModel client = realm.getClientByClientId(clientId);
        if (client == null) {
            String msg = String.format("No client with clientId: %s found.", clientId);
            event.error(msg);
            return ErrorResponse.error(msg, Response.Status.NOT_FOUND);
        }

        UserConsentManager.revokeConsentToClient(session, client, user);
        event.detail(Details.REVOKED_CLIENT, client.getClientId()).success();

        return Response.noContent().build();
    }

    /**
     * Creates or updates the consent of the given, requested consent for
     * the client with the given client id. Returns the appropriate REST response.
     *
     * @param clientId client id to set a consent for
     * @param consent  requested consent for the client
     * @return the created or updated consent
     */
    @Path("/applications/{clientId}/consent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response grantConsent(final @PathParam("clientId") String clientId,
                                 final ConsentRepresentation consent) {
        event.event(EventType.GRANT_CONSENT);
        return upsert(clientId, consent);
    }

    /**
     * Creates or updates the consent of the given, requested consent for
     * the client with the given client id. Returns the appropriate REST response.
     *
     * @param clientId client id to set a consent for
     * @param consent  requested consent for the client
     * @return the created or updated consent
     */
    @Path("/applications/{clientId}/consent")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateConsent(final @PathParam("clientId") String clientId,
                                  final ConsentRepresentation consent) {
        event.event(EventType.UPDATE_CONSENT);
        return upsert(clientId, consent);
    }

    /**
     * Creates or updates the consent of the given, requested consent for
     * the client with the given client id. Returns the appropriate REST response.
     *
     * @param clientId client id to set a consent for
     * @param consent  requested consent for the client
     * @return response to return to the caller
     */
    private Response upsert(String clientId, ConsentRepresentation consent) {
        checkAccountApiEnabled();
        auth.requireOneOf(AccountRoles.MANAGE_ACCOUNT, AccountRoles.MANAGE_CONSENT);

        ClientModel client = realm.getClientByClientId(clientId);
        if (client == null) {
            String msg = String.format("No client with clientId: %s found.", clientId);
            event.error(msg);
            return ErrorResponse.error(msg, Response.Status.NOT_FOUND);
        }

        try {
            UserConsentModel grantedConsent = createConsent(client, consent);
            if (session.users().getConsentByClient(realm, user.getId(), client.getId()) == null) {
                session.users().addConsent(realm, user.getId(), grantedConsent);
                event.event(EventType.GRANT_CONSENT);
            } else {
                session.users().updateConsent(realm, user.getId(), grantedConsent);
                event.event(EventType.UPDATE_CONSENT);
            }
            event.detail(Details.GRANTED_CLIENT,client.getClientId());
            String scopeString = grantedConsent.getGrantedClientScopes().stream().map(cs->cs.getName()).collect(Collectors.joining(" "));
            event.detail(Details.SCOPE, scopeString).success();
            grantedConsent = session.users().getConsentByClient(realm, user.getId(), client.getId());
            return Response.ok(modelToRepresentation(grantedConsent)).build();
        } catch (IllegalArgumentException e) {
            return ErrorResponse.error(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Create a new consent model object from the requested consent object
     * for the given client model.
     *
     * @param client    client to create a consent for
     * @param requested list of client scopes that the new consent should contain
     * @return newly created consent model
     * @throws IllegalArgumentException throws an exception if the scope id is not available
     */
    private UserConsentModel createConsent(ClientModel client, ConsentRepresentation requested) throws IllegalArgumentException {
        UserConsentModel consent = new UserConsentModel(client);
        Map<String, ClientScopeModel> availableGrants = realm.getClientScopesStream()
                .collect(Collectors.toMap(ClientScopeModel::getId, Function.identity()));

        if (client.isConsentRequired()) {
            availableGrants.put(client.getId(), client);
        }

        for (ConsentScopeRepresentation scopeRepresentation : requested.getGrantedScopes()) {
            ClientScopeModel scopeModel = availableGrants.get(scopeRepresentation.getId());
            if (scopeModel == null) {
                String msg = String.format("Scope id %s does not exist for client %s.", scopeRepresentation, consent.getClient().getName());
                event.error(msg);
                throw new IllegalArgumentException(msg);
            } else {
                consent.addGrantedClientScope(scopeModel);
            }
        }
        return consent;
    }
    
    @Path("/linked-accounts")
    public LinkedAccountsResource linkedAccounts() {
        return new LinkedAccountsResource(session, request, auth, event, user);
    }

    @Path("/groups")
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<GroupRepresentation> groupMemberships(@QueryParam("briefRepresentation") @DefaultValue("true") boolean briefRepresentation) {
        auth.require(AccountRoles.VIEW_GROUPS);
        return ModelToRepresentation.toGroupHierarchy(user, !briefRepresentation);
    }

    @Path("/applications")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public Stream<ClientRepresentation> applications(@QueryParam("name") String name) {
        checkAccountApiEnabled();
        auth.requireOneOf(AccountRoles.MANAGE_ACCOUNT, AccountRoles.VIEW_APPLICATIONS);

        Set<ClientModel> clients = new HashSet<>();
        List<String> inUseClients = new LinkedList<>();
        clients.addAll(session.sessions().getUserSessionsStream(realm, user)
                .flatMap(s -> s.getAuthenticatedClientSessions().values().stream())
                .map(AuthenticatedClientSessionModel::getClient)
                .peek(client -> inUseClients.add(client.getClientId()))
                .collect(Collectors.toSet()));

        List<String> offlineClients = new LinkedList<>();
        clients.addAll(session.sessions().getOfflineUserSessionsStream(realm, user)
                .flatMap(s -> s.getAuthenticatedClientSessions().values().stream())
                .map(AuthenticatedClientSessionModel::getClient)
                .peek(client -> offlineClients.add(client.getClientId()))
                .collect(Collectors.toSet()));

        Map<String, UserConsentModel> consentModels = new HashMap<>();
        clients.addAll(session.users().getConsentsStream(realm, user.getId())
                .peek(consent -> consentModels.put(consent.getClient().getClientId(), consent))
                .map(UserConsentModel::getClient)
                .collect(Collectors.toSet()));

        realm.getAlwaysDisplayInConsoleClientsStream().forEach(clients::add);

        return clients.stream().filter(client -> !client.isBearerOnly() && !client.getClientId().isEmpty())
                .filter(client -> matches(client, name))
                .map(client -> modelToRepresentation(client, inUseClients, offlineClients, consentModels));
    }

    private boolean matches(ClientModel client, String name) {
        if(name == null)
            return true;
        else if(client.getName() == null)
            return false;
        else
            return client.getName().toLowerCase().contains(name.toLowerCase());
    }

    // TODO Logs
    
    private static void checkAccountApiEnabled() {
        if (!Profile.isFeatureEnabled(Profile.Feature.ACCOUNT_API)) {
            throw new NotFoundException();
}
    }
}
