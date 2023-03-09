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

package org.keycloak.quarkus.runtime;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.hibernate.orm.runtime.integration.HibernateOrmIntegrationRuntimeInitListener;
import liquibase.Scope;

import org.hibernate.cfg.AvailableSettings;
import org.infinispan.manager.DefaultCacheManager;
import io.vertx.ext.web.RoutingContext;

import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.common.crypto.CryptoProvider;
import org.keycloak.common.crypto.FipsMode;
import org.keycloak.quarkus.runtime.configuration.Configuration;
import org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider;
import org.keycloak.quarkus.runtime.integration.QuarkusKeycloakSessionFactory;
import org.keycloak.quarkus.runtime.integration.web.QuarkusRequestFilter;
import org.keycloak.quarkus.runtime.storage.database.liquibase.FastServiceLocator;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;
import org.keycloak.quarkus.runtime.storage.legacy.infinispan.CacheManagerFactory;
import org.keycloak.theme.ClasspathThemeProviderFactory;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import liquibase.servicelocator.ServiceLocator;

@Recorder
public class KeycloakRecorder {

    public static final String DEFAULT_HEALTH_ENDPOINT = "/health";
    public static final String DEFAULT_METRICS_ENDPOINT = "/metrics";

    public void initConfig() {
        Config.init(new MicroProfileConfigProvider());
    }

    public void configureProfile(Profile.ProfileName profileName, Map<Profile.Feature, Boolean> features) {
        Profile.init(profileName, features);
    }

    public void configureLiquibase(Map<String, List<String>> services) {
        ServiceLocator locator = Scope.getCurrentScope().getServiceLocator();
        if (locator instanceof FastServiceLocator)
            ((FastServiceLocator) locator).initServices(services);
    }

    public void configSessionFactory(
            Map<Spi, Map<Class<? extends Provider>, Map<String, Class<? extends ProviderFactory>>>> factories,
            Map<Class<? extends Provider>, String> defaultProviders,
            Map<String, ProviderFactory> preConfiguredProviders,
            List<ClasspathThemeProviderFactory.ThemesRepresentation> themes, boolean reaugmented) {
        QuarkusKeycloakSessionFactory.setInstance(new QuarkusKeycloakSessionFactory(factories, defaultProviders, preConfiguredProviders, themes, reaugmented));
    }

    public RuntimeValue<CacheManagerFactory> createCacheInitializer(String config, boolean metricsEnabled, ShutdownContext shutdownContext) {
        try {
            CacheManagerFactory cacheManagerFactory = new CacheManagerFactory(config, metricsEnabled);

            shutdownContext.addShutdownTask(new Runnable() {
                @Override
                public void run() {
                    DefaultCacheManager cacheManager = cacheManagerFactory.getOrCreate();

                    if (cacheManager != null) {
                        cacheManager.stop();
                    }
                }
            });

            return new RuntimeValue<>(cacheManagerFactory);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerShutdownHook(ShutdownContext shutdownContext) {
        shutdownContext.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                QuarkusKeycloakSessionFactory.getInstance().close();
            }
        });
    }

    public HibernateOrmIntegrationRuntimeInitListener createUserDefinedUnitListener(String name) {
        return new HibernateOrmIntegrationRuntimeInitListener() {
            @Override
            public void contributeRuntimeProperties(BiConsumer<String, Object> propertyCollector) {
                InstanceHandle<AgroalDataSource> instance = Arc.container().instance(
                        AgroalDataSource.class, new DataSource() {
                            @Override public Class<? extends Annotation> annotationType() {
                                return DataSource.class;
                            }

                            @Override public String value() {
                                return name;
                            }
                        });
                propertyCollector.accept(AvailableSettings.DATASOURCE, instance.get());
            }
        };
    }

    public HibernateOrmIntegrationRuntimeInitListener createDefaultUnitListener() {
        return new HibernateOrmIntegrationRuntimeInitListener() {
            @Override
            public void contributeRuntimeProperties(BiConsumer<String, Object> propertyCollector) {
                propertyCollector.accept(AvailableSettings.DEFAULT_SCHEMA, Configuration.getRawValue("kc.db-schema"));
            }
        };
    }

    public QuarkusRequestFilter createRequestFilter(List<String> ignoredPaths, ExecutorService executor) {
        return new QuarkusRequestFilter(createIgnoredHttpPathsPredicate(ignoredPaths), executor);
    }

    private Predicate<RoutingContext> createIgnoredHttpPathsPredicate(List<String> ignoredPaths) {
        if (ignoredPaths == null || ignoredPaths.isEmpty()) {
            return null;
        }

        return new Predicate<>() {
            @Override
            public boolean test(RoutingContext context) {
                for (String ignoredPath : ignoredPaths) {
                    if (context.request().uri().startsWith(ignoredPath)) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    public void setCryptoProvider(FipsMode fipsMode) {
        String cryptoProvider = fipsMode.getProviderClassName();

        try {
            CryptoIntegration.setProvider(
                    (CryptoProvider) Thread.currentThread().getContextClassLoader().loadClass(cryptoProvider).getDeclaredConstructor().newInstance());
        } catch (ClassNotFoundException | NoClassDefFoundError cause) {
            if (fipsMode.isFipsEnabled()) {
                throw new RuntimeException("Failed to configure FIPS. Make sure you have added the Bouncy Castle FIPS dependencies to the 'providers' directory.");
            }
            throw new RuntimeException("Unexpected error when configuring the crypto provider: " + cryptoProvider, cause);
        } catch (Exception cause) {
            throw new RuntimeException("Unexpected error when configuring the crypto provider: " + cryptoProvider, cause);
        }
    }
}
