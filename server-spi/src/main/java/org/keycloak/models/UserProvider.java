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

package org.keycloak.models;

import org.keycloak.component.ComponentModel;
import org.keycloak.provider.Provider;
import org.keycloak.storage.user.UserBulkUpdateProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public interface UserProvider extends Provider,
        UserLookupProvider,
        UserQueryProvider,
        UserRegistrationProvider,
        UserBulkUpdateProvider {

    /**
     * Sets the notBefore value for the given user
     *
     * @param realm a reference to the realm
     * @param user the user model
     * @param notBefore new value for notBefore
     *
     * @throws ModelException when user doesn't exist in the storage
     */
    void setNotBeforeForUser(RealmModel realm, UserModel user, int notBefore);

    /**
     * Gets the notBefore value for the given user
     *
     * @param realm a reference to the realm
     * @param user the user model
     * @return the value of notBefore
     *
     * @throws ModelException when user doesn't exist in the storage
     */
    int getNotBeforeOfUser(RealmModel realm, UserModel user);

    /**
     * Return a UserModel representing service account of the client
     *
     * @param client the client model
     * @throws IllegalArgumentException when there are more service accounts associated with the given clientId
     * @return userModel representing service account of the client
     */
    UserModel getServiceAccount(ClientModel client);

    /**
     * Obtains the users associated with the specified realm.
     *
     * @param realm a reference to the realm being used for the search.
     * @param includeServiceAccounts {@code true} if service accounts should be included in the result; {@code false} otherwise.
     * @return a non-null {@link Stream} of users associated withe the realm.
     *
     * @deprecated Use {@link UserQueryProvider#searchForUserStream(RealmModel, Map)} with
     * {@link UserModel#INCLUDE_SERVICE_ACCOUNT} within params instead.
     */
    @Deprecated
    default Stream<UserModel> getUsersStream(RealmModel realm, boolean includeServiceAccounts) {
        Map<String, String> searchAttributes = new HashMap<>(1);
        searchAttributes.put(UserModel.INCLUDE_SERVICE_ACCOUNT, Boolean.toString(includeServiceAccounts));
        return this.searchForUserStream(realm, searchAttributes);
    }

    /**
     * Obtains the users associated with the specified realm.
     *
     * @param realm a reference to the realm being used for the search.
     * @param firstResult first result to return. Ignored if negative, zero, or {@code null}.
     * @param maxResults maximum number of results to return. Ignored if negative or {@code null}.
     * @param includeServiceAccounts {@code true} if service accounts should be included in the result; {@code false} otherwise.
     * @return a non-null {@link Stream} of users associated withe the realm.
     * 
     * @deprecated Use {@link UserQueryProvider#searchForUserStream(RealmModel, Map, Integer, Integer)} 
     * with {@link UserModel#INCLUDE_SERVICE_ACCOUNT} within params
     */
    @Deprecated
    default Stream<UserModel> getUsersStream(RealmModel realm, Integer firstResult, Integer maxResults, boolean includeServiceAccounts) {
        Map<String, String> searchAttributes = new HashMap<>(1);
        searchAttributes.put(UserModel.INCLUDE_SERVICE_ACCOUNT, Boolean.toString(includeServiceAccounts));
        return this.searchForUserStream(realm, searchAttributes, firstResult, maxResults);
    }

    /**
     * Adds a new user into the storage.
     * <p/>
     * only used for local storage
     *
     * @param realm the realm that user will be created in
     * @param id id of the new user. Should be generated to a random value if {@code null}.
     * @param username username
     * @param addDefaultRoles if {@code true}, the user should join all realm default roles
     * @param addDefaultRequiredActions if {@code true}, all default required actions are added to the created user
     * @return model of created user
     *
     * @throws NullPointerException when username or realm is {@code null}
     * @throws ModelDuplicateException when a user with given id or username already exists
     */
    UserModel addUser(RealmModel realm, String id, String username, boolean addDefaultRoles, boolean addDefaultRequiredActions);

    /**
     * Removes any imported users from a specific User Storage Provider.
     *
     * @param realm a reference to the realm
     * @param storageProviderId id of the user storage provider
     */
    void removeImportedUsers(RealmModel realm, String storageProviderId);

    /**
     * Set federation link to {@code null} to imported users of a specific User Storage Provider
     *
     * @param realm a reference to the realm
     * @param storageProviderId id of the storage provider
     */
    void unlinkUsers(RealmModel realm, String storageProviderId);

    /* USER CONSENTS methods */

    /**
     * Add user consent for the user.
     *
     * @param realm a reference to the realm
     * @param userId id of the user
     * @param consent all details corresponding to the granted consent
     *
     * @throws ModelException If there is no user with userId
     */
    void addConsent(RealmModel realm, String userId, UserConsentModel consent);

    /**
     * Returns UserConsentModel given by a user with the userId for the client with clientInternalId
     *
     * @param realm a reference to the realm
     * @param userId id of the user
     * @param clientInternalId id of the client
     * @return consent given by the user to the client or {@code null} if no consent or user exists
     *
     * @throws ModelException when there are more consents fulfilling specified parameters
     */
    UserConsentModel getConsentByClient(RealmModel realm, String userId, String clientInternalId);

    /**
     * Obtains the consents associated with the user identified by the specified {@code userId}.
     *
     * @param realm a reference to the realm.
     * @param userId the user identifier.
     * @return a non-null {@link Stream} of consents associated with the user.
     */
    Stream<UserConsentModel> getConsentsStream(RealmModel realm, String userId);

    /**
     * Update client scopes in the stored user consent
     *
     * @param realm a reference to the realm
     * @param userId id of the user
     * @param consent new details of the user consent
     *
     * @throws ModelException when consent doesn't exist for the userId
     */
    void updateConsent(RealmModel realm, String userId, UserConsentModel consent);

    /**
     * Remove a user consent given by the user id and client id
     *
     * @param realm a reference to the realm
     * @param userId id of the user
     * @param clientInternalId id of the client
     * @return {@code true} if the consent was removed, {@code false} otherwise
     *
     * TODO: Make this method return Boolean so that store can return "I don't know" answer, this can be used for example in async stores
     */
    boolean revokeConsentForClient(RealmModel realm, String userId, String clientInternalId);

    /* FEDERATED IDENTITIES methods */

    /**
     * Adds a federated identity link for the user within the realm
     *
     * @param realm a reference to the realm
     * @param user the user model
     * @param socialLink the federated identity model containing all details of the association between the user and
     *                   the identity provider
     */
    void addFederatedIdentity(RealmModel realm, UserModel user, FederatedIdentityModel socialLink);

    /**
     * Removes federation link between the user and the identity provider given by its id
     *
     * @param realm a reference to the realm
     * @param user the user model
     * @param socialProvider alias of the identity provider, see {@link IdentityProviderModel#getAlias()}
     * @return {@code true} if the association was removed, {@code false} otherwise
     *
     * TODO: Make this method return Boolean so that store can return "I don't know" answer, this can be used for example in async stores
     */
    boolean removeFederatedIdentity(RealmModel realm, UserModel user, String socialProvider);

    /**
     * Update details of association between the federatedUser and the idp given by the federatedIdentityModel
     *
     * @param realm a reference to the realm
     * @param federatedUser the user model
     * @param federatedIdentityModel the federated identity model containing all details of the association between
     *                               the user and the identity provider
     */
    void updateFederatedIdentity(RealmModel realm, UserModel federatedUser, FederatedIdentityModel federatedIdentityModel);

    /**
     * Obtains the federated identities of the specified user.
     *
     * @param realm a reference to the realm.
     * @param user the reference to the user.
     * @return a non-null {@link Stream} of federated identities associated with the user.
     */
    Stream<FederatedIdentityModel> getFederatedIdentitiesStream(RealmModel realm, UserModel user);

    /**
     * Returns details of the association between the user and the socialProvider.
     *
     * @param realm a reference to the realm
     * @param user the user model
     * @param socialProvider the id of the identity provider
     * @return federatedIdentityModel or {@code null} if no association exists
     */
    FederatedIdentityModel getFederatedIdentity(RealmModel realm, UserModel user, String socialProvider);

    /**
     * Returns a userModel that corresponds to the given socialLink.
     *
     * @param realm a reference to the realm
     * @param socialLink the socialLink
     * @return the user corresponding to socialLink and {@code null} if no such user exists
     *
     * @throws IllegalStateException when there are more users for the given socialLink
     */
    UserModel getUserByFederatedIdentity(RealmModel realm, FederatedIdentityModel socialLink);

    /* PRE REMOVE methods - for cleaning user related properties when some other entity is removed */

    /**
     * Called when a realm is removed.
     * Should remove all users that belong to the realm.
     *
     * @param realm a reference to the realm
     */
    void preRemove(RealmModel realm);

    /**
     * Called when an identity provider is removed.
     * Should remove all federated identities assigned to users from the provider.
     *
     * @param realm a reference to the realm
     * @param provider provider model
     */
    void preRemove(RealmModel realm, IdentityProviderModel provider);

    /**
     * Called when a role is removed.
     * Should remove the role membership for each user.
     *
     * @param realm a reference to the realm
     * @param role the role model
     */
    void preRemove(RealmModel realm, RoleModel role);

    /**
     * Called when a group is removed.
     * Should remove the group membership for each user.
     *
     * @param realm a reference to the realm
     * @param group the group model
     */
    void preRemove(RealmModel realm, GroupModel group);

    /**
     * Called when a client is removed.
     * Should remove all user consents associated with the client
     *
     * @param realm a reference to the realm
     * @param client the client model
     */
    void preRemove(RealmModel realm, ClientModel client);

    /**
     * Called when a protocolMapper is removed
     *
     * @param protocolMapper the protocolMapper model
     */
    void preRemove(ProtocolMapperModel protocolMapper);

    /**
     * Called when a client scope is removed.
     * Should remove the clientScope from each user consent
     *
     * @param clientScope the clientScope model
     */
    void preRemove(ClientScopeModel clientScope);

    /**
     * Called when a component is removed.
     * Should remove all data in UserStorage associated with removed component.
     * For example,
     * <ul>
     *     <li>if component corresponds to UserStorageProvider all imported users from the provider should be removed,</li>
     *     <li>if component corresponds to ClientStorageProvider all consents granted for clients imported from the
     *     provider should be removed</li>
     * </ul>
     *
     * @param realm a reference to the realm
     * @param component the component model
     */
    void preRemove(RealmModel realm, ComponentModel component);

    void close();

    /**
     * @deprecated This interface is no longer necessary, collection-based methods were removed from the parent interface
     * and therefore the parent interface can be used directly
     */
    @Deprecated
    interface Streams extends UserProvider, UserQueryProvider, UserLookupProvider {
    }
}
