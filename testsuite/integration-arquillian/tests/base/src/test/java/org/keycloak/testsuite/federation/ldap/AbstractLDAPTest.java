/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.federation.ldap;

import org.keycloak.common.Profile.Feature;
import java.util.List;
import java.util.Map;

import org.jboss.arquillian.graphene.page.Page;
import org.junit.Rule;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.storage.ldap.mappers.LDAPStorageMapper;
import org.keycloak.testsuite.AbstractTestRealmKeycloakTest;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.ProfileAssume;
import org.keycloak.testsuite.pages.AccountUpdateProfilePage;
import org.keycloak.testsuite.pages.AppPage;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.pages.LoginPasswordUpdatePage;
import org.keycloak.testsuite.pages.OAuthGrantPage;
import org.keycloak.testsuite.pages.RegisterPage;
import org.keycloak.testsuite.util.LDAPRule;
import org.junit.BeforeClass;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public abstract class AbstractLDAPTest extends AbstractTestRealmKeycloakTest {

    static final String TEST_REALM_NAME = "test";

    protected static String ldapModelId;

    @Rule
    public AssertEvents events = new AssertEvents(this);

    @Page
    protected AppPage appPage;

    @Page
    protected LoginPage loginPage;

    @Page
    protected RegisterPage registerPage;

    @Page
    protected AccountUpdateProfilePage profilePage;

    @Page
    protected OAuthGrantPage grantPage;

    @Page
    protected LoginPasswordUpdatePage requiredActionChangePasswordPage;



    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
    }


    @Override
    public void importTestRealms() {
        super.importTestRealms();
        log.infof("Test realms imported");

        createLDAPProvider();

        afterImportTestRealm();
    }

    @BeforeClass
    public static void checkNotMapStorage() {
        ProfileAssume.assumeFeatureDisabled(Feature.MAP_STORAGE);
    }


    protected void createLDAPProvider() {
        Map<String, String> cfg = getLDAPRule().getConfig();
        ldapModelId = testingClient.testing().ldap(TEST_REALM_NAME).createLDAPProvider(cfg, isImportEnabled());
        log.infof("LDAP Provider created");
    }


    protected boolean isImportEnabled() {
        return true;
    }

    /**
     * Executed once per class. It is executed after the test realm is imported
     */
    protected abstract void afterImportTestRealm();

    protected abstract LDAPRule getLDAPRule();


    protected ComponentRepresentation findMapperRepByName(String name) {
        return testRealm().components().query(ldapModelId, LDAPStorageMapper.class.getName()).stream()
          .filter(mapper -> mapper.getName().equals(name))
          .findAny().orElse(null);
    }
}
