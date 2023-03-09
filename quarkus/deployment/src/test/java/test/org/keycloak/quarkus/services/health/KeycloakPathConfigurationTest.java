/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package test.org.keycloak.quarkus.services.health;

import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.restassured.RestAssured.given;

class KeycloakPathConfigurationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                .addAsResource("keycloak.conf", "META-INF/keycloak.conf"))
            .overrideConfigKey("kc.http-relative-path","/auth")
            .overrideConfigKey("quarkus.http.non-application-root-path", "/q")
            .overrideConfigKey("quarkus.micrometer.export.prometheus.path", "/prom/metrics");


    @Test
    void testHealth() {
        given().basePath("/")
                .when().get("q/health")
                .then()
                .statusCode(200);
    }

    @Test
    void testWrongHealthEndpoints() {
        given().basePath("/")
                .when().get("health")
                .then()
                // Health is available under `/q/health` (see non-application-root-path),
                // so /health should return 404.
                .statusCode(404);

        given().basePath("/")
                .when().get("auth/health")
                .then()
                // Health is available under `/q/health` (see non-application-root-path),
                // so /auth/health one should return 404.
                .statusCode(404);
    }

    @Test
    void testMetrics() {
        given().basePath("/")
                .when().get("prom/metrics")
                .then()
                .statusCode(200);
    }

    @Test
    void testWrongMetricsEndpoints() {
        given().basePath("/")
                .when().get("metrics")
                .then()
                // Metrics is available under `/prom/metrics` (see non-application-root-path),
                // so /metrics should return 404.
                .statusCode(404);

        given().basePath("/")
                .when().get("auth/metrics")
                .then()
                // Metrics is available under `/prom/metrics` (see non-application-root-path),
                // so /auth/metrics should return 404.
                .statusCode(404);

        given().basePath("/")
                .when().get("q/metrics")
                .then()
                // Metrics is available under `/prom/metrics` (see non-application-root-path),
                // so /q/metrics should return 404.
                .statusCode(404);

    }

    @Test
    void testAuthEndpointAvailable() {

        given().basePath("/")
                .when().get("auth")
                .then()
                .statusCode(200);
    }

    @Test
    void testRootUnavailable() {
        given().basePath("/")
                .when().get("")
                .then()
                // application root is configured to /auth, so we expect 404 on /
                .statusCode(404);
    }
}
