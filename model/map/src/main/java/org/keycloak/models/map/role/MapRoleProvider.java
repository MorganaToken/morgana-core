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

package org.keycloak.models.map.role;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.RoleModel.SearchableFields;
import org.keycloak.models.RoleProvider;
import org.keycloak.models.map.common.DeepCloner;
import org.keycloak.models.map.common.HasRealmId;
import org.keycloak.models.map.storage.ModelCriteriaBuilder.Operator;
import org.keycloak.models.map.storage.criteria.DefaultModelCriteria;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.ROLE_AFTER_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.ROLE_BEFORE_REMOVE;
import static org.keycloak.models.map.storage.QueryParameters.Order.ASCENDING;
import static org.keycloak.models.map.storage.QueryParameters.withCriteria;
import static org.keycloak.models.map.storage.criteria.DefaultModelCriteria.criteria;

public class MapRoleProvider implements RoleProvider {

    private static final Logger LOG = Logger.getLogger(MapRoleProvider.class);
    private final KeycloakSession session;
    final MapKeycloakTransaction<MapRoleEntity, RoleModel> tx;
    private final boolean txHasRealmId;

    public MapRoleProvider(KeycloakSession session, MapStorage<MapRoleEntity, RoleModel> roleStore) {
        this.session = session;
        this.tx = roleStore.createTransaction(session);
        session.getTransactionManager().enlist(tx);
        this.txHasRealmId = tx instanceof HasRealmId;
    }

    private Function<MapRoleEntity, RoleModel> entityToAdapterFunc(RealmModel realm) {
        // Clone entity before returning back, to avoid giving away a reference to the live object to the caller
        return origEntity -> new MapRoleAdapter(session, realm, origEntity);
    }

    private MapKeycloakTransaction<MapRoleEntity, RoleModel> txInRealm(RealmModel realm) {
        if (txHasRealmId) {
            ((HasRealmId) tx).setRealmId(realm == null ? null : realm.getId());
        }
        return tx;
    }

    @Override
    public RoleModel addRealmRole(RealmModel realm, String id, String name) {
        if (getRealmRole(realm, name) != null) {
            throw new ModelDuplicateException("Role with the same name exists: " + name + " for realm " + realm.getName());
        }

        LOG.tracef("addRealmRole(%s, %s, %s)%s", realm, id, name, getShortStackTrace());

        MapRoleEntity entity = DeepCloner.DUMB_CLONER.newInstance(MapRoleEntity.class);
        entity.setId(id);
        entity.setRealmId(realm.getId());
        entity.setName(name);
        if (entity.getId() != null && txInRealm(realm).exists(entity.getId())) {
            throw new ModelDuplicateException("Role exists: " + id);
        }
        entity = txInRealm(realm).create(entity);
        return entityToAdapterFunc(realm).apply(entity);
    }

    @Override
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm, Integer first, Integer max) {
        DefaultModelCriteria<RoleModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                // filter realm roles only
                 .compare(SearchableFields.CLIENT_ID, Operator.NOT_EXISTS);

        return txInRealm(realm).read(withCriteria(mcb).pagination(first, max, SearchableFields.NAME))
            .map(entityToAdapterFunc(realm));
    }

    @Override
    public Stream<RoleModel> getRolesStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        LOG.tracef("getRolesStream(%s, %s, %s, %d, %d)%s", realm, ids, search, first, max, getShortStackTrace());
        if (ids == null) return Stream.empty();

        DefaultModelCriteria<RoleModel> mcb = criteria();
        mcb = mcb.compare(RoleModel.SearchableFields.ID, Operator.IN, ids)
                .compare(RoleModel.SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        if (search != null) {
            mcb = mcb.compare(RoleModel.SearchableFields.NAME, Operator.ILIKE, "%" + search + "%");
        }

        return txInRealm(realm).read(withCriteria(mcb).pagination(first, max, RoleModel.SearchableFields.NAME))
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm) {
        DefaultModelCriteria<RoleModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                // filter realm roles only
                 .compare(SearchableFields.CLIENT_ID, Operator.NOT_EXISTS);
        
        return txInRealm(realm).read(withCriteria(mcb).orderBy(SearchableFields.NAME, ASCENDING))
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public RoleModel addClientRole(ClientModel client, String id, String name) {
        if (getClientRole(client, name) != null) {
            throw new ModelDuplicateException("Role with the same name exists: " + name + " for client " + client.getClientId());
        }

        LOG.tracef("addClientRole(%s, %s, %s)%s", client, id, name, getShortStackTrace());

        MapRoleEntity entity = DeepCloner.DUMB_CLONER.newInstance(MapRoleEntity.class);
        final RealmModel realm = client.getRealm();
        entity.setId(id);
        entity.setRealmId(realm.getId());
        entity.setName(name);
        entity.setClientId(client.getId());
        if (entity.getId() != null && txInRealm(realm).exists(entity.getId())) {
            throw new ModelDuplicateException("Role exists: " + id);
        }
        entity = txInRealm(realm).create(entity);
        return entityToAdapterFunc(realm).apply(entity);
    }

    @Override
    public Stream<RoleModel> getClientRolesStream(ClientModel client, Integer first, Integer max) {
        DefaultModelCriteria<RoleModel> mcb = criteria();
        final RealmModel realm = client.getRealm();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                .compare(SearchableFields.CLIENT_ID, Operator.EQ, client.getId());

        return txInRealm(realm).read(withCriteria(mcb).pagination(first, max, SearchableFields.NAME))
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public Stream<RoleModel> getClientRolesStream(ClientModel client) {
        DefaultModelCriteria<RoleModel> mcb = criteria();
        final RealmModel realm = client.getRealm();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
          .compare(SearchableFields.CLIENT_ID, Operator.EQ, client.getId());

        return txInRealm(realm).read(withCriteria(mcb).orderBy(SearchableFields.NAME, ASCENDING))
                .map(entityToAdapterFunc(realm));
    }
    @Override
    public boolean removeRole(RoleModel role) {
        LOG.tracef("removeRole(%s)%s", role, getShortStackTrace());

        RealmModel realm = role.isClientRole() ? ((ClientModel)role.getContainer()).getRealm() : (RealmModel)role.getContainer();

        session.invalidate(ROLE_BEFORE_REMOVE, realm, role);

        txInRealm(realm).delete(role.getId());

        session.invalidate(ROLE_AFTER_REMOVE, realm, role);

        return true;
    }

    @Override
    public void removeRoles(RealmModel realm) {
        getRealmRolesStream(realm).forEach(this::removeRole);
    }

    @Override
    public void removeRoles(ClientModel client) {
        getClientRolesStream(client).forEach(this::removeRole);
    }

    @Override
    public RoleModel getRealmRole(RealmModel realm, String name) {
        if (name == null) {
            return null;
        }
        LOG.tracef("getRealmRole(%s, %s)%s", realm, name, getShortStackTrace());

        DefaultModelCriteria<RoleModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                // filter realm roles only
                 .compare(SearchableFields.CLIENT_ID, Operator.NOT_EXISTS)
                 .compare(SearchableFields.NAME, Operator.EQ, name);

        return txInRealm(realm).read(withCriteria(mcb))
                .map(entityToAdapterFunc(realm))
                .findFirst()
                .orElse(null);
    }

    @Override
    public RoleModel getClientRole(ClientModel client, String name) {
        if (name == null) {
            return null;
        }
        LOG.tracef("getClientRole(%s, %s)%s", client, name, getShortStackTrace());

        DefaultModelCriteria<RoleModel> mcb = criteria();
        final RealmModel realm = client.getRealm();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
          .compare(SearchableFields.CLIENT_ID, Operator.EQ, client.getId())
          .compare(SearchableFields.NAME, Operator.EQ, name);

        return txInRealm(realm).read(withCriteria(mcb))
                .map(entityToAdapterFunc(realm))
                .findFirst()
                .orElse(null);
    }

    @Override
    public RoleModel getRoleById(RealmModel realm, String id) {
        if (id == null || realm == null || realm.getId() == null) {
            return null;
        }

        LOG.tracef("getRoleById(%s, %s)%s", realm, id, getShortStackTrace());

        MapRoleEntity entity = txInRealm(realm).read(id);
        String realmId = realm.getId();
        // when a store doesn't store information about all realms, it doesn't have the information about
        return (entity == null || (entity.getRealmId() != null && !Objects.equals(realmId, entity.getRealmId())))
          ? null
          : entityToAdapterFunc(realm).apply(entity);
    }

    @Override
    public Stream<RoleModel> searchForRolesStream(RealmModel realm, String search, Integer first, Integer max) {
        if (search == null) {
            return Stream.empty();
        }
        DefaultModelCriteria<RoleModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                // filter realm roles only
                .compare(SearchableFields.CLIENT_ID, Operator.NOT_EXISTS)
                .or(
                        mcb.compare(SearchableFields.NAME, Operator.ILIKE, "%" + search + "%"),
                        mcb.compare(SearchableFields.DESCRIPTION, Operator.ILIKE, "%" + search + "%")
                );

        return txInRealm(realm).read(withCriteria(mcb).pagination(first, max, SearchableFields.NAME))
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public Stream<RoleModel> searchForClientRolesStream(ClientModel client, String search, Integer first, Integer max) {
        if (search == null) {
            return Stream.empty();
        }
        DefaultModelCriteria<RoleModel> mcb = criteria();
        final RealmModel realm = client.getRealm();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                .compare(SearchableFields.CLIENT_ID, Operator.EQ, client.getId())
                .or(
                        mcb.compare(SearchableFields.NAME, Operator.ILIKE, "%" + search + "%"),
                        mcb.compare(SearchableFields.DESCRIPTION, Operator.ILIKE, "%" + search + "%")
                );

        return txInRealm(realm).read(withCriteria(mcb).pagination(first, max, SearchableFields.NAME))
                .map(entityToAdapterFunc(realm));
    }

    public void preRemove(RealmModel realm) {
        LOG.tracef("preRemove(%s)%s", realm, getShortStackTrace());
        DefaultModelCriteria<RoleModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        txInRealm(realm).delete(withCriteria(mcb));
    }

    public void preRemove(RealmModel realm, RoleModel role) {
        // Remove reference from all composite roles
        DefaultModelCriteria<RoleModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                .compare(SearchableFields.COMPOSITE_ROLE, Operator.EQ, role.getId());
        txInRealm(realm).read(withCriteria(mcb)).forEach(mapRoleEntity -> mapRoleEntity.removeCompositeRole(role.getId()));
    }

    @Override
    public void close() {
    }
}
