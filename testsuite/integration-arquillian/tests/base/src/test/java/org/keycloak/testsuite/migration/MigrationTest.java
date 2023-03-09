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
package org.keycloak.testsuite.migration;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.arquillian.annotation.AuthServerContainerExclude;
import org.keycloak.testsuite.arquillian.migration.Migration;

import javax.ws.rs.NotFoundException;
import java.util.List;

import org.keycloak.testsuite.arquillian.annotation.AuthServerContainerExclude.AuthServer;
import static org.keycloak.testsuite.auth.page.AuthRealm.MASTER;

/**
 * Test for DB migration with legacy JPA store
 *
 * @author <a href="mailto:vramik@redhat.com">Vlastislav Ramik</a>
 */
public class MigrationTest extends AbstractMigrationTest {

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        log.info("Adding no test realms for migration test. Test realm should be migrated from previous version.");
    }

    @Before
    public void beforeMigrationTest() {
        migrationRealm = adminClient.realms().realm(MIGRATION);
        migrationRealm2 = adminClient.realms().realm(MIGRATION2);
        masterRealm = adminClient.realms().realm(MASTER);
        //add migration realms to testRealmReps to make them removed after test
        addTestRealmToTestRealmReps(migrationRealm);
        addTestRealmToTestRealmReps(migrationRealm2);
    }

    private void addTestRealmToTestRealmReps(RealmResource realm) {
        try {
            testRealmReps.add(realm.toRepresentation());
        } catch (NotFoundException ignore) {
        }
    }

    @Test
    @Migration(versionPrefix = "17.")
    public void migration17_xTest() throws Exception{
        testMigratedData(false);
        testMigrationTo18_x();

        // Always test offline-token login during migration test
        testOfflineTokenLogin();
        testExtremelyLongClientAttribute(migrationRealm);

        testMigrationTo20_x();
    }
}
