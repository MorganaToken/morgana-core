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

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import org.junit.jupiter.api.Test;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.WithEnvVars;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

@DistributionTest
@RawDistOnly(reason = "No need to test script again on container")
@WithEnvVars({"PRINT_ENV", "true"})
public class JavaOptsScriptTest {

    private static final String DEFAULT_OPTS = "(?:-\\S+ )*-Xms64m -Xmx512m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8(?: -\\S+)*";

    @Test
    @Launch({ "start-dev" })
    void testDefaultJavaOpts(LaunchResult result) {
        String output = result.getOutput();
        assertThat(output, matchesPattern("(?s).*Using JAVA_OPTS: " + DEFAULT_OPTS + ".*"));
    }

    @Test
    @Launch({ "start-dev" })
    @WithEnvVars({ "JAVA_OPTS", "-Dfoo=bar"})
    void testJavaOpts(LaunchResult result) {
        String output = result.getOutput();
        assertThat(output, containsString("JAVA_OPTS already set in environment; overriding default settings with values: -Dfoo=bar"));
        assertThat(output, containsString("Using JAVA_OPTS: -Dfoo=bar"));
    }

    @Test
    @Launch({ "start-dev" })
    @WithEnvVars({ "JAVA_OPTS_APPEND", "-Dfoo=bar"})
    void testJavaOptsAppend(LaunchResult result) {
        String output = result.getOutput();
        assertThat(output, containsString("Appending additional Java properties to JAVA_OPTS: -Dfoo=bar"));
        assertThat(output, matchesPattern("(?s).*Using JAVA_OPTS: " + DEFAULT_OPTS + " -Dfoo=bar\\n.*"));
    }



    @Test
    @Launch({ "start-dev" })
    @WithEnvVars({ "JAVA_ADD_OPENS", "-Dfoo=bar"})
    void testJavaAddOpens(LaunchResult result) {
        String output = result.getOutput();
        assertThat(output, containsString("JAVA_ADD_OPENS already set in environment; overriding default settings with values: -Dfoo=bar"));
        assertThat(output, not(containsString("--add-opens")));
        assertThat(output, matchesPattern("(?s).*Using JAVA_OPTS: " + DEFAULT_OPTS + " -Dfoo=bar.*"));
    }

}
