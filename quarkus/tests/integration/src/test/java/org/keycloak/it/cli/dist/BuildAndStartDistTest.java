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

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.utils.KeycloakDistribution;
import org.keycloak.it.utils.RawKeycloakDistribution;

import static org.keycloak.quarkus.runtime.cli.command.AbstractStartCommand.OPTIMIZED_BUILD_OPTION_LONG;

@DistributionTest
@RawDistOnly(reason = "Containers are immutable")
@TestMethodOrder(OrderAnnotation.class)
public class BuildAndStartDistTest {

    @Test
    void testBuildAndStart(KeycloakDistribution dist) {
        RawKeycloakDistribution rawDist = dist.unwrap(RawKeycloakDistribution.class);
        // start using based on the build options set via CLI
        CLIResult cliResult = rawDist.run("build", "--storage=chm");
        cliResult.assertBuild();
        cliResult = rawDist.run("start", "--http-enabled=true", "--hostname-strict=false", OPTIMIZED_BUILD_OPTION_LONG);
        cliResult.assertNoBuild();
        cliResult.assertStarted();

        // start using based on the build options set via conf file
        rawDist.setProperty("http-enabled", "true");
        rawDist.setProperty("hostname-strict", "false");
        rawDist.setProperty("storage", "chm");
        cliResult = rawDist.run("build");
        cliResult.assertBuild();
        cliResult = rawDist.run("start", OPTIMIZED_BUILD_OPTION_LONG);
        cliResult.assertNoBuild();
        cliResult.assertStarted();
        // running start without optimized flag should not cause a build
        cliResult = rawDist.run("start");
        cliResult.assertNoBuild();
        cliResult.assertStarted();

        // remove the build option from conf file to force a build during start
        rawDist.removeProperty("storage");
        cliResult = rawDist.run("start");
        cliResult.assertBuild();
        cliResult.assertStarted();
    }
}
