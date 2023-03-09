/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.broker.provider;

import org.jboss.logging.Logger;
import org.keycloak.broker.provider.mappersync.ConfigSyncEventListener;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class AbstractIdentityProviderMapper implements IdentityProviderMapper {

    private static final Logger LOG = Logger.getLogger(AbstractIdentityProviderMapper.class);

    private static volatile KeycloakSessionFactory keycloakSessionFactory;

    @Override
    public void close() {

    }

    @Override
    public IdentityProviderMapper create(KeycloakSession session) {
        return null;
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        registerConfigSyncEventListenerOnce(factory);
    }

    private void registerConfigSyncEventListenerOnce(KeycloakSessionFactory factory) {
        /*
         * Make sure that the config sync listener is registered only once for a session factory. It would also be
         * possible to register it only once per VM, but that does not work fine in integration tests.
         */
        if (keycloakSessionFactory != factory) {
            synchronized (AbstractIdentityProviderMapper.class) {
                if (keycloakSessionFactory != factory) {
                    keycloakSessionFactory = factory;

                    LOG.infof("Registering %s", ConfigSyncEventListener.class);
                    factory.register(new ConfigSyncEventListener());
                }
            }
        }
    }

    @Override
    public void preprocessFederatedIdentity(KeycloakSession session, RealmModel realm, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

    }

    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

    }

    @Override
    public void updateBrokeredUserLegacy(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateBrokeredUser(session, realm, user, mapperModel, context);
    }
}
