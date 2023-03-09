/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.admin;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthorizationResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.common.Profile;
import org.keycloak.models.Constants;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.ManagementPermissionRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.DecisionStrategy;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ScopePermissionRepresentation;
import org.keycloak.representations.idm.authorization.UserPolicyRepresentation;
import org.keycloak.testsuite.arquillian.annotation.EnableFeature;
import org.keycloak.testsuite.updaters.RealmAttributeUpdater;
import org.keycloak.testsuite.util.AdminClientUtil;
import org.keycloak.testsuite.util.RealmBuilder;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class UsersTest extends AbstractAdminTest {

    @Before
    public void cleanUsers() {
        List<UserRepresentation> userRepresentations = realm.users().list();
        for (UserRepresentation user : userRepresentations) {
            realm.users().delete(user.getId());
        }
    }

    @Test
    public void searchUserDefaultSettings() throws Exception {
        createUser(REALM_NAME, "User", "password", "firstName", "lastName", "user@example.com");

        assertCaseInsensitiveSearch();
    }

    @Test
    public void searchUserCaseSensitiveFirst() throws Exception {
        Assume.assumeFalse(isJpaRealmProvider());
        Map<String, String> attributes = new HashMap<>();
        attributes.put(Constants.REALM_ATTR_USERNAME_CASE_SENSITIVE, "true");
        try (AutoCloseable c = new RealmAttributeUpdater(adminClient.realm(REALM_NAME))
                .updateWith(r -> r.setAttributes(attributes))
                .update()) {

            createUser(REALM_NAME, "User", "password", "firstName", "lastName", "user@example.com");

            assertCaseSensitiveSearch();

            RealmRepresentation realmRep = adminClient.realm(REALM_NAME).toRepresentation();
            RealmBuilder.edit(realmRep)
                    .attribute(Constants.REALM_ATTR_USERNAME_CASE_SENSITIVE, "false");
            realm.update(realmRep);

            assertCaseInsensitiveSearch();
        }
    }

    @Test
    public void searchUserCaseInSensitiveFirst() throws Exception {
        Assume.assumeFalse(isJpaRealmProvider());
        Map<String, String> attributes = new HashMap<>();
        attributes.put(Constants.REALM_ATTR_USERNAME_CASE_SENSITIVE, "false");
        try (AutoCloseable c = new RealmAttributeUpdater(adminClient.realm(REALM_NAME))
                .updateWith(r -> r.setAttributes(attributes))
                .update()) {

            createUser(REALM_NAME, "User", "password", "firstName", "lastName", "user@example.com");

            assertCaseInsensitiveSearch();

            RealmRepresentation realmRep = adminClient.realm(REALM_NAME).toRepresentation();
            RealmBuilder.edit(realmRep)
                    .attribute(Constants.REALM_ATTR_USERNAME_CASE_SENSITIVE, "true");
            realm.update(realmRep);

            assertCaseSensitiveSearch();
        }
    }

    /**
     * https://issues.redhat.com/browse/KEYCLOAK-15146
     */
    @Test
    public void findUsersByEmailVerifiedStatus() {

        createUser(REALM_NAME, "user1", "password", "user1FirstName", "user1LastName", "user1@example.com", rep -> rep.setEmailVerified(true));
        createUser(REALM_NAME, "user2", "password", "user2FirstName", "user2LastName", "user2@example.com", rep -> rep.setEmailVerified(false));

        boolean emailVerified;
        emailVerified = true;
        List<UserRepresentation> usersEmailVerified = realm.users().search(null, null, null, null, emailVerified, null, null, null, true);
        assertThat(usersEmailVerified, is(not(empty())));
        assertThat(usersEmailVerified.get(0).getUsername(), is("user1"));

        emailVerified = false;
        List<UserRepresentation> usersEmailNotVerified = realm.users().search(null, null, null, null, emailVerified, null, null, null, true);
        assertThat(usersEmailNotVerified, is(not(empty())));
        assertThat(usersEmailNotVerified.get(0).getUsername(), is("user2"));
    }

    /**
     * https://issues.redhat.com/browse/KEYCLOAK-15146
     */
    @Test
    public void countUsersByEmailVerifiedStatus() {

        createUser(REALM_NAME, "user1", "password", "user1FirstName", "user1LastName", "user1@example.com", rep -> rep.setEmailVerified(true));
        createUser(REALM_NAME, "user2", "password", "user2FirstName", "user2LastName", "user2@example.com", rep -> rep.setEmailVerified(false));
        createUser(REALM_NAME, "user3", "password", "user3FirstName", "user3LastName", "user3@example.com", rep -> rep.setEmailVerified(true));

        boolean emailVerified;
        emailVerified = true;
        assertThat(realm.users().countEmailVerified(emailVerified), is(2));
        assertThat(realm.users().count(null,null,null,emailVerified,null), is(2));

        emailVerified = false;
        assertThat(realm.users().countEmailVerified(emailVerified), is(1));
        assertThat(realm.users().count(null,null,null,emailVerified,null), is(1));
    }

    @Test
    public void countUsersWithViewPermission() {
        createUser(REALM_NAME, "user1", "password", "user1FirstName", "user1LastName", "user1@example.com");
        createUser(REALM_NAME, "user2", "password", "user2FirstName", "user2LastName", "user2@example.com");
        assertThat(realm.users().count(), is(2));
    }

    @Test
    public void countUsersBySearchWithViewPermission() {
        createUser(REALM_NAME, "user1", "password", "user1FirstName", "user1LastName", "user1@example.com", rep -> rep.setEmailVerified(true));
        createUser(REALM_NAME, "user2", "password", "user2FirstName", "user2LastName", "user2@example.com", rep -> rep.setEmailVerified(false));
        createUser(REALM_NAME, "user3", "password", "user3FirstName", "user3LastName", "user3@example.com", rep -> rep.setEmailVerified(true));

        // Prefix search count
        Integer count = realm.users().count("user");
        assertThat(count, is(3));

        count = realm.users().count("user*");
        assertThat(count, is(3));

        count = realm.users().count("er");
        assertThat(count, is(0));

        count = realm.users().count("");
        assertThat(count, is(3));

        count = realm.users().count("*");
        assertThat(count, is(3));

        count = realm.users().count("user2FirstName");
        assertThat(count, is(1));

        count = realm.users().count("user2First");
        assertThat(count, is(1));

        count = realm.users().count("user2First*");
        assertThat(count, is(1));

        count = realm.users().count("user1@example");
        assertThat(count, is(1));

        count = realm.users().count("user1@example*");
        assertThat(count, is(1));

        count = realm.users().count(null);
        assertThat(count, is(3));

        // Infix search count
        count = realm.users().count("*user*");
        assertThat(count, is(3));

        count = realm.users().count("**");
        assertThat(count, is(3));

        count = realm.users().count("*foobar*");
        assertThat(count, is(0));

        count = realm.users().count("*LastName*");
        assertThat(count, is(3));

        count = realm.users().count("*FirstName*");
        assertThat(count, is(3));

        count = realm.users().count("*@example.com*");
        assertThat(count, is(3));

        // Exact search count
        count = realm.users().count("\"user1\"");
        assertThat(count, is(1));

        count = realm.users().count("\"1\"");
        assertThat(count, is(0));

        count = realm.users().count("\"\"");
        assertThat(count, is(0));

        count = realm.users().count("\"user1FirstName\"");
        assertThat(count, is(1));

        count = realm.users().count("\"user1LastName\"");
        assertThat(count, is(1));

        count = realm.users().count("\"user1@example.com\"");
        assertThat(count, is(1));
    }

    @Test
    public void countUsersByFiltersWithViewPermission() {
        createUser(REALM_NAME, "user1", "password", "user1FirstName", "user1LastName", "user1@example.com");
        createUser(REALM_NAME, "user2", "password", "user2FirstName", "user2LastName", "user2@example.com");
        //search username
        assertThat(realm.users().count(null, null, null, "user"), is(2));
        assertThat(realm.users().count(null, null, null, "user1"), is(1));
        assertThat(realm.users().count(null, null, null, "notExisting"), is(0));
        assertThat(realm.users().count(null, null, null, ""), is(2));
        //search first name
        assertThat(realm.users().count(null, "FirstName", null, null), is(2));
        assertThat(realm.users().count(null, "user2FirstName", null, null), is(1));
        assertThat(realm.users().count(null, "notExisting", null, null), is(0));
        assertThat(realm.users().count(null, "", null, null), is(2));
        //search last name
        assertThat(realm.users().count("LastName", null, null, null), is(2));
        assertThat(realm.users().count("user2LastName", null, null, null), is(1));
        assertThat(realm.users().count("notExisting", null, null, null), is(0));
        assertThat(realm.users().count("", null, null, null), is(2));
        //search in email
        assertThat(realm.users().count(null, null, "@example.com", null), is(2));
        assertThat(realm.users().count(null, null, "user1@example.com", null), is(1));
        assertThat(realm.users().count(null, null, "user1@test.com", null), is(0));
        assertThat(realm.users().count(null, null, "", null), is(2));
        //search for combinations
        assertThat(realm.users().count("LastName", "FirstName", null, null), is(2));
        assertThat(realm.users().count("user1LastName", "FirstName", null, null), is(1));
        assertThat(realm.users().count("user1LastName", "", null, null), is(1));
        assertThat(realm.users().count("LastName", "", null, null), is(2));
        assertThat(realm.users().count("LastName", "", null, null), is(2));
        assertThat(realm.users().count(null, null, "@example.com", "user"), is(2));
        //search not specified (defaults to simply /count)
        assertThat(realm.users().count(null, null, null, null), is(2));
        assertThat(realm.users().count("", "", "", ""), is(2));
    }

    @Test
    @EnableFeature(value = Profile.Feature.ADMIN_FINE_GRAINED_AUTHZ, skipRestart = true)
    public void countUsersWithGroupViewPermission() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        RealmResource testRealmResource = setupTestEnvironmentWithPermissions(true);
        assertThat(testRealmResource.users().count(), is(3));
    }

    @Test
    @EnableFeature(value = Profile.Feature.ADMIN_FINE_GRAINED_AUTHZ, skipRestart = true)
    public void countUsersBySearchWithGroupViewPermission() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        RealmResource testRealmResource = setupTestEnvironmentWithPermissions(true);
        //search all
        assertThat(testRealmResource.users().count("user"), is(3));
        //search first name
        assertThat(testRealmResource.users().count("*FirstName*"), is(3));
        assertThat(testRealmResource.users().count("user2FirstName"), is(1));
        //search last name
        assertThat(testRealmResource.users().count("*LastName*"), is(3));
        assertThat(testRealmResource.users().count("user2LastName"), is(1));
        //search in email
        assertThat(testRealmResource.users().count("*@example.com*"), is(3));
        assertThat(testRealmResource.users().count("user1@example.com"), is(1));
        //search for something not existing
        assertThat(testRealmResource.users().count("notExisting"), is(0));
        //search for empty string
        assertThat(testRealmResource.users().count(""), is(3));
        //search not specified (defaults to simply /count)
        assertThat(testRealmResource.users().count(null), is(3));
    }

    @Test
    @EnableFeature(value = Profile.Feature.ADMIN_FINE_GRAINED_AUTHZ, skipRestart = true)
    public void countUsersByFiltersWithGroupViewPermission() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        RealmResource testRealmResource = setupTestEnvironmentWithPermissions(true);
        //search username
        assertThat(testRealmResource.users().count(null, null, null, "user"), is(3));
        assertThat(testRealmResource.users().count(null, null, null, "user1"), is(1));
        assertThat(testRealmResource.users().count(null, null, null, "notExisting"), is(0));
        assertThat(testRealmResource.users().count(null, null, null, ""), is(3));
        //search first name
        assertThat(testRealmResource.users().count(null, "FirstName", null, null), is(3));
        assertThat(testRealmResource.users().count(null, "user2FirstName", null, null), is(1));
        assertThat(testRealmResource.users().count(null, "notExisting", null, null), is(0));
        assertThat(testRealmResource.users().count(null, "", null, null), is(3));
        //search last name
        assertThat(testRealmResource.users().count("LastName", null, null, null), is(3));
        assertThat(testRealmResource.users().count("user2LastName", null, null, null), is(1));
        assertThat(testRealmResource.users().count("notExisting", null, null, null), is(0));
        assertThat(testRealmResource.users().count("", null, null, null), is(3));
        //search in email
        assertThat(testRealmResource.users().count(null, null, "@example.com", null), is(3));
        assertThat(testRealmResource.users().count(null, null, "user1@example.com", null), is(1));
        assertThat(testRealmResource.users().count(null, null, "user1@test.com", null), is(0));
        assertThat(testRealmResource.users().count(null, null, "", null), is(3));
        //search for combinations
        assertThat(testRealmResource.users().count("LastName", "FirstName", null, null), is(3));
        assertThat(testRealmResource.users().count("user1LastName", "FirstName", null, null), is(1));
        assertThat(testRealmResource.users().count("user1LastName", "", null, null), is(1));
        assertThat(testRealmResource.users().count("LastName", "", null, null), is(3));
        assertThat(testRealmResource.users().count("LastName", "", null, null), is(3));
        assertThat(testRealmResource.users().count(null, null, "@example.com", "user"), is(3));
        //search not specified (defaults to simply /count)
        assertThat(testRealmResource.users().count(null, null, null, null), is(3));
        assertThat(testRealmResource.users().count("", "", "", ""), is(3));
    }

    @Test
    @EnableFeature(value = Profile.Feature.ADMIN_FINE_GRAINED_AUTHZ, skipRestart = true)
    public void countUsersWithNoViewPermission() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException {
        RealmResource testRealmResource = setupTestEnvironmentWithPermissions(false);
        assertThat(testRealmResource.users().count(), is(0));
    }

    @Test
    @EnableFeature(value = Profile.Feature.ADMIN_FINE_GRAINED_AUTHZ, skipRestart = true)
    public void countUsersBySearchWithNoViewPermission() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        RealmResource testRealmResource = setupTestEnvironmentWithPermissions(false);
        //search all
        assertThat(testRealmResource.users().count("user"), is(0));
        //search first name
        assertThat(testRealmResource.users().count("FirstName"), is(0));
        assertThat(testRealmResource.users().count("user2FirstName"), is(0));
        //search last name
        assertThat(testRealmResource.users().count("LastName"), is(0));
        assertThat(testRealmResource.users().count("user2LastName"), is(0));
        //search in email
        assertThat(testRealmResource.users().count("@example.com"), is(0));
        assertThat(testRealmResource.users().count("user1@example.com"), is(0));
        //search for something not existing
        assertThat(testRealmResource.users().count("notExisting"), is(0));
        //search for empty string
        assertThat(testRealmResource.users().count(""), is(0));
        //search not specified (defaults to simply /count)
        assertThat(testRealmResource.users().count(null), is(0));
    }

    @Test
    @EnableFeature(value = Profile.Feature.ADMIN_FINE_GRAINED_AUTHZ, skipRestart = true)
    public void countUsersByFiltersWithNoViewPermission() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        RealmResource testRealmResource = setupTestEnvironmentWithPermissions(false);
        //search username
        assertThat(testRealmResource.users().count(null, null, null, "user"), is(0));
        assertThat(testRealmResource.users().count(null, null, null, "user1"), is(0));
        assertThat(testRealmResource.users().count(null, null, null, "notExisting"), is(0));
        assertThat(testRealmResource.users().count(null, null, null, ""), is(0));
        //search first name
        assertThat(testRealmResource.users().count(null, "FirstName", null, null), is(0));
        assertThat(testRealmResource.users().count(null, "user2FirstName", null, null), is(0));
        assertThat(testRealmResource.users().count(null, "notExisting", null, null), is(0));
        assertThat(testRealmResource.users().count(null, "", null, null), is(0));
        //search last name
        assertThat(testRealmResource.users().count("LastName", null, null, null), is(0));
        assertThat(testRealmResource.users().count("user2LastName", null, null, null), is(0));
        assertThat(testRealmResource.users().count("notExisting", null, null, null), is(0));
        assertThat(testRealmResource.users().count("", null, null, null), is(0));
        //search in email
        assertThat(testRealmResource.users().count(null, null, "@example.com", null), is(0));
        assertThat(testRealmResource.users().count(null, null, "user1@example.com", null), is(0));
        assertThat(testRealmResource.users().count(null, null, "user1@test.com", null), is(0));
        assertThat(testRealmResource.users().count(null, null, "", null), is(0));
        //search for combinations
        assertThat(testRealmResource.users().count("LastName", "FirstName", null, null), is(0));
        assertThat(testRealmResource.users().count("user1LastName", "FirstName", null, null), is(0));
        assertThat(testRealmResource.users().count("user1LastName", "", null, null), is(0));
        assertThat(testRealmResource.users().count("LastName", "", null, null), is(0));
        assertThat(testRealmResource.users().count("LastName", "", null, null), is(0));
        assertThat(testRealmResource.users().count(null, null, "@example.com", "user"), is(0));
        //search not specified (defaults to simply /count)
        assertThat(testRealmResource.users().count(null, null, null, null), is(0));
        assertThat(testRealmResource.users().count("", "", "", ""), is(0));
    }

    private RealmResource setupTestEnvironmentWithPermissions(boolean grp1ViewPermissions) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        String testUserId = createUser(REALM_NAME, "test-user", "password", "", "", "");
        //assign 'query-users' role to test user
        ClientRepresentation clientRepresentation = realm.clients().findByClientId("realm-management").get(0);
        String realmManagementId = clientRepresentation.getId();
        RoleRepresentation roleRepresentation = realm.clients().get(realmManagementId).roles().get("query-users").toRepresentation();
        realm.users().get(testUserId).roles().clientLevel(realmManagementId).add(Collections.singletonList(roleRepresentation));

        //create test users and groups
        List<GroupRepresentation> groups = setupUsersInGroupsWithPermissions();

        if (grp1ViewPermissions) {
            AuthorizationResource authorizationResource = realm.clients().get(realmManagementId).authorization();
            //create a user policy for the test user
            UserPolicyRepresentation policy = new UserPolicyRepresentation();
            String policyName = "test-policy";
            policy.setName(policyName);
            policy.setUsers(Collections.singleton(testUserId));
            authorizationResource.policies().user().create(policy).close();
            PolicyRepresentation policyRepresentation = authorizationResource.policies().findByName(policyName);
            //add the policy to grp1
            Optional<GroupRepresentation> optional = groups.stream().filter(g -> g.getName().equals("grp1")).findFirst();
            assertThat(optional.isPresent(), is(true));
            GroupRepresentation grp1 = optional.get();
            ScopePermissionRepresentation scopePermissionRepresentation = authorizationResource.permissions().scope().findByName("view.members.permission.group." + grp1.getId());
            scopePermissionRepresentation.setPolicies(Collections.singleton(policyRepresentation.getId()));
            scopePermissionRepresentation.setDecisionStrategy(DecisionStrategy.UNANIMOUS);
            authorizationResource.permissions().scope().findById(scopePermissionRepresentation.getId()).update(scopePermissionRepresentation);
        }

        Keycloak testUserClient = AdminClientUtil.createAdminClient(true, realm.toRepresentation().getRealm(), "test-user", "password", "admin-cli", "");

        return testUserClient.realm(realm.toRepresentation().getRealm());
    }

    private List<GroupRepresentation> setupUsersInGroupsWithPermissions() {
        //create two groups
        GroupRepresentation grp1 = createGroupWithPermissions("grp1");
        GroupRepresentation grp2 = createGroupWithPermissions("grp2");
        //create test users
        String user1Id = createUser(REALM_NAME, "user1", "password", "user1FirstName", "user1LastName", "user1@example.com");
        String user2Id = createUser(REALM_NAME, "user2", "password", "user2FirstName", "user2LastName", "user2@example.com");
        String user3Id = createUser(REALM_NAME, "user3", "password", "user3FirstName", "user3LastName", "user3@example.com");
        String user4Id = createUser(REALM_NAME, "user4", "password", "user4FirstName", "user4LastName", "user4@example.com");
        //add users to groups
        realm.users().get(user1Id).joinGroup(grp1.getId());
        realm.users().get(user2Id).joinGroup(grp1.getId());
        realm.users().get(user3Id).joinGroup(grp1.getId());
        realm.users().get(user4Id).joinGroup(grp2.getId());

        List<GroupRepresentation> groups = new ArrayList<>();
        groups.add(grp1);
        groups.add(grp2);

        return groups;
    }

    private GroupRepresentation createGroupWithPermissions(String name) {
        GroupRepresentation grp = new GroupRepresentation();
        grp.setName(name);
        realm.groups().add(grp);
        Optional<GroupRepresentation> optional = realm.groups().groups().stream().filter(g -> g.getName().equals(name)).findFirst();
        assertThat(optional.isPresent(), is(true));
        grp = optional.get();
        String id = grp.getId();
        //enable the permissions
        realm.groups().group(id).setPermissions(new ManagementPermissionRepresentation(true));
        assertThat(realm.groups().group(id).getPermissions().isEnabled(), is(true));

        return grp;
    }

    private void assertCaseInsensitiveSearch() {
        // not-exact case-insensitive search
        assertThat(realm.users().search("user"), hasSize(1));
        assertThat(realm.users().search("User"), hasSize(1));
        assertThat(realm.users().search("USER"), hasSize(1));
        assertThat(realm.users().search("Use"), hasSize(1));

        // exact case-insensitive search
        assertThat(realm.users().search("user", true), hasSize(1));
        assertThat(realm.users().search("User", true), hasSize(1));
        assertThat(realm.users().search("USER", true), hasSize(1));
        assertThat(realm.users().search("Use", true), hasSize(0));
    }

    private void assertCaseSensitiveSearch() {
        // not-exact case-sensitive search
        assertThat(realm.users().search("user"), hasSize(0));
        assertThat(realm.users().search("User"), hasSize(1));
        assertThat(realm.users().search("USER"), hasSize(0));
        assertThat(realm.users().search("Use"), hasSize(1));
        
        // exact case-sensitive search
        assertThat(realm.users().search("user", true), hasSize(0));
        assertThat(realm.users().search("User", true), hasSize(1));
        assertThat(realm.users().search("USER", true), hasSize(0));
        assertThat(realm.users().search("Use", true), hasSize(0));
    }
}
