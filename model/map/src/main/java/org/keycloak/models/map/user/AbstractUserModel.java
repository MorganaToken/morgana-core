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

package org.keycloak.models.map.user;

import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.map.common.AbstractEntity;

import java.util.Objects;

public abstract class AbstractUserModel<E extends AbstractEntity> implements UserModel {

    protected final KeycloakSession session;
    protected final RealmModel realm;
    protected final E entity;

    public AbstractUserModel(KeycloakSession session, RealmModel realm, E entity) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(realm, "realm");

        this.session = session;
        this.realm = realm;
        this.entity = entity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserModel)) return false;

        UserModel that = (UserModel) o;
        return Objects.equals(that.getId(), getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
