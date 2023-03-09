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
package org.keycloak.testsuite.broker;

import java.util.Collections;
import javax.ws.rs.core.Response;
import org.jboss.arquillian.graphene.page.Page;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.common.Profile;
import org.keycloak.common.Profile.Feature;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.Assert;
import org.keycloak.testsuite.ProfileAssume;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.arquillian.annotation.DisableFeature;
import org.keycloak.testsuite.federation.PassThroughFederatedUserStorageProvider;
import org.keycloak.testsuite.federation.PassThroughFederatedUserStorageProviderFactory;
import org.keycloak.testsuite.federation.UserMapStorageFactory;
import org.keycloak.testsuite.pages.AccountFederatedIdentityPage;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.pages.UpdateAccountInformationPage;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.keycloak.storage.UserStorageProviderModel.IMPORT_ENABLED;
import static org.keycloak.testsuite.admin.ApiUtil.createUserAndResetPasswordWithAdminClient;
import static org.keycloak.testsuite.admin.ApiUtil.createUserWithAdminClient;

import org.keycloak.testsuite.runonserver.RunOnServer;
import org.keycloak.testsuite.util.FederatedIdentityBuilder;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@DisableFeature(value = Profile.Feature.ACCOUNT2, skipRestart = true) // TODO remove this (KEYCLOAK-16228)
public class AccountLinkTest extends AbstractKeycloakTest {
    public static final String CHILD_IDP = "child";
    public static final String PARENT_IDP = "parent-idp";
    public static final String PARENT_USERNAME = "parent";

    @Page
    protected AccountFederatedIdentityPage accountFederatedIdentityPage;

    @Page
    protected UpdateAccountInformationPage profilePage;

    @Page
    protected LoginPage loginPage;

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(CHILD_IDP);
        realm.setEnabled(true);
        testRealms.add(realm);

        realm = new RealmRepresentation();
        realm.setRealm(PARENT_IDP);
        realm.setEnabled(true);

        testRealms.add(realm);

    }

    @Before
    public void beforeBrokerTest() {
        if (testContext.isInitialized()) {
            return;
        }

        // addIdpUser
        RealmResource realmParent = adminClient.realms().realm(PARENT_IDP);
        UserRepresentation user = new UserRepresentation();
        user.setUsername(PARENT_USERNAME);
        user.setEnabled(true);
        String userId = createUserAndResetPasswordWithAdminClient(realmParent, user, "password");

        // addChildUser
        RealmResource realmChild = adminClient.realms().realm(CHILD_IDP);
        user = new UserRepresentation();
        user.setUsername("child");
        user.setEnabled(true);
        userId = createUserAndResetPasswordWithAdminClient(realmChild, user, "password");

        // setupUserStorageProvider
        ComponentRepresentation provider = new ComponentRepresentation();
        provider.setName("passthrough");
        provider.setProviderId(PassThroughFederatedUserStorageProviderFactory.PROVIDER_ID);
        provider.setProviderType(UserStorageProvider.class.getName());
        provider.setConfig(new MultivaluedHashMap<>());
        provider.getConfig().putSingle("priority", Integer.toString(1));
        realmChild.components().add(provider);

        // createBroker
        createParentChild();

        testContext.setInitialized(true);
    }


    public void createParentChild() {
        BrokerTestTools.createKcOidcBroker(adminClient, CHILD_IDP, PARENT_IDP);
    }

    @Test
    public void testAccountLink() {
        String childUsername = "child";
        String childPassword = "password";
        String childIdp = CHILD_IDP;

        testAccountLink(childUsername, childPassword, childIdp);
    }

    @Test
    public void testAccountLinkWithUserStorageProvider() {
        ProfileAssume.assumeFeatureDisabled(Feature.MAP_STORAGE);

        String childUsername = PassThroughFederatedUserStorageProvider.PASSTHROUGH_USERNAME;
        String childPassword = PassThroughFederatedUserStorageProvider.INITIAL_PASSWORD;
        String childIdp = CHILD_IDP;

        testAccountLink(childUsername, childPassword, childIdp);

    }

    @Test
    public void testDeleteIdentityOnProviderRemoval() {
        String childUsername = "child";
        String childPassword = "password";
        String childIdp = CHILD_IDP;

        assertFederatedIdentity(childUsername, childPassword, childIdp);

        RealmResource realm = adminClient.realm(CHILD_IDP);
        UsersResource users = realm.users();
        List<UserRepresentation> search = users.search(childUsername);
        assertFalse(search.isEmpty());
        String userId = search.get(0).getId();
        List<FederatedIdentityRepresentation> identities = users.get(userId).getFederatedIdentity();
        assertFalse(identities.isEmpty());

        realm.identityProviders().get(PARENT_IDP).remove();
        
        identities = users.get(userId).getFederatedIdentity();
        assertTrue(identities.isEmpty());

        getTestingClient().server(CHILD_IDP).run(AccountLinkTest::checkEmptyFederatedIdentities);
    }

    @Test
    public void testDeleteFederatedUserFederatedIdentityOnProviderRemoval() {
        RealmResource realm = adminClient.realm(CHILD_IDP);
        final String testIdpToDelete = "test-idp-to-delete";

        BrokerTestTools.createKcOidcBroker(adminClient, CHILD_IDP, testIdpToDelete);

        // Create user federation
        Assume.assumeTrue("User cache disabled.", isUserCacheEnabled());

        ComponentRepresentation memProvider = new ComponentRepresentation();
        memProvider.setName("memory");
        memProvider.setProviderId(UserMapStorageFactory.PROVIDER_ID);
        memProvider.setProviderType(UserStorageProvider.class.getName());
        memProvider.setConfig(new MultivaluedHashMap<>());
        memProvider.getConfig().putSingle("priority", Integer.toString(0));
        memProvider.getConfig().putSingle(IMPORT_ENABLED, Boolean.toString(false));

        Response resp = realm.components().add(memProvider);
        resp.close();
        String memProviderId = ApiUtil.getCreatedId(resp);

        // Create federated user
        String username = "fed-user1";
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(username);
        userRepresentation.setEmail("feduser1@mail.com");
        userRepresentation.setRequiredActions(Collections.emptyList());
        userRepresentation.setEnabled(true);
        userRepresentation.setFederationLink(memProviderId);
        String userId = createUserWithAdminClient(realm, userRepresentation);
        Assert.assertFalse(StorageId.isLocalStorage(userId));

        // Link identity provider and federated user
        FederatedIdentityRepresentation identity = FederatedIdentityBuilder.create()
            .userId(userId)
            .userName(username)
            .identityProvider(testIdpToDelete)
            .build();

        UserResource userResource = realm.users().get(userId);
        Response response = userResource.addFederatedIdentity(testIdpToDelete, identity);
        Assert.assertEquals("status", 204, response.getStatus());

        userResource = realm.users().get(userId);
        Assert.assertFalse(userResource.getFederatedIdentity().isEmpty());

        // Delete the identity provider
        realm.identityProviders().get(testIdpToDelete).remove();

        // Check that links to federated identity has been deleted
        userResource = realm.users().get(userId);
        Assert.assertTrue(userResource.getFederatedIdentity().isEmpty());

        getTestingClient().server(CHILD_IDP).run((RunOnServer) session -> {
            RealmModel realm1 = session.getContext().getRealm();
            UserModel user = session.users().getUserByUsername(realm1, username);
            assertEquals(0, session.users().getFederatedIdentitiesStream(realm1, user).count());
            assertNull(session.users().getFederatedIdentity(realm1, user, testIdpToDelete));
        });
    }
    
    private static void checkEmptyFederatedIdentities(KeycloakSession session) {
        RealmModel realm = session.getContext().getRealm();
        UserModel user = session.users().getUserByUsername(realm, "child");
        assertEquals(0, session.users().getFederatedIdentitiesStream(realm, user).count());
        assertNull(session.users().getFederatedIdentity(realm, user, PARENT_IDP));
    }

    protected void testAccountLink(String childUsername, String childPassword, String childIdp) {
        assertFederatedIdentity(childUsername, childPassword, childIdp);
        assertRemoveFederatedIdentity();

    }

    private void assertFederatedIdentity(String childUsername, String childPassword, String childIdp) {
        accountFederatedIdentityPage.realm(childIdp);
        accountFederatedIdentityPage.open();
        loginPage.isCurrent();
        loginPage.login(childUsername, childPassword);
        assertTrue(accountFederatedIdentityPage.isCurrent());

        accountFederatedIdentityPage.clickAddProvider(PARENT_IDP);

        this.loginPage.isCurrent();
        loginPage.login(PARENT_USERNAME, "password");

        // Assert identity linked in account management
        assertTrue(accountFederatedIdentityPage.isCurrent());
        assertTrue(driver.getPageSource().contains("id=\"remove-link-" + PARENT_IDP + "\""));

        // Logout from account management
        accountFederatedIdentityPage.logout();

        // Assert I am logged immediately to account management due to previously linked "test-user" identity
        loginPage.isCurrent();
        loginPage.clickSocial(PARENT_IDP);
        loginPage.login(PARENT_USERNAME, "password");
        System.out.println(driver.getCurrentUrl());
        System.out.println("--------------------------------");
        System.out.println(driver.getPageSource());
        assertTrue(accountFederatedIdentityPage.isCurrent());
        assertTrue(driver.getPageSource().contains("id=\"remove-link-" + PARENT_IDP + "\""));
    }

    private void assertRemoveFederatedIdentity() {
        // Unlink my "test-user"
        accountFederatedIdentityPage.clickRemoveProvider(PARENT_IDP);
        assertTrue(driver.getPageSource().contains("id=\"add-link-" + PARENT_IDP + "\""));

        // Logout from account management
        accountFederatedIdentityPage.logout();

        this.loginPage.clickSocial(PARENT_IDP);
        this.loginPage.login(PARENT_USERNAME, "password");
        this.profilePage.assertCurrent();
    }


}
