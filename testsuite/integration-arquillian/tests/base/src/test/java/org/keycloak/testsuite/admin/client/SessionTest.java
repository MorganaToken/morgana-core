/*
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.keycloak.testsuite.admin.client;

import org.jboss.arquillian.graphene.page.Page;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.common.Profile;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.keycloak.testsuite.arquillian.annotation.DisableFeature;
import org.keycloak.testsuite.auth.page.account.AccountManagement;
import org.keycloak.testsuite.util.AdminEventPaths;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.keycloak.testsuite.auth.page.AuthRealm.TEST;

/**
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2016 Red Hat Inc.
 */
@DisableFeature(value = Profile.Feature.ACCOUNT2, skipRestart = true) // TODO remove this (KEYCLOAK-16228)
public class SessionTest extends AbstractClientTest {


    @Page
    protected AccountManagement testRealmAccountManagementPage;

    @Before
    public void init() {
        // make user test user exists in test realm
        createTestUserWithAdminClient();
        getCleanup().addUserId(testUser.getId());

        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.userResourcePath(testUser.getId()), ResourceType.USER);
        assertAdminEvents.assertEvent(getRealmId(), OperationType.ACTION, AdminEventPaths.userResetPasswordPath(testUser.getId()), ResourceType.USER);
    }

    @Override
    public void setDefaultPageUriParameters() {
        super.setDefaultPageUriParameters();
        testRealmAccountManagementPage.setAuthRealm(TEST);
        loginPage.setAuthRealm(TEST);
    }

    @Test
    public void testGetAppSessionCount() {
        ClientResource accountClient = findClientResourceById("account");
        int sessionCount = accountClient.getApplicationSessionCount().get("count");
        assertEquals(0, sessionCount);

        testRealmAccountManagementPage.navigateTo();
        loginPage.form().login(testUser);

        sessionCount = accountClient.getApplicationSessionCount().get("count");
        assertEquals(1, sessionCount);

        testRealmAccountManagementPage.signOut();

        sessionCount = accountClient.getApplicationSessionCount().get("count");
        assertEquals(0, sessionCount);
    }

    @Test
    public void testGetUserSessions() {
        //List<java.util.Map<String, String>> stats = this.testRealmResource().getClientSessionStats();
        ClientResource account = findClientResourceById("account");

        testRealmAccountManagementPage.navigateTo();
        loginPage.form().login(testUser);

        List<UserSessionRepresentation> sessions = account.getUserSessions(0, 5);
        assertEquals(1, sessions.size());

        UserSessionRepresentation rep = sessions.get(0);

        UserRepresentation testUserRep = getFullUserRep(testUser.getUsername());
        assertEquals(testUserRep.getId(), rep.getUserId());
        assertEquals(testUserRep.getUsername(), rep.getUsername());

        String clientId = account.toRepresentation().getId();
        assertEquals("account", rep.getClients().get(clientId));
        assertNotNull(rep.getIpAddress());
        assertNotNull(rep.getLastAccess());
        assertNotNull(rep.getStart());
        assertFalse(rep.isRememberMe());

        testRealmAccountManagementPage.signOut();
    }

    @Test
    public void testGetUserSessionsWithRememberMe() {
        RealmRepresentation realm = adminClient.realm(TEST).toRepresentation();
        realm.setRememberMe(true);
        adminClient.realm(TEST).update(realm);

        testRealmAccountManagementPage.navigateTo();
        loginPage.form().rememberMe(true);
        loginPage.form().login(testUser);

        ClientResource account = findClientResourceById("account");
        List<UserSessionRepresentation> sessions = account.getUserSessions(0, 5);
        assertEquals(1, sessions.size());

        UserSessionRepresentation rep = sessions.get(0);
        assertTrue(rep.isRememberMe());

        testRealmAccountManagementPage.signOut();
    }
}
