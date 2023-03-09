/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.it.cli.dist;

import org.junit.jupiter.api.Test;
import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.LegacyStore;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.TestProvider;
import com.acme.provider.legacy.jpa.entity.CustomLegacyJpaEntityProvider;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;

@DistributionTest
@RawDistOnly(reason = "Containers are immutable")
@LegacyStore
public class CustomLegacyJpaEntityProviderDistTest {

    @Test
    @TestProvider(CustomLegacyJpaEntityProvider.class)
    @Launch({ "start-dev", "--log-level=org.hibernate.jpa.internal.util.LogHelper:debug" })
    void testUserManagedEntityNotAddedToDefaultPU(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertStringCount("name: user-store", 1);
        cliResult.assertStringCount("com.acme.provider.legacy.jpa.entity.Realm", 1);
        cliResult.assertStartedDevMode();
    }
}
