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

package org.keycloak.models.jpa;

import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmProviderFactory;

import javax.persistence.EntityManager;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleContainerModel;
import org.keycloak.models.RoleContainerModel.RoleRemovedEvent;
import org.keycloak.models.RoleModel;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class JpaRealmProviderFactory implements RealmProviderFactory, ProviderEventListener {

    private Runnable onClose;

    public static final String PROVIDER_ID = "jpa";
    public static final int PROVIDER_PRIORITY = 1;

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(this);
        onClose = () -> factory.unregister(this);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public JpaRealmProvider create(KeycloakSession session) {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        return new JpaRealmProvider(session, em, null, null);
    }

    @Override
    public void close() {
        onClose.run();
    }

    @Override
    public void onEvent(ProviderEvent event) {
        if (event instanceof RoleContainerModel.RoleRemovedEvent) {
            RoleRemovedEvent e = (RoleContainerModel.RoleRemovedEvent) event;
            RoleModel role = e.getRole();
            RoleContainerModel container = role.getContainer();
            RealmModel realm;
            if (container instanceof RealmModel) {
                realm = (RealmModel) container;
            } else if (container instanceof ClientModel) {
                realm = ((ClientModel) container).getRealm();
            } else {
                return;
            }
            ((JpaRealmProvider) e.getKeycloakSession().getProvider(RealmProvider.class)).preRemove(realm, role);
        }
    }

    @Override
    public int order() {
        return PROVIDER_PRIORITY;
    }

}
