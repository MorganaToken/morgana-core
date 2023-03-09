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
package org.keycloak.testsuite.model.parameters;

import com.google.common.collect.ImmutableSet;
import org.jboss.logging.Logger;
import org.keycloak.authorization.store.StoreFactorySpi;
import org.keycloak.events.EventStoreSpi;
import org.keycloak.models.DeploymentStateSpi;
import org.keycloak.models.SingleUseObjectSpi;
import org.keycloak.models.UserLoginFailureSpi;
import org.keycloak.models.UserSessionSpi;
import org.keycloak.models.locking.GlobalLockProviderSpi;
import org.keycloak.models.locking.NoneGlobalLockProviderFactory;
import org.keycloak.models.map.authSession.MapRootAuthenticationSessionProviderFactory;
import org.keycloak.models.map.authorization.MapAuthorizationStoreFactory;
import org.keycloak.models.map.client.MapClientProviderFactory;
import org.keycloak.models.map.clientscope.MapClientScopeProviderFactory;
import org.keycloak.models.map.keys.MapPublicKeyStorageProviderFactory;
import org.keycloak.models.map.singleUseObject.MapSingleUseObjectProviderFactory;
import org.keycloak.models.map.storage.hotRod.connections.DefaultHotRodConnectionProviderFactory;
import org.keycloak.models.map.storage.hotRod.connections.HotRodConnectionProviderFactory;
import org.keycloak.models.map.storage.hotRod.connections.HotRodConnectionSpi;
import org.keycloak.models.map.deploymentState.MapDeploymentStateProviderFactory;
import org.keycloak.models.map.group.MapGroupProviderFactory;
import org.keycloak.models.map.loginFailure.MapUserLoginFailureProviderFactory;
import org.keycloak.models.map.realm.MapRealmProviderFactory;
import org.keycloak.models.map.role.MapRoleProviderFactory;
import org.keycloak.models.map.storage.MapStorageSpi;
import org.keycloak.models.map.storage.chm.ConcurrentHashMapStorageProviderFactory;
import org.keycloak.models.map.storage.hotRod.HotRodMapStorageProviderFactory;
import org.keycloak.models.map.storage.hotRod.locking.HotRodGlobalLockProviderFactory;
import org.keycloak.models.map.user.MapUserProviderFactory;
import org.keycloak.models.map.userSession.MapUserSessionProviderFactory;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;
import org.keycloak.sessions.AuthenticationSessionSpi;
import org.keycloak.testsuite.model.Config;
import org.keycloak.testsuite.model.KeycloakModelParameters;
import org.keycloak.testsuite.util.InfinispanContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.keycloak.testsuite.model.transaction.StorageTransactionTest.LOCK_TIMEOUT_SYSTEM_PROPERTY;

/**
 *
 * @author hmlnarik
 */
public class HotRodMapStorage extends KeycloakModelParameters {

    private final Logger LOG = Logger.getLogger(getClass());
    public static final Boolean START_CONTAINER = Boolean.valueOf(System.getProperty("keycloak.testsuite.start-hotrod-container", "true"));
    static final Set<Class<? extends Spi>> ALLOWED_SPIS = ImmutableSet.<Class<? extends Spi>>builder()
      .add(HotRodConnectionSpi.class)
      .build();

    static final Set<Class<? extends ProviderFactory>> ALLOWED_FACTORIES = ImmutableSet.<Class<? extends ProviderFactory>>builder()
      .add(HotRodMapStorageProviderFactory.class)
      .add(HotRodConnectionProviderFactory.class)
      .add(HotRodGlobalLockProviderFactory.class)
      .build();
    
    private final InfinispanContainer hotRodContainer = new InfinispanContainer();

    @Override
    public void updateConfig(Config cf) {
        cf.spi(AuthenticationSessionSpi.PROVIDER_ID).provider(MapRootAuthenticationSessionProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi(SingleUseObjectSpi.NAME).provider(MapSingleUseObjectProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi("publicKeyStorage").provider(MapPublicKeyStorageProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
          .spi("client").provider(MapClientProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi("clientScope").provider(MapClientScopeProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi("group").provider(MapGroupProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi("realm").provider(MapRealmProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi("role").provider(MapRoleProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi(DeploymentStateSpi.NAME).provider(MapDeploymentStateProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
          .spi(StoreFactorySpi.NAME).provider(MapAuthorizationStoreFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi("user").provider(MapUserProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi(UserSessionSpi.NAME).provider(MapUserSessionProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi(UserLoginFailureSpi.NAME).provider(MapUserLoginFailureProviderFactory.PROVIDER_ID).config(STORAGE_CONFIG, HotRodMapStorageProviderFactory.PROVIDER_ID)
          .spi(EventStoreSpi.NAME).provider(MapUserSessionProviderFactory.PROVIDER_ID).config("storage-admin-events.provider", HotRodMapStorageProviderFactory.PROVIDER_ID)
                                                                                       .config("storage-auth-events.provider", HotRodMapStorageProviderFactory.PROVIDER_ID)
            .spi(GlobalLockProviderSpi.GLOBAL_LOCK).defaultProvider(HotRodGlobalLockProviderFactory.PROVIDER_ID);

        cf.spi(MapStorageSpi.NAME)
                .provider(ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .config("dir", "${project.build.directory:target}")
                .config("keyType.single-use-objects", "string");

        cf.spi(HotRodConnectionSpi.NAME).provider(DefaultHotRodConnectionProviderFactory.PROVIDER_ID)
                .config("host", hotRodContainer.getHost())
                .config("port", hotRodContainer.getPort())
                .config("username", hotRodContainer.getUsername())
                .config("password", hotRodContainer.getPassword())
                .config("configureRemoteCaches", "true")
                .config("lockTimeout", "${" + LOCK_TIMEOUT_SYSTEM_PROPERTY + ":}");
    }

    @Override
    public void beforeSuite(Config cf) {
        if (START_CONTAINER) {
            hotRodContainer.start();
        }
    }

    @Override
    public void afterSuite() {
        if (START_CONTAINER) {
            hotRodContainer.stop();
        }
    }

    public HotRodMapStorage() {
        super(ALLOWED_SPIS, ALLOWED_FACTORIES);
    }

}
