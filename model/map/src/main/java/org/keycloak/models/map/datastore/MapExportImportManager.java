/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.map.datastore;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.ClientScopeProvider;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.ImpersonationConstants;
import org.keycloak.models.ModelException;
import org.keycloak.exportimport.ExportAdapter;
import org.keycloak.exportimport.ExportOptions;
import org.keycloak.keys.KeyProvider;
import org.keycloak.migration.MigrationProvider;
import org.keycloak.migration.migrators.MigrationUtils;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.BrowserSecurityHeaders;
import org.keycloak.models.CibaConfig;
import org.keycloak.models.ClaimMask;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.Constants;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OAuth2DeviceConfig;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.ParConfig;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.RoleProvider;
import org.keycloak.models.ScopeContainerModel;
import org.keycloak.models.UserConsentModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.WebAuthnPolicy;
import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.common.AbstractMapProviderFactory;
import org.keycloak.models.map.realm.MapRealmEntity;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.ModelCriteriaBuilder;
import org.keycloak.models.map.storage.criteria.DefaultModelCriteria;
import org.keycloak.models.utils.DefaultAuthenticationFlows;
import org.keycloak.models.utils.DefaultKeyProviders;
import org.keycloak.models.utils.DefaultRequiredActions;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.partialimport.PartialImportResults;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.representations.idm.ApplicationRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.ClaimRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ClientTemplateRepresentation;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.OAuthClientRepresentation;
import org.keycloak.representations.idm.PartialImportRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.ScopeMappingRepresentation;
import org.keycloak.representations.idm.UserConsentRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.storage.ExportImportManager;
import org.keycloak.storage.ImportRealmFromRepresentationEvent;
import org.keycloak.storage.PartialImportRealmFromRepresentationEvent;
import org.keycloak.storage.SearchableModelField;
import org.keycloak.storage.SetDefaultsForNewRealm;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.util.JsonSerialization;
import org.keycloak.utils.ReservedCharValidator;
import org.keycloak.validation.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.keycloak.models.map.storage.QueryParameters.withCriteria;
import static org.keycloak.models.map.storage.criteria.DefaultModelCriteria.criteria;
import static org.keycloak.models.utils.DefaultRequiredActions.getDefaultRequiredActionCaseInsensitively;
import static org.keycloak.models.utils.RepresentationToModel.createCredentials;
import static org.keycloak.models.utils.RepresentationToModel.createFederatedIdentities;
import static org.keycloak.models.utils.RepresentationToModel.createGroups;
import static org.keycloak.models.utils.RepresentationToModel.createRoleMappings;
import static org.keycloak.models.utils.RepresentationToModel.importGroup;
import static org.keycloak.models.utils.RepresentationToModel.importRoles;

/**
 * This wraps the functionality about export/import for legacy storage.
 *
 * <p>
 * Currently, this only removes the user-storage and federation code from LegacyExportImportManager.
 * <p>
 * In the future, this needs to be rewritten completely.
 *
 * @author Alexander Schwartz
 * @author Hynek Mlnarik
 */
public class MapExportImportManager implements ExportImportManager {
    private final KeycloakSession session;
    private static final Logger logger = Logger.getLogger(MapExportImportManager.class);

    /**
     * Use the old import via the logical layer vs. the new method importing to CHM first and then copying over
     * This is a temporary to test the functionality with the old representations until the new file store arrives in main,
     * and will then be removed.
     */
    private final boolean useNewImportMethod;

    public MapExportImportManager(KeycloakSession session) {
        this.session = session;
        useNewImportMethod = Boolean.parseBoolean(System.getProperty(MapExportImportManager.class.getName(), "false"));
    }

    public MapExportImportManager(KeycloakSession session, boolean useNewImportMethod) {
        this.session = session;
        this.useNewImportMethod = useNewImportMethod;
    }

    @Override
    public void importRealm(RealmRepresentation rep, RealmModel newRealm, boolean skipUserDependent) {
        convertDeprecatedSocialProviders(rep);
        convertDeprecatedApplications(session, rep);
        convertDeprecatedClientTemplates(rep);

        newRealm.setName(rep.getRealm());
        if (rep.getDisplayName() != null) newRealm.setDisplayName(rep.getDisplayName());
        if (rep.getDisplayNameHtml() != null) newRealm.setDisplayNameHtml(rep.getDisplayNameHtml());
        if (rep.isEnabled() != null) newRealm.setEnabled(rep.isEnabled());
        if (rep.isUserManagedAccessAllowed() != null) newRealm.setUserManagedAccessAllowed(rep.isUserManagedAccessAllowed());
        if (rep.isBruteForceProtected() != null) newRealm.setBruteForceProtected(rep.isBruteForceProtected());
        if (rep.isPermanentLockout() != null) newRealm.setPermanentLockout(rep.isPermanentLockout());
        if (rep.getMaxFailureWaitSeconds() != null) newRealm.setMaxFailureWaitSeconds(rep.getMaxFailureWaitSeconds());
        if (rep.getMinimumQuickLoginWaitSeconds() != null)
            newRealm.setMinimumQuickLoginWaitSeconds(rep.getMinimumQuickLoginWaitSeconds());
        if (rep.getWaitIncrementSeconds() != null) newRealm.setWaitIncrementSeconds(rep.getWaitIncrementSeconds());
        if (rep.getQuickLoginCheckMilliSeconds() != null)
            newRealm.setQuickLoginCheckMilliSeconds(rep.getQuickLoginCheckMilliSeconds());
        if (rep.getMaxDeltaTimeSeconds() != null) newRealm.setMaxDeltaTimeSeconds(rep.getMaxDeltaTimeSeconds());
        if (rep.getFailureFactor() != null) newRealm.setFailureFactor(rep.getFailureFactor());
        if (rep.isEventsEnabled() != null) newRealm.setEventsEnabled(rep.isEventsEnabled());
        if (rep.getEnabledEventTypes() != null)
            newRealm.setEnabledEventTypes(new HashSet<>(rep.getEnabledEventTypes()));
        if (rep.getEventsExpiration() != null) newRealm.setEventsExpiration(rep.getEventsExpiration());
        if (rep.getEventsListeners() != null) newRealm.setEventsListeners(new HashSet<>(rep.getEventsListeners()));
        if (rep.isAdminEventsEnabled() != null) newRealm.setAdminEventsEnabled(rep.isAdminEventsEnabled());
        if (rep.isAdminEventsDetailsEnabled() != null)
            newRealm.setAdminEventsDetailsEnabled(rep.isAdminEventsDetailsEnabled());

        if (rep.getNotBefore() != null) newRealm.setNotBefore(rep.getNotBefore());

        if (rep.getDefaultSignatureAlgorithm() != null) newRealm.setDefaultSignatureAlgorithm(rep.getDefaultSignatureAlgorithm());
        else newRealm.setDefaultSignatureAlgorithm(Constants.DEFAULT_SIGNATURE_ALGORITHM);

        if (rep.getRevokeRefreshToken() != null) newRealm.setRevokeRefreshToken(rep.getRevokeRefreshToken());
        else newRealm.setRevokeRefreshToken(false);

        if (rep.getRefreshTokenMaxReuse() != null) newRealm.setRefreshTokenMaxReuse(rep.getRefreshTokenMaxReuse());
        else newRealm.setRefreshTokenMaxReuse(0);

        if (rep.getAccessTokenLifespan() != null) newRealm.setAccessTokenLifespan(rep.getAccessTokenLifespan());
        else newRealm.setAccessTokenLifespan(300);

        if (rep.getAccessTokenLifespanForImplicitFlow() != null)
            newRealm.setAccessTokenLifespanForImplicitFlow(rep.getAccessTokenLifespanForImplicitFlow());
        else
            newRealm.setAccessTokenLifespanForImplicitFlow(Constants.DEFAULT_ACCESS_TOKEN_LIFESPAN_FOR_IMPLICIT_FLOW_TIMEOUT);

        if (rep.getSsoSessionIdleTimeout() != null) newRealm.setSsoSessionIdleTimeout(rep.getSsoSessionIdleTimeout());
        else newRealm.setSsoSessionIdleTimeout(1800);
        if (rep.getSsoSessionMaxLifespan() != null) newRealm.setSsoSessionMaxLifespan(rep.getSsoSessionMaxLifespan());
        else newRealm.setSsoSessionMaxLifespan(36000);
        if (rep.getSsoSessionMaxLifespanRememberMe() != null) newRealm.setSsoSessionMaxLifespanRememberMe(rep.getSsoSessionMaxLifespanRememberMe());
        if (rep.getSsoSessionIdleTimeoutRememberMe() != null) newRealm.setSsoSessionIdleTimeoutRememberMe(rep.getSsoSessionIdleTimeoutRememberMe());
        if (rep.getOfflineSessionIdleTimeout() != null)
            newRealm.setOfflineSessionIdleTimeout(rep.getOfflineSessionIdleTimeout());
        else newRealm.setOfflineSessionIdleTimeout(Constants.DEFAULT_OFFLINE_SESSION_IDLE_TIMEOUT);

        // KEYCLOAK-7688 Offline Session Max for Offline Token
        if (rep.getOfflineSessionMaxLifespanEnabled() != null) newRealm.setOfflineSessionMaxLifespanEnabled(rep.getOfflineSessionMaxLifespanEnabled());
        else newRealm.setOfflineSessionMaxLifespanEnabled(false);

        if (rep.getOfflineSessionMaxLifespan() != null)
            newRealm.setOfflineSessionMaxLifespan(rep.getOfflineSessionMaxLifespan());
        else newRealm.setOfflineSessionMaxLifespan(Constants.DEFAULT_OFFLINE_SESSION_MAX_LIFESPAN);

        if (rep.getClientSessionIdleTimeout() != null)
            newRealm.setClientSessionIdleTimeout(rep.getClientSessionIdleTimeout());
        if (rep.getClientSessionMaxLifespan() != null)
            newRealm.setClientSessionMaxLifespan(rep.getClientSessionMaxLifespan());

        if (rep.getClientOfflineSessionIdleTimeout() != null)
            newRealm.setClientOfflineSessionIdleTimeout(rep.getClientOfflineSessionIdleTimeout());
        if (rep.getClientOfflineSessionMaxLifespan() != null)
            newRealm.setClientOfflineSessionMaxLifespan(rep.getClientOfflineSessionMaxLifespan());

        if (rep.getAccessCodeLifespan() != null) newRealm.setAccessCodeLifespan(rep.getAccessCodeLifespan());
        else newRealm.setAccessCodeLifespan(60);

        if (rep.getAccessCodeLifespanUserAction() != null)
            newRealm.setAccessCodeLifespanUserAction(rep.getAccessCodeLifespanUserAction());
        else newRealm.setAccessCodeLifespanUserAction(300);

        if (rep.getAccessCodeLifespanLogin() != null)
            newRealm.setAccessCodeLifespanLogin(rep.getAccessCodeLifespanLogin());
        else newRealm.setAccessCodeLifespanLogin(1800);

        if (rep.getActionTokenGeneratedByAdminLifespan() != null)
            newRealm.setActionTokenGeneratedByAdminLifespan(rep.getActionTokenGeneratedByAdminLifespan());
        else newRealm.setActionTokenGeneratedByAdminLifespan(12 * 60 * 60);

        if (rep.getActionTokenGeneratedByUserLifespan() != null)
            newRealm.setActionTokenGeneratedByUserLifespan(rep.getActionTokenGeneratedByUserLifespan());
        else newRealm.setActionTokenGeneratedByUserLifespan(newRealm.getAccessCodeLifespanUserAction());

        // OAuth 2.0 Device Authorization Grant
        OAuth2DeviceConfig deviceConfig = newRealm.getOAuth2DeviceConfig();

        deviceConfig.setOAuth2DeviceCodeLifespan(rep.getOAuth2DeviceCodeLifespan());
        deviceConfig.setOAuth2DevicePollingInterval(rep.getOAuth2DevicePollingInterval());

        if (rep.getSslRequired() != null)
            newRealm.setSslRequired(SslRequired.valueOf(rep.getSslRequired().toUpperCase()));
        if (rep.isRegistrationAllowed() != null) newRealm.setRegistrationAllowed(rep.isRegistrationAllowed());
        if (rep.isRegistrationEmailAsUsername() != null)
            newRealm.setRegistrationEmailAsUsername(rep.isRegistrationEmailAsUsername());
        if (rep.isRememberMe() != null) newRealm.setRememberMe(rep.isRememberMe());
        if (rep.isVerifyEmail() != null) newRealm.setVerifyEmail(rep.isVerifyEmail());
        if (rep.isLoginWithEmailAllowed() != null) newRealm.setLoginWithEmailAllowed(rep.isLoginWithEmailAllowed());
        if (rep.isDuplicateEmailsAllowed() != null) newRealm.setDuplicateEmailsAllowed(rep.isDuplicateEmailsAllowed());
        if (rep.isResetPasswordAllowed() != null) newRealm.setResetPasswordAllowed(rep.isResetPasswordAllowed());
        if (rep.isEditUsernameAllowed() != null) newRealm.setEditUsernameAllowed(rep.isEditUsernameAllowed());
        if (rep.getLoginTheme() != null) newRealm.setLoginTheme(rep.getLoginTheme());
        if (rep.getAccountTheme() != null) newRealm.setAccountTheme(rep.getAccountTheme());
        if (rep.getAdminTheme() != null) newRealm.setAdminTheme(rep.getAdminTheme());
        if (rep.getEmailTheme() != null) newRealm.setEmailTheme(rep.getEmailTheme());

        // todo remove this stuff as its all deprecated
        if (rep.getRequiredCredentials() != null) {
            for (String requiredCred : rep.getRequiredCredentials()) {
                newRealm.addRequiredCredential(requiredCred);
            }
        } else {
            newRealm.addRequiredCredential(CredentialRepresentation.PASSWORD);
        }

        if (rep.getPasswordPolicy() != null)
            newRealm.setPasswordPolicy(PasswordPolicy.parse(session, rep.getPasswordPolicy()));
        if (rep.getOtpPolicyType() != null) newRealm.setOTPPolicy(toPolicy(rep));
        else newRealm.setOTPPolicy(OTPPolicy.DEFAULT_POLICY);

        WebAuthnPolicy webAuthnPolicy = getWebAuthnPolicyTwoFactor(rep);
        newRealm.setWebAuthnPolicy(webAuthnPolicy);

        webAuthnPolicy = getWebAuthnPolicyPasswordless(rep);
        newRealm.setWebAuthnPolicyPasswordless(webAuthnPolicy);

        updateCibaSettings(rep, newRealm);

        updateParSettings(rep, newRealm);

        Map<String, String> mappedFlows = importAuthenticationFlows(newRealm, rep);
        if (rep.getRequiredActions() != null) {
            for (RequiredActionProviderRepresentation action : rep.getRequiredActions()) {
                RequiredActionProviderModel model = toModel(action);

                MigrationUtils.updateOTPRequiredAction(model);

                newRealm.addRequiredActionProvider(model);
            }
            DefaultRequiredActions.addDeleteAccountAction(newRealm);
        } else {
            DefaultRequiredActions.addActions(newRealm);
        }

        importIdentityProviders(rep, newRealm, session);
        importIdentityProviderMappers(rep, newRealm);

        Map<String, ClientScopeModel> clientScopes = new HashMap<>();
        if (rep.getClientScopes() != null) {
            clientScopes = createClientScopes(session, rep.getClientScopes(), newRealm);
        }
        if (rep.getDefaultDefaultClientScopes() != null) {
            for (String clientScopeName : rep.getDefaultDefaultClientScopes()) {
                ClientScopeModel clientScope = clientScopes.get(clientScopeName);
                if (clientScope != null) {
                    newRealm.addDefaultClientScope(clientScope, true);
                } else {
                    logger.warnf("Referenced client scope '%s' doesn't exist", clientScopeName);
                }
            }
        }
        if (rep.getDefaultOptionalClientScopes() != null) {
            for (String clientScopeName : rep.getDefaultOptionalClientScopes()) {
                ClientScopeModel clientScope = clientScopes.get(clientScopeName);
                if (clientScope != null) {
                    newRealm.addDefaultClientScope(clientScope, false);
                } else {
                    logger.warnf("Referenced client scope '%s' doesn't exist", clientScopeName);
                }
            }
        }

        Map<String, ClientModel> createdClients = new HashMap<>();
        if (rep.getClients() != null) {
            createdClients = createClients(session, rep, newRealm, mappedFlows);
        }

        importRoles(rep.getRoles(), newRealm);
        convertDeprecatedDefaultRoles(rep, newRealm);

        // Now that all possible roles and clients are created, create scope mappings

        if (rep.getClientScopeMappings() != null) {

            for (Map.Entry<String, List<ScopeMappingRepresentation>> entry : rep.getClientScopeMappings().entrySet()) {
                ClientModel app = createdClients.computeIfAbsent(entry.getKey(), k -> newRealm.getClientByClientId(entry.getKey()));
                if (app == null) {
                    throw new RuntimeException("Unable to find client role mappings for client: " + entry.getKey());
                }
                createClientScopeMappings(newRealm, app, entry.getValue());
            }
        }

        if (rep.getScopeMappings() != null) {
            Map<String, RoleModel> roleModelMap = newRealm.getRolesStream().collect(Collectors.toMap(RoleModel::getId, Function.identity()));

            for (ScopeMappingRepresentation scope : rep.getScopeMappings()) {
                ScopeContainerModel scopeContainer = getScopeContainerHavingScope(newRealm, scope);
                for (String roleString : scope.getRoles()) {
                    final String roleStringTrimmed = roleString.trim();
                    RoleModel role = roleModelMap.computeIfAbsent(roleStringTrimmed, k -> newRealm.getRole(roleStringTrimmed));
                    if (role == null) {
                        role = newRealm.addRole(roleString);
                        roleModelMap.put(role.getId(), role);
                    }
                    scopeContainer.addScopeMapping(role);
                }
            }
        }

        if (rep.getSmtpServer() != null) {
            newRealm.setSmtpConfig(new HashMap(rep.getSmtpServer()));
        }

        if (rep.getBrowserSecurityHeaders() != null) {
            newRealm.setBrowserSecurityHeaders(rep.getBrowserSecurityHeaders());
        } else {
            newRealm.setBrowserSecurityHeaders(BrowserSecurityHeaders.realmDefaultHeaders);
        }

        if (rep.getComponents() != null) {
            MultivaluedHashMap<String, ComponentExportRepresentation> components = rep.getComponents();
            String parentId = newRealm.getId();
            importComponents(newRealm, components, parentId);
        }

        if (rep.getGroups() != null) {
            importGroups(newRealm, rep);
            if (rep.getDefaultGroups() != null) {
                for (String path : rep.getDefaultGroups()) {
                    GroupModel found = KeycloakModelUtils.findGroupByPath(newRealm, path);
                    if (found == null) throw new RuntimeException("default group in realm rep doesn't exist: " + path);
                    newRealm.addDefaultGroup(found);
                }
            }
        }


        // create users and their role mappings and social mappings

        if (rep.getUsers() != null) {
            for (UserRepresentation userRep : rep.getUsers()) {
                createUser(newRealm, userRep);
            }
        }

        if (!skipUserDependent) {
            importRealmAuthorizationSettings(rep, newRealm, session);
        }

        if (rep.isInternationalizationEnabled() != null) {
            newRealm.setInternationalizationEnabled(rep.isInternationalizationEnabled());
        }
        if (rep.getSupportedLocales() != null) {
            newRealm.setSupportedLocales(new HashSet<>(rep.getSupportedLocales()));
        }
        if (rep.getDefaultLocale() != null) {
            newRealm.setDefaultLocale(rep.getDefaultLocale());
        }

        // import attributes

        if (rep.getAttributes() != null) {
            for (Map.Entry<String, String> attr : rep.getAttributes().entrySet()) {
                newRealm.setAttribute(attr.getKey(), attr.getValue());
            }
        }

        if (!useNewImportMethod) {
            if (newRealm.getComponentsStream(newRealm.getId(), KeyProvider.class.getName()).count() == 0) {
                if (rep.getPrivateKey() != null) {
                    DefaultKeyProviders.createProviders(newRealm, rep.getPrivateKey(), rep.getCertificate());
                } else {
                    DefaultKeyProviders.createProviders(newRealm);
                }
            }
        } else {
            if (rep.getPrivateKey() != null) {

                ComponentModel rsa = new ComponentModel();
                rsa.setName("rsa");
                rsa.setParentId(newRealm.getId());
                rsa.setProviderId("rsa");
                rsa.setProviderType(KeyProvider.class.getName());

                MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
                config.putSingle("priority", "100");
                config.putSingle("privateKey", rep.getPrivateKey());
                if (rep.getCertificate() != null) {
                    config.putSingle("certificate", rep.getCertificate());
                }
                rsa.setConfig(config);

                newRealm.addComponentModel(rsa);
            }
        }
    }

    private static RoleModel getOrAddRealmRole(RealmModel realm, String name) {
        RoleModel role = realm.getRole(name);
        if (role == null) {
            role = realm.addRole(name);
        }
        return role;
    }

    public void exportRealm(RealmModel realm, ExportOptions options, ExportAdapter callback) {
        throw new ModelException("exporting for map storage is currently not supported");
    }

    @Override
    public RealmModel importRealm(InputStream requestBody) {
        /* A future implementation that would differentiate between the old JSON representations and the new file store
          might want to add the file name or the media type as a method parameter to switch between different implementations. */

        RealmRepresentation rep;
        try {
            rep = JsonSerialization.readValue(requestBody, RealmRepresentation.class);
        } catch (IOException e) {
            throw new ModelException("unable to read contents from stream", e);
        }
        logger.debugv("importRealm: {0}", rep.getRealm());

        if (!useNewImportMethod) {
            /* The import for the JSON representation might be called from the Admin UI, where it will be empty except for
               the realm name and if the realm is enabled. For that scenario, it would need to create all missing elements,
               which is done by firing an event to call the existing implementation in the RealmManager. */
            return ImportRealmFromRepresentationEvent.fire(session, rep);
        } else {
            /* This makes use of the representation to mimic the future setup: Some kind of import into a ConcurrentHashMap in-memory and then copying
               that over to the real store. This is the basis for future file store import. Results are different
               when importing, for example, an empty list of roles vs a non-existing list of roles, and possibility in other ways.
               Importing from a classic representation will eventually be removed and replaced when the new file store arrived. */
            return importToChmAndThenCopyOver(rep);
        }
    }


    @Override
    public PartialImportResults partialImportRealm(RealmModel realm, InputStream requestBody) {
        /* A future implementation that would differentiate between the old JSON representations and the new file store
          might want to add the file name or the media type as a method parameter to switch between different implementations. */

        PartialImportRepresentation rep;
        try {
            rep = JsonSerialization.readValue(requestBody, PartialImportRepresentation.class);
        } catch (IOException e) {
            throw new ModelException("unable to read contents from stream", e);
        }

        /* The import for the legacy JSON representation might be called from the Admin UI, and it allows for several options as part
         * of the representation. Therefore, direct this to the service layer with a (temporary) event so that the logic isn't duplicated
         * between legacy and map store.
         */
        return PartialImportRealmFromRepresentationEvent.fire(session, rep, realm);
    }

    private RealmModel importToChmAndThenCopyOver(RealmRepresentation rep) {
        String id = rep.getId();
        if (id == null || id.trim().isEmpty()) {
            id = KeycloakModelUtils.generateId();
        } else {
            ReservedCharValidator.validate(id);
        }

        ReservedCharValidator.validate(rep.getRealm());

        RealmModel realm;
        RealmModel currentRealm = session.getContext().getRealm();

        try {

            String _id = id;
            KeycloakModelUtils.runJobInTransaction(new ImportSessionFactoryWrapper(session.getKeycloakSessionFactory()), chmSession -> {
                // import the representation
                fillRealm(chmSession, _id, rep);

                // copy over the realm from in-memory to the real
                copyRealm(_id, chmSession);
                copyEntities(_id, chmSession, ClientProvider.class, ClientModel.class, ClientModel.SearchableFields.REALM_ID);
                copyEntities(_id, chmSession, ClientScopeProvider.class, ClientScopeModel.class, ClientScopeModel.SearchableFields.REALM_ID);
                copyEntities(_id, chmSession, GroupProvider.class, GroupModel.class, GroupModel.SearchableFields.REALM_ID);
                copyEntities(_id, chmSession, UserProvider.class, UserModel.class, UserModel.SearchableFields.REALM_ID);
                copyEntities(_id, chmSession, RoleProvider.class, RoleModel.class, RoleModel.SearchableFields.REALM_ID);

                // clear the CHM store
                chmSession.getTransactionManager().setRollbackOnly();
            });

            realm = session.realms().getRealm(id);
            session.getContext().setRealm(realm);
            setupMasterAdminManagement(realm);
            ImpersonationConstants.setupImpersonationService(session, realm);
            fireRealmPostCreate(realm);
        } finally {
            session.getContext().setRealm(currentRealm);
        }

        return realm;
    }

    private void copyRealm(String realmId, KeycloakSession sessionChm) {
        MapRealmEntity realmEntityChm = (MapRealmEntity) getTransaction(sessionChm, RealmProvider.class).read(realmId);
        getTransaction(session, RealmProvider.class).create(realmEntityChm);
    }

    private static <P extends Provider, E extends AbstractEntity, M> MapKeycloakTransaction<E, M> getTransaction(KeycloakSession session, Class<P> provider) {
        ProviderFactory<P> factoryChm = session.getKeycloakSessionFactory().getProviderFactory(provider);
        return ((AbstractMapProviderFactory<P, E, M>) factoryChm).getStorage(session).createTransaction(session);
    }

    private <P extends Provider, M> void copyEntities(String realmId, KeycloakSession sessionChm, Class<P> provider, Class<M> model, SearchableModelField<M> field) {
        MapKeycloakTransaction<AbstractEntity, M> txChm = getTransaction(sessionChm, provider);
        MapKeycloakTransaction<AbstractEntity, M> txOrig = getTransaction(session, provider);

        DefaultModelCriteria<M> mcb = criteria();
        mcb = mcb.compare(field, ModelCriteriaBuilder.Operator.EQ, realmId);

        txChm.read(withCriteria(mcb)).forEach(txOrig::create);
    }

    private static void fillRealm(KeycloakSession session, String id, RealmRepresentation rep) {
        RealmModel realm = session.realms().createRealm(id, rep.getRealm());
        session.getContext().setRealm(realm);
        SetDefaultsForNewRealm.fire(session, realm);
        MapExportImportManager mapExportImportManager = new MapExportImportManager(session);
        mapExportImportManager.clearDefaultsThatConflictWithRepresentation(rep, realm);
        mapExportImportManager.importRealm(rep, realm, false);
    }

    private void clearDefaultsThatConflictWithRepresentation(RealmRepresentation rep, RealmModel newRealm) {
        if (rep.getDefaultRole() != null) {
            if (newRealm.getDefaultRole() != null) {
                newRealm.removeRole(newRealm.getDefaultRole());
                // set the new role here already as the legacy code expects it this way
                newRealm.setDefaultRole(RepresentationToModel.createRole(newRealm, rep.getDefaultRole()));
            }
        }

        if (rep.getRequiredActions() != null) {
            for (RequiredActionProviderRepresentation action : rep.getRequiredActions()) {
                RequiredActionProviderModel requiredActionProviderByAlias = newRealm.getRequiredActionProviderByAlias(action.getAlias());
                if (requiredActionProviderByAlias != null) {
                    newRealm.removeRequiredActionProvider(requiredActionProviderByAlias);
                }
            }
        }

        if (rep.getRoles() != null) {
            for (RoleRepresentation representation : rep.getRoles().getRealm()) {
                RoleModel role = newRealm.getRole(representation.getName());
                if (role != null && (newRealm.getDefaultRole() == null || newRealm.getDefaultRole() != null && !Objects.equals(role.getId(), newRealm.getDefaultRole().getId()))) {
                    newRealm.removeRole(role);
                }
            }
        }

        if (rep.getPrivateKey() != null) {
            newRealm.getComponentsStream(newRealm.getId(), KeyProvider.class.getName())
                    .filter(component -> Objects.equals(component.getProviderId(), "rsa-generated") || Objects.equals(component.getProviderId(), "rsa-enc-generated"))
                    .collect(Collectors.toList()).forEach(newRealm::removeComponent);
            // will later create the "rsa" provider
        }

        if (rep.getClients() != null) {
            for (ClientRepresentation resourceRep : rep.getClients()) {
                ClientModel clientByClientId = newRealm.getClientByClientId(resourceRep.getClientId());
                if (clientByClientId != null) {
                    newRealm.removeClient(clientByClientId.getId());
                }
            }
        }

        if (rep.getClientScopes() != null) {
            for (ClientScopeRepresentation resourceRep : rep.getClientScopes()) {
                Optional<ClientScopeModel> existingClientScope = newRealm.getClientScopesStream().filter(clientScopeModel -> clientScopeModel.getName().equals(resourceRep.getName())).findFirst();
                if (existingClientScope.isPresent()) {
                    newRealm.removeClientScope(existingClientScope.get().getId());
                }
            }
        }

        if (rep.getComponents() != null) {
            clearExistingComponents(newRealm, rep.getComponents());
        }
    }

    protected static void clearExistingComponents(RealmModel newRealm, MultivaluedHashMap<String, ComponentExportRepresentation> components) {
        for (Map.Entry<String, List<ComponentExportRepresentation>> entry : components.entrySet()) {
            String providerType = entry.getKey();
            for (ComponentExportRepresentation compRep : entry.getValue()) {
                newRealm.getComponentsStream(newRealm.getId(), providerType)
                        .filter(component -> Objects.equals(component.getProviderId(), compRep.getProviderId())).findAny().ifPresent(newRealm::removeComponent);
                if (compRep.getSubComponents() != null) {
                    clearExistingComponents(newRealm, compRep.getSubComponents());
                }
            }
        }
    }

    public void setupMasterAdminManagement(RealmModel realm) {
        // Need to refresh masterApp for current realm
        String adminRealmName = Config.getAdminRealm();
        RealmModel adminRealm = session.realms().getRealmByName(adminRealmName);
        ClientModel masterApp = adminRealm.getClientByClientId(KeycloakModelUtils.getMasterRealmAdminApplicationClientId(realm.getName()));
        if (masterApp == null) {
            createMasterAdminManagement(realm);
            return;
        }
        realm.setMasterAdminClient(masterApp);
    }

    private void createMasterAdminManagement(RealmModel realm) {
        RealmModel adminRealm;
        RoleModel adminRole;

        if (realm.getName().equals(Config.getAdminRealm())) {
            adminRealm = realm;

            adminRole = realm.addRole(AdminRoles.ADMIN);

            RoleModel createRealmRole = realm.addRole(AdminRoles.CREATE_REALM);
            adminRole.addCompositeRole(createRealmRole);
            createRealmRole.setDescription("${role_" + AdminRoles.CREATE_REALM + "}");
        } else {
            adminRealm = session.realms().getRealmByName(Config.getAdminRealm());
            adminRole = adminRealm.getRole(AdminRoles.ADMIN);
        }
        adminRole.setDescription("${role_" + AdminRoles.ADMIN + "}");

        ClientModel realmAdminApp = KeycloakModelUtils.createManagementClient(adminRealm, KeycloakModelUtils.getMasterRealmAdminApplicationClientId(realm.getName()));
        // No localized name for now
        realmAdminApp.setName(realm.getName() + " Realm");
        realm.setMasterAdminClient(realmAdminApp);

        for (String r : AdminRoles.ALL_REALM_ROLES) {
            RoleModel role = realmAdminApp.addRole(r);
            role.setDescription("${role_" + r + "}");
            adminRole.addCompositeRole(role);
        }
        addQueryCompositeRoles(realmAdminApp);
    }

    public void addQueryCompositeRoles(ClientModel realmAccess) {
        RoleModel queryClients = realmAccess.getRole(AdminRoles.QUERY_CLIENTS);
        RoleModel queryUsers = realmAccess.getRole(AdminRoles.QUERY_USERS);
        RoleModel queryGroups = realmAccess.getRole(AdminRoles.QUERY_GROUPS);

        RoleModel viewClients = realmAccess.getRole(AdminRoles.VIEW_CLIENTS);
        viewClients.addCompositeRole(queryClients);
        RoleModel viewUsers = realmAccess.getRole(AdminRoles.VIEW_USERS);
        viewUsers.addCompositeRole(queryUsers);
        viewUsers.addCompositeRole(queryGroups);
    }

    private void fireRealmPostCreate(RealmModel realm) {
        session.getKeycloakSessionFactory().publish(new RealmModel.RealmPostCreateEvent() {
            @Override
            public RealmModel getCreatedRealm() {
                return realm;
            }
            @Override
            public KeycloakSession getKeycloakSession() {
                return session;
            }
        });

    }

    private static void convertDeprecatedDefaultRoles(RealmRepresentation rep, RealmModel newRealm) {
        if (rep.getDefaultRole() == null) {

            // Setup realm default roles
            if (rep.getDefaultRoles() != null) {
                rep.getDefaultRoles().stream()
                        .map(String::trim)
                        .map(name -> getOrAddRealmRole(newRealm, name))
                        .forEach(role -> newRealm.getDefaultRole().addCompositeRole(role));
            }

            // Setup client default roles
            if (rep.getClients() != null) {
                for (ClientRepresentation clientRep : rep.getClients()) {
                    if (clientRep.getDefaultRoles() != null) {
                        Arrays.stream(clientRep.getDefaultRoles())
                                .map(String::trim)
                                .map(name -> getOrAddClientRole(newRealm.getClientById(clientRep.getId()), name))
                                .forEach(role -> newRealm.getDefaultRole().addCompositeRole(role));
                    }
                }
            }
        }
    }

    private static RoleModel getOrAddClientRole(ClientModel client, String name) {
        RoleModel role = client.getRole(name);
        if (role == null) {
            role = client.addRole(name);
        }
        return role;
    }

    private static Map<String, ClientModel> createClients(KeycloakSession session, RealmRepresentation rep, RealmModel realm, Map<String, String> mappedFlows) {
        Map<String, ClientModel> appMap = new HashMap<>();
        for (ClientRepresentation resourceRep : rep.getClients()) {
            ClientModel app = RepresentationToModel.createClient(session, realm, resourceRep, mappedFlows);
            String postLogoutRedirectUris = app.getAttribute(OIDCConfigAttributes.POST_LOGOUT_REDIRECT_URIS);
            if (postLogoutRedirectUris == null) {
                app.setAttribute(OIDCConfigAttributes.POST_LOGOUT_REDIRECT_URIS, "+");
            }
            appMap.put(app.getClientId(), app);

            ValidationUtil.validateClient(session, app, false, r -> {
                throw new RuntimeException("Invalid client " + app.getClientId() + ": " + r.getAllErrorsAsString());
            });
        }
        return appMap;
    }

    private static Map<String, ClientScopeModel> createClientScopes(KeycloakSession session, List<ClientScopeRepresentation> clientScopes, RealmModel realm) {
        Map<String, ClientScopeModel> appMap = new HashMap<>();
        for (ClientScopeRepresentation resourceRep : clientScopes) {
            ClientScopeModel app = RepresentationToModel.createClientScope(session, realm, resourceRep);
            appMap.put(app.getName(), app);
        }
        return appMap;
    }

    private static void importIdentityProviders(RealmRepresentation rep, RealmModel newRealm, KeycloakSession session) {
        if (rep.getIdentityProviders() != null) {
            for (IdentityProviderRepresentation representation : rep.getIdentityProviders()) {
                newRealm.addIdentityProvider(RepresentationToModel.toModel(newRealm, representation, session));
            }
        }
    }

    private static void importIdentityProviderMappers(RealmRepresentation rep, RealmModel newRealm) {
        if (rep.getIdentityProviderMappers() != null) {
            for (IdentityProviderMapperRepresentation representation : rep.getIdentityProviderMappers()) {
                newRealm.addIdentityProviderMapper(RepresentationToModel.toModel(representation));
            }
        }
    }

    public static long getClaimsMask(ClaimRepresentation rep) {
        long mask = ClaimMask.ALL;

        if (rep.getAddress()) {
            mask |= ClaimMask.ADDRESS;
        } else {
            mask &= ~ClaimMask.ADDRESS;
        }
        if (rep.getEmail()) {
            mask |= ClaimMask.EMAIL;
        } else {
            mask &= ~ClaimMask.EMAIL;
        }
        if (rep.getGender()) {
            mask |= ClaimMask.GENDER;
        } else {
            mask &= ~ClaimMask.GENDER;
        }
        if (rep.getLocale()) {
            mask |= ClaimMask.LOCALE;
        } else {
            mask &= ~ClaimMask.LOCALE;
        }
        if (rep.getName()) {
            mask |= ClaimMask.NAME;
        } else {
            mask &= ~ClaimMask.NAME;
        }
        if (rep.getPhone()) {
            mask |= ClaimMask.PHONE;
        } else {
            mask &= ~ClaimMask.PHONE;
        }
        if (rep.getPicture()) {
            mask |= ClaimMask.PICTURE;
        } else {
            mask &= ~ClaimMask.PICTURE;
        }
        if (rep.getProfile()) {
            mask |= ClaimMask.PROFILE;
        } else {
            mask &= ~ClaimMask.PROFILE;
        }
        if (rep.getUsername()) {
            mask |= ClaimMask.USERNAME;
        } else {
            mask &= ~ClaimMask.USERNAME;
        }
        if (rep.getWebsite()) {
            mask |= ClaimMask.WEBSITE;
        } else {
            mask &= ~ClaimMask.WEBSITE;
        }
        return mask;
    }

    public static void createClientScopeMappings(RealmModel realm, ClientModel clientModel, List<ScopeMappingRepresentation> mappings) {
        for (ScopeMappingRepresentation mapping : mappings) {
            ScopeContainerModel scopeContainer = getScopeContainerHavingScope(realm, mapping);

            for (String roleString : mapping.getRoles()) {
                RoleModel role = clientModel.getRole(roleString.trim());
                if (role == null) {
                    role = clientModel.addRole(roleString.trim());
                }
                scopeContainer.addScopeMapping(role);
            }
        }
    }

    private static ScopeContainerModel getScopeContainerHavingScope(RealmModel realm, ScopeMappingRepresentation scope) {
        if (scope.getClient() != null) {
            ClientModel client = realm.getClientByClientId(scope.getClient());
            if (client == null) {
                throw new RuntimeException("Unknown client specification in scope mappings: " + scope.getClient());
            }
            return client;
        } else if (scope.getClientScope() != null) {
            ClientScopeModel clientScope = KeycloakModelUtils.getClientScopeByName(realm, scope.getClientScope());
            if (clientScope == null) {
                throw new RuntimeException("Unknown clientScope specification in scope mappings: " + scope.getClientScope());
            }
            return clientScope;
        } else if (scope.getClientTemplate() != null) { // Backwards compatibility
            String templateName = KeycloakModelUtils.convertClientScopeName(scope.getClientTemplate());
            ClientScopeModel clientTemplate = KeycloakModelUtils.getClientScopeByName(realm, templateName);
            if (clientTemplate == null) {
                throw new RuntimeException("Unknown clientScope specification in scope mappings: " + templateName);
            }
            return clientTemplate;
        } else {
            throw new RuntimeException("Either client or clientScope needs to be specified in scope mappings");
        }
    }


    public static void renameRealm(RealmModel realm, String name) {
        if (name.equals(realm.getName())) return;

        String oldName = realm.getName();

        ClientModel masterApp = realm.getMasterAdminClient();
        masterApp.setClientId(KeycloakModelUtils.getMasterRealmAdminApplicationClientId(name));
        realm.setName(name);

        ClientModel adminClient = realm.getClientByClientId(Constants.ADMIN_CONSOLE_CLIENT_ID);
        if (adminClient != null) {
            if (adminClient.getBaseUrl() != null) {
                adminClient.setBaseUrl(adminClient.getBaseUrl().replace("/admin/" + oldName + "/", "/admin/" + name + "/"));
            }
            Set<String> adminRedirectUris = new HashSet<>();
            for (String r : adminClient.getRedirectUris()) {
                adminRedirectUris.add(replace(r, "/admin/" + oldName + "/", "/admin/" + name + "/"));
            }
            adminClient.setRedirectUris(adminRedirectUris);
        }

        ClientModel accountClient = realm.getClientByClientId(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID);
        if (accountClient != null) {
            if (accountClient.getBaseUrl() != null) {
                accountClient.setBaseUrl(accountClient.getBaseUrl().replace("/realms/" + oldName + "/", "/realms/" + name + "/"));
            }
            Set<String> accountRedirectUris = new HashSet<>();
            for (String r : accountClient.getRedirectUris()) {
                accountRedirectUris.add(replace(r, "/realms/" + oldName + "/", "/realms/" + name + "/"));
            }
            accountClient.setRedirectUris(accountRedirectUris);
        }
    }

    private static String replace(String url, String target, String replacement) {
        return url != null ? url.replace(target, replacement) : null;
    }

    @Override
    public void updateRealm(RealmRepresentation rep, RealmModel realm) {
        if (rep.getRealm() != null) {
            renameRealm(realm, rep.getRealm());
        }

        if (!Boolean.parseBoolean(rep.getAttributesOrEmpty().get("userProfileEnabled"))) {
            UserProfileProvider provider = session.getProvider(UserProfileProvider.class);
            provider.setConfiguration(null);
        }

        // Import attributes first, so the stuff saved directly on representation (displayName, bruteForce etc) has bigger priority
        if (rep.getAttributes() != null) {
            Set<String> attrsToRemove = new HashSet<>(realm.getAttributes().keySet());
            attrsToRemove.removeAll(rep.getAttributes().keySet());

            for (Map.Entry<String, String> entry : rep.getAttributes().entrySet()) {
                realm.setAttribute(entry.getKey(), entry.getValue());
            }

            for (String attr : attrsToRemove) {
                realm.removeAttribute(attr);
            }
        }

        if (rep.getDisplayName() != null) realm.setDisplayName(rep.getDisplayName());
        if (rep.getDisplayNameHtml() != null) realm.setDisplayNameHtml(rep.getDisplayNameHtml());
        if (rep.isEnabled() != null) realm.setEnabled(rep.isEnabled());
        if (rep.isUserManagedAccessAllowed() != null) realm.setUserManagedAccessAllowed(rep.isUserManagedAccessAllowed());
        if (rep.isBruteForceProtected() != null) realm.setBruteForceProtected(rep.isBruteForceProtected());
        if (rep.isPermanentLockout() != null) realm.setPermanentLockout(rep.isPermanentLockout());
        if (rep.getMaxFailureWaitSeconds() != null) realm.setMaxFailureWaitSeconds(rep.getMaxFailureWaitSeconds());
        if (rep.getMinimumQuickLoginWaitSeconds() != null)
            realm.setMinimumQuickLoginWaitSeconds(rep.getMinimumQuickLoginWaitSeconds());
        if (rep.getWaitIncrementSeconds() != null) realm.setWaitIncrementSeconds(rep.getWaitIncrementSeconds());
        if (rep.getQuickLoginCheckMilliSeconds() != null)
            realm.setQuickLoginCheckMilliSeconds(rep.getQuickLoginCheckMilliSeconds());
        if (rep.getMaxDeltaTimeSeconds() != null) realm.setMaxDeltaTimeSeconds(rep.getMaxDeltaTimeSeconds());
        if (rep.getFailureFactor() != null) realm.setFailureFactor(rep.getFailureFactor());
        if (rep.isRegistrationAllowed() != null) realm.setRegistrationAllowed(rep.isRegistrationAllowed());
        if (rep.isRegistrationEmailAsUsername() != null)
            realm.setRegistrationEmailAsUsername(rep.isRegistrationEmailAsUsername());
        if (rep.isRememberMe() != null) realm.setRememberMe(rep.isRememberMe());
        if (rep.isVerifyEmail() != null) realm.setVerifyEmail(rep.isVerifyEmail());
        if (rep.isLoginWithEmailAllowed() != null) realm.setLoginWithEmailAllowed(rep.isLoginWithEmailAllowed());
        if (rep.isDuplicateEmailsAllowed() != null) realm.setDuplicateEmailsAllowed(rep.isDuplicateEmailsAllowed());
        if (rep.isResetPasswordAllowed() != null) realm.setResetPasswordAllowed(rep.isResetPasswordAllowed());
        if (rep.isEditUsernameAllowed() != null) realm.setEditUsernameAllowed(rep.isEditUsernameAllowed());
        if (rep.getSslRequired() != null) realm.setSslRequired(SslRequired.valueOf(rep.getSslRequired().toUpperCase()));
        if (rep.getAccessCodeLifespan() != null) realm.setAccessCodeLifespan(rep.getAccessCodeLifespan());
        if (rep.getAccessCodeLifespanUserAction() != null)
            realm.setAccessCodeLifespanUserAction(rep.getAccessCodeLifespanUserAction());
        if (rep.getAccessCodeLifespanLogin() != null)
            realm.setAccessCodeLifespanLogin(rep.getAccessCodeLifespanLogin());
        if (rep.getActionTokenGeneratedByAdminLifespan() != null)
            realm.setActionTokenGeneratedByAdminLifespan(rep.getActionTokenGeneratedByAdminLifespan());
        if (rep.getActionTokenGeneratedByUserLifespan() != null)
            realm.setActionTokenGeneratedByUserLifespan(rep.getActionTokenGeneratedByUserLifespan());

        OAuth2DeviceConfig deviceConfig = realm.getOAuth2DeviceConfig();

        deviceConfig.setOAuth2DeviceCodeLifespan(rep.getOAuth2DeviceCodeLifespan());
        deviceConfig.setOAuth2DevicePollingInterval(rep.getOAuth2DevicePollingInterval());

        if (rep.getNotBefore() != null) realm.setNotBefore(rep.getNotBefore());
        if (rep.getDefaultSignatureAlgorithm() != null) realm.setDefaultSignatureAlgorithm(rep.getDefaultSignatureAlgorithm());
        if (rep.getRevokeRefreshToken() != null) realm.setRevokeRefreshToken(rep.getRevokeRefreshToken());
        if (rep.getRefreshTokenMaxReuse() != null) realm.setRefreshTokenMaxReuse(rep.getRefreshTokenMaxReuse());
        if (rep.getAccessTokenLifespan() != null) realm.setAccessTokenLifespan(rep.getAccessTokenLifespan());
        if (rep.getAccessTokenLifespanForImplicitFlow() != null)
            realm.setAccessTokenLifespanForImplicitFlow(rep.getAccessTokenLifespanForImplicitFlow());
        if (rep.getSsoSessionIdleTimeout() != null) realm.setSsoSessionIdleTimeout(rep.getSsoSessionIdleTimeout());
        if (rep.getSsoSessionMaxLifespan() != null) realm.setSsoSessionMaxLifespan(rep.getSsoSessionMaxLifespan());
        if (rep.getSsoSessionIdleTimeoutRememberMe() != null) realm.setSsoSessionIdleTimeoutRememberMe(rep.getSsoSessionIdleTimeoutRememberMe());
        if (rep.getSsoSessionMaxLifespanRememberMe() != null) realm.setSsoSessionMaxLifespanRememberMe(rep.getSsoSessionMaxLifespanRememberMe());
        if (rep.getOfflineSessionIdleTimeout() != null)
            realm.setOfflineSessionIdleTimeout(rep.getOfflineSessionIdleTimeout());
        // KEYCLOAK-7688 Offline Session Max for Offline Token
        if (rep.getOfflineSessionMaxLifespanEnabled() != null) realm.setOfflineSessionMaxLifespanEnabled(rep.getOfflineSessionMaxLifespanEnabled());
        if (rep.getOfflineSessionMaxLifespan() != null)
            realm.setOfflineSessionMaxLifespan(rep.getOfflineSessionMaxLifespan());
        if (rep.getClientSessionIdleTimeout() != null)
            realm.setClientSessionIdleTimeout(rep.getClientSessionIdleTimeout());
        if (rep.getClientSessionMaxLifespan() != null)
            realm.setClientSessionMaxLifespan(rep.getClientSessionMaxLifespan());
        if (rep.getClientOfflineSessionIdleTimeout() != null)
            realm.setClientOfflineSessionIdleTimeout(rep.getClientOfflineSessionIdleTimeout());
        if (rep.getClientOfflineSessionMaxLifespan() != null)
            realm.setClientOfflineSessionMaxLifespan(rep.getClientOfflineSessionMaxLifespan());
        if (rep.getRequiredCredentials() != null) {
            realm.updateRequiredCredentials(rep.getRequiredCredentials());
        }
        if (rep.getLoginTheme() != null) realm.setLoginTheme(rep.getLoginTheme());
        if (rep.getAccountTheme() != null) realm.setAccountTheme(rep.getAccountTheme());
        if (rep.getAdminTheme() != null) realm.setAdminTheme(rep.getAdminTheme());
        if (rep.getEmailTheme() != null) realm.setEmailTheme(rep.getEmailTheme());

        if (rep.isEventsEnabled() != null) realm.setEventsEnabled(rep.isEventsEnabled());
        if (rep.getEventsExpiration() != null) realm.setEventsExpiration(rep.getEventsExpiration());
        if (rep.getEventsListeners() != null) realm.setEventsListeners(new HashSet<>(rep.getEventsListeners()));
        if (rep.getEnabledEventTypes() != null) realm.setEnabledEventTypes(new HashSet<>(rep.getEnabledEventTypes()));

        if (rep.isAdminEventsEnabled() != null) realm.setAdminEventsEnabled(rep.isAdminEventsEnabled());
        if (rep.isAdminEventsDetailsEnabled() != null)
            realm.setAdminEventsDetailsEnabled(rep.isAdminEventsDetailsEnabled());


        if (rep.getPasswordPolicy() != null)
            realm.setPasswordPolicy(PasswordPolicy.parse(session, rep.getPasswordPolicy()));
        if (rep.getOtpPolicyType() != null) realm.setOTPPolicy(toPolicy(rep));

        WebAuthnPolicy webAuthnPolicy = getWebAuthnPolicyTwoFactor(rep);
        realm.setWebAuthnPolicy(webAuthnPolicy);

        webAuthnPolicy = getWebAuthnPolicyPasswordless(rep);
        realm.setWebAuthnPolicyPasswordless(webAuthnPolicy);

        updateCibaSettings(rep, realm);
        updateParSettings(rep, realm);
        session.clientPolicy().updateRealmModelFromRepresentation(realm, rep);

        if (rep.getSmtpServer() != null) {
            Map<String, String> config = new HashMap(rep.getSmtpServer());
            if (rep.getSmtpServer().containsKey("password") && ComponentRepresentation.SECRET_VALUE.equals(rep.getSmtpServer().get("password"))) {
                String passwordValue = realm.getSmtpConfig() != null ? realm.getSmtpConfig().get("password") : null;
                config.put("password", passwordValue);
            }
            realm.setSmtpConfig(config);
        }

        if (rep.getBrowserSecurityHeaders() != null) {
            realm.setBrowserSecurityHeaders(rep.getBrowserSecurityHeaders());
        }

        if (rep.isInternationalizationEnabled() != null) {
            realm.setInternationalizationEnabled(rep.isInternationalizationEnabled());
        }
        if (rep.getSupportedLocales() != null) {
            realm.setSupportedLocales(new HashSet<>(rep.getSupportedLocales()));
        }
        if (rep.getDefaultLocale() != null) {
            realm.setDefaultLocale(rep.getDefaultLocale());
        }
        if (rep.getBrowserFlow() != null) {
            realm.setBrowserFlow(realm.getFlowByAlias(rep.getBrowserFlow()));
        }
        if (rep.getRegistrationFlow() != null) {
            realm.setRegistrationFlow(realm.getFlowByAlias(rep.getRegistrationFlow()));
        }
        if (rep.getDirectGrantFlow() != null) {
            realm.setDirectGrantFlow(realm.getFlowByAlias(rep.getDirectGrantFlow()));
        }
        if (rep.getResetCredentialsFlow() != null) {
            realm.setResetCredentialsFlow(realm.getFlowByAlias(rep.getResetCredentialsFlow()));
        }
        if (rep.getClientAuthenticationFlow() != null) {
            realm.setClientAuthenticationFlow(realm.getFlowByAlias(rep.getClientAuthenticationFlow()));
        }
        if (rep.getDockerAuthenticationFlow() != null) {
            realm.setDockerAuthenticationFlow(realm.getFlowByAlias(rep.getDockerAuthenticationFlow()));
        }
    }

    @Override
    public UserModel createUser(RealmModel newRealm, UserRepresentation userRep) {
        convertDeprecatedSocialProviders(userRep);

        // Import users just to user storage. Don't federate
        UserModel user = session.users().addUser(newRealm, userRep.getId(), userRep.getUsername(), false, false);
        user.setEnabled(userRep.isEnabled() != null && userRep.isEnabled());
        user.setCreatedTimestamp(userRep.getCreatedTimestamp());
        user.setEmail(userRep.getEmail());
        if (userRep.isEmailVerified() != null) user.setEmailVerified(userRep.isEmailVerified());
        user.setFirstName(userRep.getFirstName());
        user.setLastName(userRep.getLastName());
        user.setFederationLink(userRep.getFederationLink());
        if (userRep.getAttributes() != null) {
            for (Map.Entry<String, List<String>> entry : userRep.getAttributes().entrySet()) {
                List<String> value = entry.getValue();
                if (value != null) {
                    user.setAttribute(entry.getKey(), new ArrayList<>(value));
                }
            }
        }
        if (userRep.getRequiredActions() != null) {
            for (String requiredAction : userRep.getRequiredActions()) {
                user.addRequiredAction(getDefaultRequiredActionCaseInsensitively(requiredAction));
            }
        }
        createCredentials(userRep, session, newRealm, user, false);
        createFederatedIdentities(userRep, session, newRealm, user);
        createRoleMappings(userRep, user, newRealm);
        if (userRep.getClientConsents() != null) {
            for (UserConsentRepresentation consentRep : userRep.getClientConsents()) {
                UserConsentModel consentModel = RepresentationToModel.toModel(newRealm, consentRep);
                session.users().addConsent(newRealm, user.getId(), consentModel);
            }
        }

        if (userRep.getNotBefore() != null) {
            session.users().setNotBeforeForUser(newRealm, user, userRep.getNotBefore());
        }

        if (userRep.getServiceAccountClientId() != null) {
            String clientId = userRep.getServiceAccountClientId();
            ClientModel client = newRealm.getClientByClientId(clientId);
            if (client == null) {
                throw new RuntimeException("Unable to find client specified for service account link. Client: " + clientId);
            }
            user.setServiceAccountClientLink(client.getId());
        }
        createGroups(userRep, newRealm, user);
        return user;
    }

    public static void convertDeprecatedSocialProviders(UserRepresentation user) {
        if (user.getSocialLinks() != null && !user.getSocialLinks().isEmpty() && user.getFederatedIdentities() == null) {
            logger.warnf("Using deprecated 'socialLinks' configuration in for user '%s' was removed", user.getUsername());
        }

        user.setSocialLinks(null);
    }

    private static void convertDeprecatedApplications(KeycloakSession session, RealmRepresentation realm) {
        if (realm.getApplications() != null || realm.getOauthClients() != null) {
            if (realm.getClients() == null) {
                realm.setClients(new LinkedList<>());
            }

            List<ApplicationRepresentation> clients = new LinkedList<>();
            if (realm.getApplications() != null) {
                clients.addAll(realm.getApplications());
            }
            if (realm.getOauthClients() != null) {
                clients.addAll(realm.getOauthClients());
            }

            for (ApplicationRepresentation app : clients) {
                app.setClientId(app.getName());
                app.setName(null);

                if (app instanceof OAuthClientRepresentation) {
                    app.setConsentRequired(true);
                    app.setFullScopeAllowed(false);
                }

                if (app.getProtocolMappers() == null && app.getClaims() != null) {
                    long mask = getClaimsMask(app.getClaims());
                    List<ProtocolMapperRepresentation> convertedProtocolMappers = session.getProvider(MigrationProvider.class).getMappersForClaimMask(mask);
                    app.setProtocolMappers(convertedProtocolMappers);
                    app.setClaims(null);
                }

                realm.getClients().add(app);
            }
        }

        if (realm.getApplicationScopeMappings() != null && realm.getClientScopeMappings() == null) {
            realm.setClientScopeMappings(realm.getApplicationScopeMappings());
        }

        if (realm.getRoles() != null && realm.getRoles().getApplication() != null && realm.getRoles().getClient() == null) {
            realm.getRoles().setClient(realm.getRoles().getApplication());
        }

        if (realm.getUsers() != null) {
            for (UserRepresentation user : realm.getUsers()) {
                if (user.getApplicationRoles() != null && user.getClientRoles() == null) {
                    user.setClientRoles(user.getApplicationRoles());
                }
            }
        }

        if (realm.getRoles() != null && realm.getRoles().getRealm() != null) {
            for (RoleRepresentation role : realm.getRoles().getRealm()) {
                if (role.getComposites() != null && role.getComposites().getApplication() != null && role.getComposites().getClient() == null) {
                    role.getComposites().setClient(role.getComposites().getApplication());
                }
            }
        }

        if (realm.getRoles() != null && realm.getRoles().getClient() != null) {
            for (Map.Entry<String, List<RoleRepresentation>> clientRoles : realm.getRoles().getClient().entrySet()) {
                for (RoleRepresentation role : clientRoles.getValue()) {
                    if (role.getComposites() != null && role.getComposites().getApplication() != null && role.getComposites().getClient() == null) {
                        role.getComposites().setClient(role.getComposites().getApplication());
                    }
                }
            }
        }
    }

    private static void convertDeprecatedSocialProviders(RealmRepresentation rep) {
        if (rep.isSocial() != null && rep.isSocial() && rep.getSocialProviders() != null && !rep.getSocialProviders().isEmpty() && rep.getIdentityProviders() == null) {
            Boolean updateProfileFirstLogin = rep.isUpdateProfileOnInitialSocialLogin() != null && rep.isUpdateProfileOnInitialSocialLogin();
            if (rep.getSocialProviders() != null) {

                logger.warn("Using deprecated 'social' configuration in JSON representation. It will be removed in future versions");
                List<IdentityProviderRepresentation> identityProviders = new LinkedList<>();
                for (String k : rep.getSocialProviders().keySet()) {
                    if (k.endsWith(".key")) {
                        String providerId = k.split("\\.")[0];
                        String key = rep.getSocialProviders().get(k);
                        String secret = rep.getSocialProviders().get(k.replace(".key", ".secret"));

                        IdentityProviderRepresentation identityProvider = new IdentityProviderRepresentation();
                        identityProvider.setAlias(providerId);
                        identityProvider.setProviderId(providerId);
                        identityProvider.setEnabled(true);
                        identityProvider.setLinkOnly(false);
                        identityProvider.setUpdateProfileFirstLogin(updateProfileFirstLogin);

                        Map<String, String> config = new HashMap<>();
                        config.put("clientId", key);
                        config.put("clientSecret", secret);
                        identityProvider.setConfig(config);

                        identityProviders.add(identityProvider);
                    }
                }
                rep.setIdentityProviders(identityProviders);
            }
        }
    }

    private static void convertDeprecatedClientTemplates(RealmRepresentation realm) {
        if (realm.getClientTemplates() != null) {

            logger.warnf("Using deprecated 'clientTemplates' configuration in JSON representation for realm '%s'. It will be removed in future versions", realm.getRealm());

            List<ClientScopeRepresentation> clientScopes = new LinkedList<>();
            for (ClientTemplateRepresentation template : realm.getClientTemplates()) {
                ClientScopeRepresentation scopeRep = new ClientScopeRepresentation();
                scopeRep.setId(template.getId());
                scopeRep.setName(template.getName());
                scopeRep.setProtocol(template.getProtocol());
                scopeRep.setDescription(template.getDescription());
                scopeRep.setAttributes(template.getAttributes());
                scopeRep.setProtocolMappers(template.getProtocolMappers());

                clientScopes.add(scopeRep);
            }

            realm.setClientScopes(clientScopes);
        }
    }



    protected static void importComponents(RealmModel newRealm, MultivaluedHashMap<String, ComponentExportRepresentation> components, String parentId) {
        for (Map.Entry<String, List<ComponentExportRepresentation>> entry : components.entrySet()) {
            String providerType = entry.getKey();
            for (ComponentExportRepresentation compRep : entry.getValue()) {
                ComponentModel component = new ComponentModel();
                component.setId(compRep.getId());
                component.setName(compRep.getName());
                component.setConfig(compRep.getConfig());
                component.setProviderType(providerType);
                component.setProviderId(compRep.getProviderId());
                component.setSubType(compRep.getSubType());
                component.setParentId(parentId);
                component = newRealm.importComponentModel(component);
                if (compRep.getSubComponents() != null) {
                    importComponents(newRealm, compRep.getSubComponents(), component.getId());
                }
            }
        }
    }

    public static void importGroups(RealmModel realm, RealmRepresentation rep) {
        List<GroupRepresentation> groups = rep.getGroups();
        if (groups == null) return;

        GroupModel parent = null;
        for (GroupRepresentation group : groups) {
            importGroup(realm, parent, group);
        }
    }

    private static WebAuthnPolicy getWebAuthnPolicyTwoFactor(RealmRepresentation rep) {
        WebAuthnPolicy webAuthnPolicy = new WebAuthnPolicy();

        String webAuthnPolicyRpEntityName = rep.getWebAuthnPolicyRpEntityName();
        if (webAuthnPolicyRpEntityName == null || webAuthnPolicyRpEntityName.isEmpty())
            webAuthnPolicyRpEntityName = Constants.DEFAULT_WEBAUTHN_POLICY_RP_ENTITY_NAME;
        webAuthnPolicy.setRpEntityName(webAuthnPolicyRpEntityName);

        List<String> webAuthnPolicySignatureAlgorithms = rep.getWebAuthnPolicySignatureAlgorithms();
        if (webAuthnPolicySignatureAlgorithms == null || webAuthnPolicySignatureAlgorithms.isEmpty())
            webAuthnPolicySignatureAlgorithms = Arrays.asList(Constants.DEFAULT_WEBAUTHN_POLICY_SIGNATURE_ALGORITHMS.split(","));
        webAuthnPolicy.setSignatureAlgorithm(webAuthnPolicySignatureAlgorithms);

        String webAuthnPolicyRpId = rep.getWebAuthnPolicyRpId();
        if (webAuthnPolicyRpId == null || webAuthnPolicyRpId.isEmpty())
            webAuthnPolicyRpId = "";
        webAuthnPolicy.setRpId(webAuthnPolicyRpId);

        String webAuthnPolicyAttestationConveyancePreference = rep.getWebAuthnPolicyAttestationConveyancePreference();
        if (webAuthnPolicyAttestationConveyancePreference == null || webAuthnPolicyAttestationConveyancePreference.isEmpty())
            webAuthnPolicyAttestationConveyancePreference = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setAttestationConveyancePreference(webAuthnPolicyAttestationConveyancePreference);

        String webAuthnPolicyAuthenticatorAttachment = rep.getWebAuthnPolicyAuthenticatorAttachment();
        if (webAuthnPolicyAuthenticatorAttachment == null || webAuthnPolicyAuthenticatorAttachment.isEmpty())
            webAuthnPolicyAuthenticatorAttachment = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setAuthenticatorAttachment(webAuthnPolicyAuthenticatorAttachment);

        String webAuthnPolicyRequireResidentKey = rep.getWebAuthnPolicyRequireResidentKey();
        if (webAuthnPolicyRequireResidentKey == null || webAuthnPolicyRequireResidentKey.isEmpty())
            webAuthnPolicyRequireResidentKey = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setRequireResidentKey(webAuthnPolicyRequireResidentKey);

        String webAuthnPolicyUserVerificationRequirement = rep.getWebAuthnPolicyUserVerificationRequirement();
        if (webAuthnPolicyUserVerificationRequirement == null || webAuthnPolicyUserVerificationRequirement.isEmpty())
            webAuthnPolicyUserVerificationRequirement = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setUserVerificationRequirement(webAuthnPolicyUserVerificationRequirement);

        Integer webAuthnPolicyCreateTimeout = rep.getWebAuthnPolicyCreateTimeout();
        if (webAuthnPolicyCreateTimeout != null) webAuthnPolicy.setCreateTimeout(webAuthnPolicyCreateTimeout);
        else webAuthnPolicy.setCreateTimeout(0);

        Boolean webAuthnPolicyAvoidSameAuthenticatorRegister = rep.isWebAuthnPolicyAvoidSameAuthenticatorRegister();
        if (webAuthnPolicyAvoidSameAuthenticatorRegister != null) webAuthnPolicy.setAvoidSameAuthenticatorRegister(webAuthnPolicyAvoidSameAuthenticatorRegister);

        List<String> webAuthnPolicyAcceptableAaguids = rep.getWebAuthnPolicyAcceptableAaguids();
        if (webAuthnPolicyAcceptableAaguids != null) webAuthnPolicy.setAcceptableAaguids(webAuthnPolicyAcceptableAaguids);

        return webAuthnPolicy;
    }


    private static WebAuthnPolicy getWebAuthnPolicyPasswordless(RealmRepresentation rep) {
        WebAuthnPolicy webAuthnPolicy = new WebAuthnPolicy();

        String webAuthnPolicyRpEntityName = rep.getWebAuthnPolicyPasswordlessRpEntityName();
        if (webAuthnPolicyRpEntityName == null || webAuthnPolicyRpEntityName.isEmpty())
            webAuthnPolicyRpEntityName = Constants.DEFAULT_WEBAUTHN_POLICY_RP_ENTITY_NAME;
        webAuthnPolicy.setRpEntityName(webAuthnPolicyRpEntityName);

        List<String> webAuthnPolicySignatureAlgorithms = rep.getWebAuthnPolicyPasswordlessSignatureAlgorithms();
        if (webAuthnPolicySignatureAlgorithms == null || webAuthnPolicySignatureAlgorithms.isEmpty())
            webAuthnPolicySignatureAlgorithms = Arrays.asList(Constants.DEFAULT_WEBAUTHN_POLICY_SIGNATURE_ALGORITHMS.split(","));
        webAuthnPolicy.setSignatureAlgorithm(webAuthnPolicySignatureAlgorithms);

        String webAuthnPolicyRpId = rep.getWebAuthnPolicyPasswordlessRpId();
        if (webAuthnPolicyRpId == null || webAuthnPolicyRpId.isEmpty())
            webAuthnPolicyRpId = "";
        webAuthnPolicy.setRpId(webAuthnPolicyRpId);

        String webAuthnPolicyAttestationConveyancePreference = rep.getWebAuthnPolicyPasswordlessAttestationConveyancePreference();
        if (webAuthnPolicyAttestationConveyancePreference == null || webAuthnPolicyAttestationConveyancePreference.isEmpty())
            webAuthnPolicyAttestationConveyancePreference = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setAttestationConveyancePreference(webAuthnPolicyAttestationConveyancePreference);

        String webAuthnPolicyAuthenticatorAttachment = rep.getWebAuthnPolicyPasswordlessAuthenticatorAttachment();
        if (webAuthnPolicyAuthenticatorAttachment == null || webAuthnPolicyAuthenticatorAttachment.isEmpty())
            webAuthnPolicyAuthenticatorAttachment = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setAuthenticatorAttachment(webAuthnPolicyAuthenticatorAttachment);

        String webAuthnPolicyRequireResidentKey = rep.getWebAuthnPolicyPasswordlessRequireResidentKey();
        if (webAuthnPolicyRequireResidentKey == null || webAuthnPolicyRequireResidentKey.isEmpty())
            webAuthnPolicyRequireResidentKey = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setRequireResidentKey(webAuthnPolicyRequireResidentKey);

        String webAuthnPolicyUserVerificationRequirement = rep.getWebAuthnPolicyPasswordlessUserVerificationRequirement();
        if (webAuthnPolicyUserVerificationRequirement == null || webAuthnPolicyUserVerificationRequirement.isEmpty())
            webAuthnPolicyUserVerificationRequirement = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        webAuthnPolicy.setUserVerificationRequirement(webAuthnPolicyUserVerificationRequirement);

        Integer webAuthnPolicyCreateTimeout = rep.getWebAuthnPolicyPasswordlessCreateTimeout();
        if (webAuthnPolicyCreateTimeout != null) webAuthnPolicy.setCreateTimeout(webAuthnPolicyCreateTimeout);
        else webAuthnPolicy.setCreateTimeout(0);

        Boolean webAuthnPolicyAvoidSameAuthenticatorRegister = rep.isWebAuthnPolicyPasswordlessAvoidSameAuthenticatorRegister();
        if (webAuthnPolicyAvoidSameAuthenticatorRegister != null) webAuthnPolicy.setAvoidSameAuthenticatorRegister(webAuthnPolicyAvoidSameAuthenticatorRegister);

        List<String> webAuthnPolicyAcceptableAaguids = rep.getWebAuthnPolicyPasswordlessAcceptableAaguids();
        if (webAuthnPolicyAcceptableAaguids != null) webAuthnPolicy.setAcceptableAaguids(webAuthnPolicyAcceptableAaguids);

        return webAuthnPolicy;
    }
    public static Map<String, String> importAuthenticationFlows(RealmModel newRealm, RealmRepresentation rep) {
        Map<String, String> mappedFlows = new HashMap<>();
        if (rep.getAuthenticationFlows() == null) {
            // assume this is an old version being imported
            DefaultAuthenticationFlows.migrateFlows(newRealm);
        } else {
            if (rep.getAuthenticatorConfig() != null) {
                for (AuthenticatorConfigRepresentation configRep : rep.getAuthenticatorConfig()) {
                    if (configRep.getAlias() == null) {
                        // this can happen only during import json files from keycloak 3.4.0 and older
                        throw new IllegalStateException("Provided realm contains authenticator config with null alias. "
                                + "It should be resolved by adding alias to the authenticator config before exporting the realm.");
                    }
                    AuthenticatorConfigModel model = RepresentationToModel.toModel(configRep);
                    newRealm.addAuthenticatorConfig(model);
                }
            }
            if (rep.getAuthenticationFlows() != null) {
                for (AuthenticationFlowRepresentation flowRep : rep.getAuthenticationFlows()) {
                    AuthenticationFlowModel model = RepresentationToModel.toModel(flowRep);
                    // make sure new id is generated for new AuthenticationFlowModel instance
                    String previousId = model.getId();
                    model.setId(null);
                    model = newRealm.addAuthenticationFlow(model);
                    // store the mapped ids so that clients can reference the correct flow when importing the authenticationFlowBindingOverrides
                    mappedFlows.put(previousId, model.getId());
                }
                for (AuthenticationFlowRepresentation flowRep : rep.getAuthenticationFlows()) {
                    AuthenticationFlowModel model = newRealm.getFlowByAlias(flowRep.getAlias());
                    for (AuthenticationExecutionExportRepresentation exeRep : flowRep.getAuthenticationExecutions()) {
                        AuthenticationExecutionModel execution = toModel(newRealm, model, exeRep);
                        newRealm.addAuthenticatorExecution(execution);
                    }
                }
            }
        }
        if (rep.getBrowserFlow() == null) {
            AuthenticationFlowModel defaultFlow = newRealm.getFlowByAlias(DefaultAuthenticationFlows.BROWSER_FLOW);
            if (defaultFlow != null) {
                newRealm.setBrowserFlow(defaultFlow);
            }
        } else {
            newRealm.setBrowserFlow(newRealm.getFlowByAlias(rep.getBrowserFlow()));
        }
        if (rep.getRegistrationFlow() == null) {
            AuthenticationFlowModel defaultFlow = newRealm.getFlowByAlias(DefaultAuthenticationFlows.REGISTRATION_FLOW);
            if (defaultFlow != null) {
                newRealm.setRegistrationFlow(defaultFlow);
            }
        } else {
            newRealm.setRegistrationFlow(newRealm.getFlowByAlias(rep.getRegistrationFlow()));
        }
        if (rep.getDirectGrantFlow() == null) {
            AuthenticationFlowModel defaultFlow = newRealm.getFlowByAlias(DefaultAuthenticationFlows.DIRECT_GRANT_FLOW);
            if (defaultFlow != null) {
                newRealm.setDirectGrantFlow(defaultFlow);
            }
        } else {
            newRealm.setDirectGrantFlow(newRealm.getFlowByAlias(rep.getDirectGrantFlow()));
        }

        // reset credentials + client flow needs to be more defensive as they were added later (in 1.5 )
        if (rep.getResetCredentialsFlow() == null) {
            AuthenticationFlowModel resetFlow = newRealm.getFlowByAlias(DefaultAuthenticationFlows.RESET_CREDENTIALS_FLOW);
            if (resetFlow == null) {
                DefaultAuthenticationFlows.resetCredentialsFlow(newRealm);
            } else {
                newRealm.setResetCredentialsFlow(resetFlow);
            }
        } else {
            newRealm.setResetCredentialsFlow(newRealm.getFlowByAlias(rep.getResetCredentialsFlow()));
        }
        if (rep.getClientAuthenticationFlow() == null) {
            AuthenticationFlowModel clientFlow = newRealm.getFlowByAlias(DefaultAuthenticationFlows.CLIENT_AUTHENTICATION_FLOW);
            if (clientFlow == null) {
                DefaultAuthenticationFlows.clientAuthFlow(newRealm);
            } else {
                newRealm.setClientAuthenticationFlow(clientFlow);
            }
        } else {
            newRealm.setClientAuthenticationFlow(newRealm.getFlowByAlias(rep.getClientAuthenticationFlow()));
        }

        // Added in 1.7
        if (newRealm.getFlowByAlias(DefaultAuthenticationFlows.FIRST_BROKER_LOGIN_FLOW) == null) {
            DefaultAuthenticationFlows.firstBrokerLoginFlow(newRealm, true);
        }

        // Added in 2.2
        String defaultProvider = null;
        if (rep.getIdentityProviders() != null) {
            for (IdentityProviderRepresentation i : rep.getIdentityProviders()) {
                if (i.isEnabled() && i.isAuthenticateByDefault()) {
                    defaultProvider = i.getProviderId();
                    break;
                }
            }
        }

        // Added in 3.2
        if (rep.getDockerAuthenticationFlow() == null) {
            AuthenticationFlowModel dockerAuthenticationFlow = newRealm.getFlowByAlias(DefaultAuthenticationFlows.DOCKER_AUTH);
            if (dockerAuthenticationFlow == null) {
                DefaultAuthenticationFlows.dockerAuthenticationFlow(newRealm);
            } else {
                newRealm.setDockerAuthenticationFlow(dockerAuthenticationFlow);
            }
        } else {
            newRealm.setDockerAuthenticationFlow(newRealm.getFlowByAlias(rep.getDockerAuthenticationFlow()));
        }

        DefaultAuthenticationFlows.addIdentityProviderAuthenticator(newRealm, defaultProvider);

        return mappedFlows;
    }

    private static AuthenticationExecutionModel toModel(RealmModel realm, AuthenticationFlowModel parentFlow, AuthenticationExecutionExportRepresentation rep) {
        AuthenticationExecutionModel model = new AuthenticationExecutionModel();
        if (rep.getAuthenticatorConfig() != null) {
            AuthenticatorConfigModel config = realm.getAuthenticatorConfigByAlias(rep.getAuthenticatorConfig());
            model.setAuthenticatorConfig(config.getId());
        }
        model.setAuthenticator(rep.getAuthenticator());
        model.setAuthenticatorFlow(rep.isAuthenticatorFlow());
        if (rep.getFlowAlias() != null) {
            AuthenticationFlowModel flow = realm.getFlowByAlias(rep.getFlowAlias());
            model.setFlowId(flow.getId());
        }
        model.setPriority(rep.getPriority());
        model.setRequirement(AuthenticationExecutionModel.Requirement.valueOf(rep.getRequirement()));
        model.setParentFlow(parentFlow.getId());
        return model;
    }

    private static void updateCibaSettings(RealmRepresentation rep, RealmModel realm) {
        Map<String, String> newAttributes = rep.getAttributesOrEmpty();
        CibaConfig cibaPolicy = realm.getCibaPolicy();

        cibaPolicy.setBackchannelTokenDeliveryMode(newAttributes.get(CibaConfig.CIBA_BACKCHANNEL_TOKEN_DELIVERY_MODE));
        cibaPolicy.setExpiresIn(newAttributes.get(CibaConfig.CIBA_EXPIRES_IN));
        cibaPolicy.setPoolingInterval(newAttributes.get(CibaConfig.CIBA_INTERVAL));
        cibaPolicy.setAuthRequestedUserHint(newAttributes.get(CibaConfig.CIBA_AUTH_REQUESTED_USER_HINT));
    }

    private static void updateParSettings(RealmRepresentation rep, RealmModel realm) {
        Map<String, String> newAttributes = rep.getAttributesOrEmpty();
        ParConfig parPolicy = realm.getParPolicy();

        parPolicy.setRequestUriLifespan(newAttributes.get(ParConfig.PAR_REQUEST_URI_LIFESPAN));
    }

    public static OTPPolicy toPolicy(RealmRepresentation rep) {
        OTPPolicy policy = new OTPPolicy();
        if (rep.getOtpPolicyType() != null) policy.setType(rep.getOtpPolicyType());
        if (rep.getOtpPolicyLookAheadWindow() != null) policy.setLookAheadWindow(rep.getOtpPolicyLookAheadWindow());
        if (rep.getOtpPolicyInitialCounter() != null) policy.setInitialCounter(rep.getOtpPolicyInitialCounter());
        if (rep.getOtpPolicyAlgorithm() != null) policy.setAlgorithm(rep.getOtpPolicyAlgorithm());
        if (rep.getOtpPolicyDigits() != null) policy.setDigits(rep.getOtpPolicyDigits());
        if (rep.getOtpPolicyPeriod() != null) policy.setPeriod(rep.getOtpPolicyPeriod());
        if (rep.isOtpPolicyCodeReusable() != null) policy.setCodeReusable(rep.isOtpPolicyCodeReusable());
        return policy;
    }


    public static RequiredActionProviderModel toModel(RequiredActionProviderRepresentation rep) {
        RequiredActionProviderModel model = new RequiredActionProviderModel();
        model.setConfig(RepresentationToModel.removeEmptyString(rep.getConfig()));
        model.setPriority(rep.getPriority());
        model.setDefaultAction(rep.isDefaultAction());
        model.setEnabled(rep.isEnabled());
        model.setProviderId(rep.getProviderId());
        model.setName(rep.getName());
        model.setAlias(rep.getAlias());
        return model;
    }


    public static void importRealmAuthorizationSettings(RealmRepresentation rep, RealmModel newRealm, KeycloakSession session) {
        if (rep.getClients() != null) {
            rep.getClients().forEach(clientRepresentation -> {
                ClientModel client = newRealm.getClientByClientId(clientRepresentation.getClientId());
                RepresentationToModel.importAuthorizationSettings(clientRepresentation, client, session);
            });
        }
    }
}
