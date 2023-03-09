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

import org.keycloak.authorization.store.StoreFactorySpi;
import org.keycloak.events.EventStoreSpi;
import org.keycloak.exportimport.ExportSpi;
import org.keycloak.exportimport.dir.DirExportProviderFactory;
import org.keycloak.exportimport.singlefile.SingleFileExportProviderFactory;
import org.keycloak.keys.PublicKeyStorageSpi;
import org.keycloak.models.DeploymentStateSpi;
import org.keycloak.models.SingleUseObjectProviderFactory;
import org.keycloak.models.SingleUseObjectSpi;
import org.keycloak.models.UserLoginFailureSpi;
import org.keycloak.models.UserSessionSpi;
import org.keycloak.models.locking.GlobalLockProviderSpi;
import org.keycloak.models.locking.NoneGlobalLockProviderFactory;
import org.keycloak.models.map.authSession.MapRootAuthenticationSessionProviderFactory;
import org.keycloak.models.map.authorization.MapAuthorizationStoreFactory;
import org.keycloak.models.map.events.MapEventStoreProviderFactory;
import org.keycloak.models.map.keys.MapPublicKeyStorageProviderFactory;
import org.keycloak.models.map.loginFailure.MapUserLoginFailureProviderFactory;
import org.keycloak.models.map.singleUseObject.MapSingleUseObjectProviderFactory;
import org.keycloak.models.map.storage.chm.ConcurrentHashMapStorageProviderFactory;
import org.keycloak.models.map.userSession.MapUserSessionProviderFactory;
import org.keycloak.services.clientpolicy.ClientPolicyManagerFactory;
import org.keycloak.services.clientpolicy.ClientPolicyManagerSpi;
import org.keycloak.sessions.AuthenticationSessionSpi;
import org.keycloak.testsuite.model.KeycloakModelParameters;
import org.keycloak.models.map.client.MapClientProviderFactory;
import org.keycloak.models.map.clientscope.MapClientScopeProviderFactory;
import org.keycloak.models.map.group.MapGroupProviderFactory;
import org.keycloak.models.map.realm.MapRealmProviderFactory;
import org.keycloak.models.map.role.MapRoleProviderFactory;
import org.keycloak.models.map.deploymentState.MapDeploymentStateProviderFactory;
import org.keycloak.models.map.storage.MapStorageSpi;
import org.keycloak.models.map.user.MapUserProviderFactory;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;
import org.keycloak.testsuite.model.Config;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 *
 * @author hmlnarik
 */
public class Map extends KeycloakModelParameters {

    static final Set<Class<? extends Spi>> ALLOWED_SPIS = ImmutableSet.<Class<? extends Spi>>builder()
      .add(AuthenticationSessionSpi.class)
      .add(SingleUseObjectSpi.class)
      .add(PublicKeyStorageSpi.class)
      .add(MapStorageSpi.class)

      .build();

    static final Set<Class<? extends ProviderFactory>> ALLOWED_FACTORIES = ImmutableSet.<Class<? extends ProviderFactory>>builder()
      .add(MapAuthorizationStoreFactory.class)
      .add(MapClientProviderFactory.class)
      .add(MapClientScopeProviderFactory.class)
      .add(MapGroupProviderFactory.class)
      .add(MapRealmProviderFactory.class)
      .add(MapRoleProviderFactory.class)
      .add(MapRootAuthenticationSessionProviderFactory.class)
      .add(MapDeploymentStateProviderFactory.class)
      .add(MapUserProviderFactory.class)
      .add(MapUserSessionProviderFactory.class)
      .add(MapUserLoginFailureProviderFactory.class)
      .add(NoneGlobalLockProviderFactory.class)
      .add(MapEventStoreProviderFactory.class)
      .add(SingleUseObjectProviderFactory.class)
      .add(MapPublicKeyStorageProviderFactory.class)
      .build();

    public Map() {
        super(ALLOWED_SPIS, ALLOWED_FACTORIES);
    }

    @Override
    public void updateConfig(Config cf) {
        cf.spi(AuthenticationSessionSpi.PROVIDER_ID).defaultProvider(MapRootAuthenticationSessionProviderFactory.PROVIDER_ID)
          .spi(SingleUseObjectSpi.NAME).defaultProvider(MapSingleUseObjectProviderFactory.PROVIDER_ID)
          .spi("client").defaultProvider(MapClientProviderFactory.PROVIDER_ID)
          .spi("clientScope").defaultProvider(MapClientScopeProviderFactory.PROVIDER_ID)
          .spi("group").defaultProvider(MapGroupProviderFactory.PROVIDER_ID)
          .spi("realm").defaultProvider(MapRealmProviderFactory.PROVIDER_ID)
          .spi("role").defaultProvider(MapRoleProviderFactory.PROVIDER_ID)
          .spi(DeploymentStateSpi.NAME).defaultProvider(MapDeploymentStateProviderFactory.PROVIDER_ID)
          .spi(StoreFactorySpi.NAME).defaultProvider(MapAuthorizationStoreFactory.PROVIDER_ID)
          .spi("user").defaultProvider(MapUserProviderFactory.PROVIDER_ID)
          .spi(UserSessionSpi.NAME).defaultProvider(MapUserSessionProviderFactory.PROVIDER_ID)
          .spi(UserLoginFailureSpi.NAME).defaultProvider(MapUserLoginFailureProviderFactory.PROVIDER_ID)
          .spi(GlobalLockProviderSpi.GLOBAL_LOCK).defaultProvider(NoneGlobalLockProviderFactory.PROVIDER_ID)
          .spi(EventStoreSpi.NAME).defaultProvider(MapEventStoreProviderFactory.PROVIDER_ID)
          .spi("publicKeyStorage").defaultProvider(MapPublicKeyStorageProviderFactory.PROVIDER_ID)
        ;
        cf.spi(MapStorageSpi.NAME).provider(ConcurrentHashMapStorageProviderFactory.PROVIDER_ID).config("keyType.single-use-objects", "string");
    }
}
