package org.keycloak.it.storage.database.dist;

import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.LegacyStore;
import org.keycloak.it.junit5.extension.WithDatabase;
import org.keycloak.it.storage.database.MySQLTest;

@DistributionTest(removeBuildOptionsAfterBuild = true)
@WithDatabase(alias = "mysql")
@LegacyStore
public class MySQLDistTest extends MySQLTest {
}
