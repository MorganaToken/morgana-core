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

import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.UserCredentialStore;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manage the credentials for a user.
 *
 * @deprecated Instead of this class, use {@link UserModel#credentialManager()} instead.
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@Deprecated
public interface UserCredentialManager extends UserCredentialStore {

    /**
     * Validates list of credentials.  Will call UserStorageProvider and UserFederationProviders first, then loop through
     * each CredentialProvider.
     *
     * @param realm
     * @param user
     * @param inputs
     * @return
     */
    boolean isValid(RealmModel realm, UserModel user, List<CredentialInput> inputs);

    /**
     * Validates list of credentials.  Will call UserStorageProvider and UserFederationProviders first, then loop through
     * each CredentialProvider.
     *
     * @param realm
     * @param user
     * @param inputs
     * @return
     */
    boolean isValid(RealmModel realm, UserModel user, CredentialInput... inputs);

    /**
     * Updates a credential.  Will call UserStorageProvider and UserFederationProviders first, then loop through
     * each CredentialProvider.  Update is finished whenever any one provider returns true.
     *
     * @param realm
     * @param user
     * @return true if credential was successfully updated by UserStorage or any CredentialInputUpdater
     */
    boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input);

    /**
     * Creates a credential from the credentialModel, by looping through the providers to find a match for the type
     * @param realm
     * @param user
     * @param model
     * @return
     */
    CredentialModel createCredentialThroughProvider(RealmModel realm, UserModel user, CredentialModel model);

    /**
     * Updates the credential label and invalidates the cache for the user.
     * @param realm
     * @param user
     * @param credentialId
     * @param userLabel
     */
    void updateCredentialLabel(RealmModel realm, UserModel user, String credentialId, String userLabel);

    /**
     * Calls disableCredential on UserStorageProvider and UserFederationProviders first, then loop through
     * each CredentialProvider.
     *
     * @param realm
     * @param user
     * @param credentialType
     */
    void disableCredentialType(RealmModel realm, UserModel user, String credentialType);

    /**
     * Obtains the credential types that can be disabled.
     * method.
     *
     * @param realm a reference to the realm.
     * @param user the user whose credentials are being searched.
     * @return a non-null {@link Stream} of credential types.
     *
     * @deprecated Use {@link UserModel#credentialManager()} and then call {@link SubjectCredentialManager#getDisableableCredentialTypesStream()}
     */
    default Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user) {
        return user.credentialManager().getDisableableCredentialTypesStream();
    }

    /**
     * Checks to see if user has credential type configured.  Looks in UserStorageProvider or UserFederationProvider first,
     * then loops through each CredentialProvider.
     *
     * @param realm
     * @param user
     * @param type
     * @return
     */
    boolean isConfiguredFor(RealmModel realm, UserModel user, String type);

    /**
     * Only loops through each CredentialProvider to see if credential type is configured for the user.
     * This allows UserStorageProvider and UserFederationProvider isValid() implementations to punt to local storage
     * when validating a credential that has been overriden in Keycloak storage.
     *
     * @param realm
     * @param user
     * @param type
     * @return
     */
    boolean isConfiguredLocally(RealmModel realm, UserModel user, String type);

    /**
     * Given a CredentialInput, authenticate the user.  This is used in the case where the credential must be processed
     * to determine and find the user.  An example is Kerberos where the kerberos token might be validated and processed
     * by a variety of different storage providers.
     *
     *
     * @param session
     * @param realm
     * @param input
     * @return
     */
    CredentialValidationOutput authenticate(KeycloakSession session, RealmModel realm, CredentialInput input);

    /**
     * Obtains the credential types provided by the user storage where the specified user is stored. Examples of returned
     * values are "password", "otp", etc.
     * <p/>
     * This method will always return an empty stream for "local" users - i.e. users that are not backed by any user storage.
     *
     * @param realm a reference to the realm.
     * @param user a reference to the user.
     * @return a non-null {@link Stream} of credential types.
     *
     * @deprecated Use {@link UserModel#credentialManager()} and then call {@link SubjectCredentialManager#getConfiguredUserStorageCredentialTypesStream()}
     */
    default Stream<String> getConfiguredUserStorageCredentialTypesStream(RealmModel realm, UserModel user) {
        return user.credentialManager().getConfiguredUserStorageCredentialTypesStream();
    }

    /**
     * @deprecated This interface is no longer necessary, collection-based methods were removed from the parent interface
     * and therefore the parent interface can be used directly
     */
    @Deprecated
    interface Streams extends UserCredentialManager, UserCredentialStore {
    }
}
