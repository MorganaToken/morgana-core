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

package org.keycloak.it.junit5.extension;

import io.quarkus.deployment.util.FileUtil;
import io.quarkus.runtime.configuration.QuarkusConfigFactory;
import io.quarkus.test.junit.QuarkusMainTestExtension;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.keycloak.it.utils.KeycloakDistribution;
import org.keycloak.it.utils.RawDistRootPath;
import org.keycloak.it.utils.RawKeycloakDistribution;
import org.keycloak.quarkus.runtime.Environment;
import org.keycloak.quarkus.runtime.cli.command.Start;
import org.keycloak.quarkus.runtime.cli.command.StartDev;
import org.keycloak.quarkus.runtime.configuration.KeycloakPropertiesConfigSource;
import org.keycloak.quarkus.runtime.configuration.test.TestConfigArgsConfigSource;
import org.keycloak.quarkus.runtime.integration.QuarkusPlatform;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.it.junit5.extension.DistributionTest.ReInstall.BEFORE_ALL;
import static org.keycloak.it.junit5.extension.DistributionType.RAW;
import static org.keycloak.quarkus.runtime.Environment.forceTestLaunchMode;
import static org.keycloak.quarkus.runtime.cli.command.Main.CONFIG_FILE_LONG_NAME;
import static org.keycloak.quarkus.runtime.cli.command.Main.CONFIG_FILE_SHORT_NAME;

public class CLITestExtension extends QuarkusMainTestExtension {

    private static final String KEY_VALUE_SEPARATOR = "[= ]";
    private KeycloakDistribution dist;
    private final Set<String> testSysProps = new HashSet<>();
    private DatabaseContainer databaseContainer;
    private CLIResult result;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        DistributionTest distConfig = getDistributionConfig(context);
        Launch launch = context.getRequiredTestMethod().getAnnotation(Launch.class);

        if (launch != null) {
            for (String arg : launch.value()) {
                if (arg.contains(CONFIG_FILE_SHORT_NAME) || arg.contains(CONFIG_FILE_LONG_NAME)) {
                    Pattern kvSeparator = Pattern.compile(KEY_VALUE_SEPARATOR);
                    String[] cfKeyValue = kvSeparator.split(arg);
                    setProperty(KeycloakPropertiesConfigSource.KEYCLOAK_CONFIG_FILE_PROP, cfKeyValue[1]);
                } else if (distConfig == null && arg.startsWith("-D")) {
                    // allow setting system properties from JVM tests
                    int keyValueSeparator = arg.indexOf('=');

                    if (keyValueSeparator == -1) {
                        continue;
                    }

                    String name = arg.substring(2, keyValueSeparator);
                    String value = arg.substring(keyValueSeparator + 1);
                    setProperty(name, value);
                }
            }
        }

        configureDatabase(context);

        if (distConfig != null) {
            onKeepServerAlive(context.getRequiredTestMethod().getAnnotation(KeepServerAlive.class));

            if (dist == null) {
                dist = createDistribution(distConfig, getLegacyStoreConfig(context), getDatabaseConfig(context));
            }

            copyTestProvider(context.getRequiredTestClass().getAnnotation(TestProvider.class));
            copyTestProvider(context.getRequiredTestMethod().getAnnotation(TestProvider.class));
            onBeforeStartDistribution(context.getRequiredTestClass().getAnnotation(BeforeStartDistribution.class));
            onBeforeStartDistribution(context.getRequiredTestMethod().getAnnotation(BeforeStartDistribution.class));

            configureEnvVars(context.getRequiredTestClass().getAnnotation(WithEnvVars.class));
            configureEnvVars(context.getRequiredTestMethod().getAnnotation(WithEnvVars.class));

            if (launch != null) {
                result = dist.run(Stream.concat(List.of(launch.value()).stream(), List.of(distConfig.defaultOptions()).stream()).collect(Collectors.toList()));
            }
        } else {
            configureProfile(context);
            super.beforeEach(context);
        }
    }

    private static LegacyStore getLegacyStoreConfig(ExtensionContext context) {
        return context.getTestClass().get().getDeclaredAnnotation(LegacyStore.class);
    }

    private void copyTestProvider(TestProvider provider) {
        if (provider == null) {
            return;
        }

        if (RAW.equals(DistributionType.getCurrent().orElse(RAW))) {
            try {
                dist.unwrap(RawKeycloakDistribution.class).copyProvider(provider.value().getDeclaredConstructor().newInstance());
            } catch (Exception cause) {
                throw new RuntimeException("Failed to instantiate test provider: " + provider.getClass(), cause);
            }
        }
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        if (dist == null) {
            super.interceptTestMethod(invocation, invocationContext, extensionContext);
        } else {
            invocation.proceed();
        }
    }

    private void onBeforeStartDistribution(BeforeStartDistribution annotation) {
        if (annotation != null) {
            try {
                annotation.value().getDeclaredConstructor().newInstance().accept(dist);
            } catch (Exception cause) {
                throw new RuntimeException("Error when invoking " + annotation.value() + " instance before starting distribution", cause);
            }
        }
    }

    private void onKeepServerAlive(KeepServerAlive annotation) {
        if(annotation != null && dist != null) {
            try {
                dist.setManualStop(true);
            } catch (Exception cause) {
                throw new RuntimeException("Error when invoking " + annotation, cause);
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        DistributionTest distConfig = getDistributionConfig(context);

        if (distConfig != null) {
            if (distConfig.keepAlive()) {
                dist.stop();
            }

            if (DistributionTest.ReInstall.BEFORE_TEST.equals(distConfig.reInstall())) {
                dist = null;
            }
        }

        super.afterEach(context);
        reset(distConfig);
    }

    private void reset(DistributionTest distConfig) {
        QuarkusConfigFactory.setConfig(null);
        //remove the config file property if set, and also the profile, to not have side effects in other tests.
        System.getProperties().remove(Environment.PROFILE);
        System.getProperties().remove("quarkus.profile");
        TestConfigArgsConfigSource.setCliArgs(new String[0]);
        for (String property : testSysProps) {
            System.getProperties().remove(property);
        }
        if (databaseContainer != null && databaseContainer.isRunning()) {
            databaseContainer.stop();
            databaseContainer = null;
        }
        result = null;
        if (RAW.equals(DistributionType.getCurrent().orElse(RAW))) {
            if (distConfig != null && !DistributionTest.ReInstall.NEVER.equals(distConfig.reInstall()) && dist != null) {
                try {
                    FileUtil.deleteDirectory(getDistPath().getDistRootPath().resolve("conf"));
                    getDistPath().getDistRootPath().resolve("conf").toFile().mkdirs();
                    FileUtil.deleteDirectory(getDistPath().getDistRootPath().resolve("providers"));
                    getDistPath().getDistRootPath().resolve("providers").toFile().mkdirs();
                    FileUtil.deleteDirectory(getDistPath().getDistRootPath().resolve("data"));
                    getDistPath().getDistRootPath().resolve("data").toFile().mkdirs();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete conf directory");
                }
            }
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        DistributionTest distConfig = getDistributionConfig(context);

        if (distConfig != null) {
            if (BEFORE_ALL.equals(distConfig.reInstall())) {
                dist = createDistribution(distConfig, getLegacyStoreConfig(context), getDatabaseConfig(context));
            }
        } else {
            forceTestLaunchMode();
        }

        super.beforeAll(context);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (dist != null) {
            // just to make sure the server is stopped after all tests
            dist.stop();
        }
        super.afterAll(context);
    }

    private KeycloakDistribution createDistribution(DistributionTest config, LegacyStore legacyStoreConfig, WithDatabase databaseConfig) {
        return new KeycloakDistributionDecorator(legacyStoreConfig, databaseConfig, config, DistributionType.getCurrent().orElse(RAW).newInstance(config));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();

        if (type == LaunchResult.class) {
            boolean isDistribution = getDistributionConfig(context) != null;

            if (isDistribution) {
                return result;
            }

            LaunchResult result = (LaunchResult) super.resolveParameter(parameterContext, context);
            List<String> outputStream = result.getOutputStream();
            List<String> errStream = result.getErrorStream();
            int exitCode = result.exitCode();

            return CLIResult.create(outputStream, errStream, exitCode);
        }

        if (type.equals(RawDistRootPath.class)) {
            //assuming the path to the distribution directory
            return getDistPath();
        }

        if (type.equals(KeycloakDistribution.class)) {
            if (context.getTestClass().orElse(Object.class).getDeclaredAnnotation(DistributionTest.class) == null) {
                throw new RuntimeException("Only tests annotated with " + DistributionTest.class + " can inject a distribution instance");
            }
            return dist;
        }

        // for now, no support for manual launching using QuarkusMainLauncher
        throw new RuntimeException("Parameter type [" + type + "] not supported");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type == LaunchResult.class || type == RawDistRootPath.class || type == KeycloakDistribution.class;
    }

    private void configureProfile(ExtensionContext context) {
        List<String> cliArgs = getCliArgs(context);

        // when running tests, build steps happen before executing our CLI so that profiles are not set and not taken
        // into account when executing the build steps
        // this is basically reproducing the behavior when using kc.sh
        if (cliArgs.contains(Start.NAME)) {
            Environment.setProfile("prod");
        } else if (cliArgs.contains(StartDev.NAME)) {
            Environment.forceDevProfile();
        }
    }

    private void configureDatabase(ExtensionContext context) {
        WithDatabase database = getDatabaseConfig(context);

        if (database != null) {
            if (dist == null) {
                configureDevServices();
                setProperty("kc.db", database.alias());
                setProperty("kc.db-password", DatabaseContainer.DEFAULT_PASSWORD);
            } else {
                databaseContainer = new DatabaseContainer(database.alias());

                databaseContainer.start();

                if (database.buildOptions().length == 0) {
                    dist.setProperty("db", database.alias());
                } else {
                    for (String option : database.buildOptions()) {
                        dist.setProperty(option.substring(0, option.indexOf('=')), option.substring(option.indexOf('=') + 1));
                    }
                }

                databaseContainer.configureDistribution(dist);

                dist.run("build");
            }
        } else {
            // This is for re-creating the H2 database instead of using the default in home
            setProperty("kc.db-url-path", new QuarkusPlatform().getTmpDirectory().getAbsolutePath());
        }
    }

    private static WithDatabase getDatabaseConfig(ExtensionContext context) {
        return context.getTestClass().orElse(Object.class).getDeclaredAnnotation(WithDatabase.class);
    }

    private void configureDevServices() {
        setProperty("quarkus.vault.devservices.enabled", Boolean.FALSE.toString());
        setProperty("quarkus.datasource.devservices.enabled", Boolean.TRUE.toString());
        setProperty("quarkus.devservices.enabled", Boolean.TRUE.toString());
    }

    private void configureEnvVars(WithEnvVars envVars) {
        if (envVars == null) {
            return;
        }

        for (int i=0; i<envVars.value().length; i=i+2) {
            dist.setEnvVar(envVars.value()[i], envVars.value()[i+1]);
        }
    }

    private void setProperty(String name, String value) {
        System.setProperty(name, value);
        testSysProps.add(name);
    }

    private List<String> getCliArgs(ExtensionContext context) {
        Launch annotation = context.getRequiredTestMethod().getAnnotation(Launch.class);

        if (annotation != null) {
            return Arrays.asList(annotation.value());
        }

        return Collections.emptyList();
    }

    private DistributionTest getDistributionConfig(ExtensionContext context) {
        return context.getTestClass().get().getDeclaredAnnotation(DistributionTest.class);
    }

    private RawDistRootPath getDistPath(){
        return new RawDistRootPath(dist.unwrap(RawKeycloakDistribution.class).getDistPath());
    }
}
