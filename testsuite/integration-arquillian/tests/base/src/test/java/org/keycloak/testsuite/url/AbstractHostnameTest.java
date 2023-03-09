package org.keycloak.testsuite.url;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.logging.Logger;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.arquillian.containers.AbstractQuarkusDeployableContainer;
import org.keycloak.testsuite.util.OAuthClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractHostnameTest extends AbstractKeycloakTest {

    private static final Logger LOGGER = Logger.getLogger(AbstractHostnameTest.class);

    @ArquillianResource
    protected ContainerController controller;

    void reset() throws Exception {
        LOGGER.info("Reset hostname config to default");

        if (suiteContext.getAuthServerInfo().isUndertow()) {
            controller.stop(suiteContext.getAuthServerInfo().getQualifier());
            removeProperties("keycloak.hostname.provider",
                    "keycloak.frontendUrl",
                    "keycloak.adminUrl",
                    "keycloak.hostname.default.forceBackendUrlToFrontendUrl",
                    "keycloak.hostname.fixed.hostname",
                    "keycloak.hostname.fixed.httpPort",
                    "keycloak.hostname.fixed.httpsPort",
                    "keycloak.hostname.fixed.alwaysHttps");
            controller.start(suiteContext.getAuthServerInfo().getQualifier());
        } else if (suiteContext.getAuthServerInfo().isQuarkus()) {
            AbstractQuarkusDeployableContainer container = (AbstractQuarkusDeployableContainer)suiteContext.getAuthServerInfo().getArquillianContainer().getDeployableContainer();
            container.resetConfiguration();
            configureDefault(OAuthClient.AUTH_SERVER_ROOT, false, null);
            container.restartServer();
        } else {
            throw new RuntimeException("Don't know how to config");
        }

        reconnectAdminClient();
    }

    void configureDefault(String frontendUrl, boolean forceBackendUrlToFrontendUrl, String adminUrl) throws Exception {
        LOGGER.infov("Configuring default hostname provider: frontendUrl={0}, forceBackendUrlToFrontendUrl={1}, adminUrl={3}", frontendUrl, forceBackendUrlToFrontendUrl, adminUrl);

        if (suiteContext.getAuthServerInfo().isUndertow()) {
            controller.stop(suiteContext.getAuthServerInfo().getQualifier());
            System.setProperty("keycloak.hostname.provider", "default");
            System.setProperty("keycloak.frontendUrl", frontendUrl);
            if (adminUrl != null){
                System.setProperty("keycloak.adminUrl", adminUrl);
            }
            System.setProperty("keycloak.hostname.default.forceBackendUrlToFrontendUrl", String.valueOf(forceBackendUrlToFrontendUrl));
            controller.start(suiteContext.getAuthServerInfo().getQualifier());
        } else if (suiteContext.getAuthServerInfo().isQuarkus()) {
            controller.stop(suiteContext.getAuthServerInfo().getQualifier());
            AbstractQuarkusDeployableContainer container = (AbstractQuarkusDeployableContainer)suiteContext.getAuthServerInfo().getArquillianContainer().getDeployableContainer();
            List<String> additionalArgs = new ArrayList<>();
            URI frontendUri = URI.create(frontendUrl);
            // enable proxy so that we can check headers are taken into account when building urls
            additionalArgs.add("--proxy=reencrypt");
            additionalArgs.add("--hostname=" + frontendUri.getHost());
            additionalArgs.add("--hostname-path=" + frontendUri.getPath());
            if ("https".equals(frontendUri.getScheme())) {
                additionalArgs.add("--hostname-strict-https=true");
            }
            additionalArgs.add("--hostname-strict-backchannel="+ forceBackendUrlToFrontendUrl);
            container.setAdditionalBuildArgs(additionalArgs);
            controller.start(suiteContext.getAuthServerInfo().getQualifier());
        } else {
            throw new RuntimeException("Don't know how to config");
        }

        reconnectAdminClient();
    }

    void configureFixed(String hostname, int httpPort, int httpsPort, boolean alwaysHttps) throws Exception {

        if (suiteContext.getAuthServerInfo().isUndertow()) {
            controller.stop(suiteContext.getAuthServerInfo().getQualifier());
            System.setProperty("keycloak.hostname.provider", "fixed");
            System.setProperty("keycloak.hostname.fixed.hostname", hostname);
            System.setProperty("keycloak.hostname.fixed.httpPort", String.valueOf(httpPort));
            System.setProperty("keycloak.hostname.fixed.httpsPort", String.valueOf(httpsPort));
            System.setProperty("keycloak.hostname.fixed.alwaysHttps", String.valueOf(alwaysHttps));
            controller.start(suiteContext.getAuthServerInfo().getQualifier());
        } else {
            throw new RuntimeException("Don't know how to config");
        }

        reconnectAdminClient();
    }

    private void removeProperties(String... keys) {
        for (String k : keys) {
            System.getProperties().remove(k);
        }
    }


}
