/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.arquillian.containers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.lang3.SystemUtils;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.keycloak.common.crypto.FipsMode;
import org.keycloak.testsuite.arquillian.SuiteContext;
import org.keycloak.testsuite.model.StoreProvider;

public abstract class AbstractQuarkusDeployableContainer implements DeployableContainer<KeycloakQuarkusConfiguration> {

    private static final Logger log = Logger.getLogger(AbstractQuarkusDeployableContainer.class);

    protected static AtomicBoolean restart = new AtomicBoolean();

    @Inject
    protected Instance<SuiteContext> suiteContext;

    protected KeycloakQuarkusConfiguration configuration;
    protected List<String> additionalBuildArgs = Collections.emptyList();

    @Override
    public Class<KeycloakQuarkusConfiguration> getConfigurationClass() {
        return KeycloakQuarkusConfiguration.class;
    }

    @Override
    public void setup(KeycloakQuarkusConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        try {
            deployArchiveToServer(archive);
            restartServer();
        } catch (Exception e) {
            throw new DeploymentException(e.getMessage(),e);
        }

        return new ProtocolMetaData();
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        File wrkDir = configuration.getProvidersPath().resolve("providers").toFile();
        try {
            if (isWindows()) {
                // stop before updating providers to avoid file locking issues on Windows
                stop();
            }
            Files.deleteIfExists(wrkDir.toPath().resolve(archive.getName()));
            restartServer();
        } catch (Exception e) {
            throw new DeploymentException(e.getMessage(),e);
        }
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return null;
    }

    @Override
    public void deploy(Descriptor descriptor) throws DeploymentException {

    }

    @Override
    public void undeploy(Descriptor descriptor) throws DeploymentException {

    }

    public void restartServer() throws Exception {
        stop();
        start();
    }

    protected List<String> getArgs() {
        return getArgs(new HashMap<>());
    }

    protected List<String> getArgs(Map<String, String> env) {
        List<String> commands = new ArrayList<>();

        commands.add("-v");
        commands.add("start");
        commands.add("--http-enabled=true");

        if (Boolean.parseBoolean(System.getProperty("auth.server.debug", "false"))) {
            commands.add("--debug");

            String debugPort = configuration.getDebugPort() > 0 ? Integer.toString(configuration.getDebugPort()) : System.getProperty("auth.server.debug.port", "5005");
            env.put("DEBUG_PORT", debugPort);

            String debugSuspend = System.getProperty("auth.server.debug.suspend");
            if (debugSuspend != null) {
                env.put("DEBUG_SUSPEND", debugSuspend);
            }
        }

        commands.add("--http-port=" + configuration.getBindHttpPort());
        commands.add("--https-port=" + configuration.getBindHttpsPort());

        if (configuration.getRoute() != null) {
            commands.add("-Djboss.node.name=" + configuration.getRoute());
        }

        if (System.getProperty("auth.server.quarkus.log-level") != null) {
            commands.add("--log-level=" + System.getProperty("auth.server.quarkus.log-level"));
        }

        commands.addAll(getAdditionalBuildArgs());

        commands = configureArgs(commands);

        final StoreProvider storeProvider = StoreProvider.getCurrentProvider();
        final Supplier<Boolean> shouldSetUpDb = () -> !restart.get() && !storeProvider.equals(StoreProvider.DEFAULT);
        final Supplier<String> getClusterConfig = () -> System.getProperty("auth.server.quarkus.cluster.config", "local");

        log.debugf("FIPS Mode: %s", configuration.getFipsMode());

        // only run build during first execution of the server (if the DB is specified), restarts or when running cluster tests
        if (restart.get() || shouldSetUpDb.get() || "ha".equals(getClusterConfig.get()) || configuration.getFipsMode() != FipsMode.DISABLED) {
            commands.removeIf("--optimized"::equals);
            commands.add("--http-relative-path=/auth");

            if (!storeProvider.isMapStore()) {
                String cacheMode = getClusterConfig.get();

                if ("local".equals(cacheMode)) {
                    commands.add("--cache=local");
                } else {
                    commands.add("--cache-config-file=cluster-" + cacheMode + ".xml");
                }
            }

            if (configuration.getFipsMode() != FipsMode.DISABLED) {
                addFipsOptions(commands);
            }
        }

        addStorageOptions(storeProvider, commands);

        return commands;
    }

    protected List<String> configureArgs(List<String> commands) {
        return commands;
    }

    private void deployArchiveToServer(Archive<?> archive) throws IOException, LifecycleException {
        if (isWindows()) {
            // stop before updating providers to avoid file locking issues on Windows
            stop();
        }
        File providersDir = configuration.getProvidersPath().resolve("providers").toFile();
        InputStream zipStream = archive.as(ZipExporter.class).exportAsInputStream();
        Files.copy(zipStream, providersDir.toPath().resolve(archive.getName()), StandardCopyOption.REPLACE_EXISTING);
    }

    protected static boolean isWindows() {
        return SystemUtils.IS_OS_WINDOWS;
    }

    public List<String> getAdditionalBuildArgs() {
        return additionalBuildArgs;
    }

    public void setAdditionalBuildArgs(List<String> newArgs) {
        additionalBuildArgs = newArgs;
    }

    public void resetConfiguration() {
        additionalBuildArgs = Collections.emptyList();
    }

    protected void waitForReadiness() throws MalformedURLException, LifecycleException {
        SuiteContext suiteContext = this.suiteContext.get();
        //TODO: not sure if the best endpoint but it makes sure that everything is properly initialized. Once we have
        // support for MP Health this should change
        URL contextRoot = new URL(getBaseUrl(suiteContext) + "/auth/realms/master/");
        HttpURLConnection connection;
        long startTime = System.currentTimeMillis();

        while (true) {
            if (System.currentTimeMillis() - startTime > getStartTimeout()) {
                stop();
                throw new IllegalStateException("Timeout [" + getStartTimeout() + "] while waiting for Quarkus server");
            }

            try {
                // wait before checking for opening a new connection
                Thread.sleep(1000);
                if ("https".equals(contextRoot.getProtocol())) {
                    HttpsURLConnection httpsConnection = (HttpsURLConnection) (connection = (HttpURLConnection) contextRoot.openConnection());
                    httpsConnection.setSSLSocketFactory(createInsecureSslSocketFactory());
                    httpsConnection.setHostnameVerifier(createInsecureHostnameVerifier());
                } else {
                    connection = (HttpURLConnection) contextRoot.openConnection();
                }

                connection.setReadTimeout((int) getStartTimeout());
                connection.setConnectTimeout((int) getStartTimeout());
                connection.connect();

                if (connection.getResponseCode() == 200) {
                    break;
                }

                connection.disconnect();
            } catch (Exception ignore) {
            }
        }

        log.infof("Keycloak is ready at %s", contextRoot);
    }

    private URL getBaseUrl(SuiteContext suiteContext) throws MalformedURLException {
        URL baseUrl = suiteContext.getAuthServerInfo().getContextRoot();

        // might be running behind a load balancer
        if ("https".equals(baseUrl.getProtocol())) {
            baseUrl = new URL(baseUrl.toString().replace(String.valueOf(baseUrl.getPort()), String.valueOf(configuration.getBindHttpsPort())));
        } else {
            baseUrl = new URL(baseUrl.toString().replace(String.valueOf(baseUrl.getPort()), String.valueOf(configuration.getBindHttpPort())));
        }
        return baseUrl;
    }

    private HostnameVerifier createInsecureHostnameVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        };
    }

    private SSLSocketFactory createInsecureSslSocketFactory() throws IOException {
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            }

            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }};

        SSLContext sslContext;
        SSLSocketFactory socketFactory;

        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            socketFactory = sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("Can't create unsecure trust manager");
        }
        return socketFactory;
    }

    private long getStartTimeout() {
        return TimeUnit.SECONDS.toMillis(configuration.getStartupTimeoutInSeconds());
    }

    private void addStorageOptions(StoreProvider storeProvider, List<String> commands) {
        log.debugf("Store '%s' is used.", storeProvider.name());
        storeProvider.addStoreOptions(commands);
    }

    private void addFipsOptions(List<String> commands) {
        commands.add("--features=fips");
        commands.add("--fips-mode=" + configuration.getFipsMode().toString());

        log.debugf("Keystore file: %s, truststore file: %s",
                configuration.getKeystoreFile(),
                configuration.getTruststoreFile());
        commands.add("--https-key-store-file=" + configuration.getKeystoreFile());
        commands.add("--https-key-store-password=" + configuration.getKeystorePassword());
        commands.add("--https-trust-store-file=" + configuration.getTruststoreFile());
        commands.add("--https-trust-store-password=" + configuration.getTruststorePassword());
        commands.add("--spi-truststore-file-file=" + configuration.getTruststoreFile());
        commands.add("--spi-truststore-file-password=" + configuration.getTruststorePassword());

        // BCFIPS approved mode requires passwords of at least 112 bits (14 characters) to be used. To bypass this, we use this by default
        // as testsuite uses shorter passwords everywhere
        if (FipsMode.STRICT == configuration.getFipsMode()) {
            commands.add("--spi-password-hashing-pbkdf2-max-padding-length=14");
            commands.add("--spi-password-hashing-pbkdf2-sha256-max-padding-length=14");
            commands.add("--spi-password-hashing-pbkdf2-sha512-max-padding-length=14");
        }

        commands.add("--log-level=INFO,org.keycloak.common.crypto:TRACE,org.keycloak.crypto:TRACE,org.keycloak.truststore:TRACE");

        configuration.appendJavaOpts("-Djava.security.properties=" + System.getProperty("auth.server.java.security.file"));
    }
}
