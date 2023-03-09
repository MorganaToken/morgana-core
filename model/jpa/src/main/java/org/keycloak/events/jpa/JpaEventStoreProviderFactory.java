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

package org.keycloak.events.jpa;

import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.EventStoreProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.InvalidationHandler;
import org.keycloak.storage.datastore.PeriodicEventInvalidation;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class JpaEventStoreProviderFactory implements EventStoreProviderFactory, InvalidationHandler {

    public static final String ID = "jpa";
    private int maxDetailLength;
    private int maxFieldLength;

    @Override
    public EventStoreProvider create(KeycloakSession session) {
        JpaConnectionProvider connection = session.getProvider(JpaConnectionProvider.class);
        return new JpaEventStoreProvider(session, connection.getEntityManager(), maxDetailLength, maxFieldLength);
    }

    @Override
    public void init(Config.Scope config) {
        maxDetailLength = config.getInt("max-detail-length", -1);
        maxFieldLength = config.getInt("max-field-length", -1);
        if (maxDetailLength != -1 && maxDetailLength < 3) throw new IllegalArgumentException("max-detail-length cannot be less that 3.");
        if (maxFieldLength != -1 && maxFieldLength < 3) throw new IllegalArgumentException("max-field-length cannot be less that 3.");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void invalidate(KeycloakSession session, InvalidableObjectType type, Object... params) {
        if(type == PeriodicEventInvalidation.JPA_EVENT_STORE) {
            ((JpaEventStoreProvider) session.getProvider(EventStoreProvider.class)).clearExpiredAdminEvents();
        }
    }
}
