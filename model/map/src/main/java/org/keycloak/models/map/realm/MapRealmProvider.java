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

package org.keycloak.models.map.realm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmModel.SearchableFields;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleModel;
import org.keycloak.models.map.common.DeepCloner;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.ModelCriteriaBuilder.Operator;
import org.keycloak.models.map.storage.criteria.DefaultModelCriteria;
import org.keycloak.models.utils.KeycloakModelUtils;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.REALM_AFTER_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.REALM_BEFORE_REMOVE;
import static org.keycloak.models.map.storage.QueryParameters.Order.ASCENDING;
import static org.keycloak.models.map.storage.QueryParameters.withCriteria;
import static org.keycloak.models.map.storage.criteria.DefaultModelCriteria.criteria;

public class MapRealmProvider implements RealmProvider {

    private static final Logger LOG = Logger.getLogger(MapRealmProvider.class);
    private final KeycloakSession session;
    final MapKeycloakTransaction<MapRealmEntity, RealmModel> tx;

    public MapRealmProvider(KeycloakSession session, MapStorage<MapRealmEntity, RealmModel> realmStore) {
        this.session = session;
        this.tx = realmStore.createTransaction(session);
        session.getTransactionManager().enlist(tx);
    }

    private RealmModel entityToAdapter(MapRealmEntity entity) {
        return new MapRealmAdapter(session, entity);
    }

    @Override
    public RealmModel createRealm(String name) {
        return createRealm(KeycloakModelUtils.generateId(), name);
    }

    @Override
    public RealmModel createRealm(String id, String name) {
        if (getRealmByName(name) != null) {
            throw new ModelDuplicateException("Realm with given name exists: " + name);
        }

        if (id != null && tx.exists(id)) {
            throw new ModelDuplicateException("Realm exists: " + id);
        }

        LOG.tracef("createRealm(%s, %s)%s", id, name, getShortStackTrace());

        MapRealmEntity entity = DeepCloner.DUMB_CLONER.newInstance(MapRealmEntity.class);
        entity.setId(id);
        entity.setName(name);

        entity = tx.create(entity);
        return entityToAdapter(entity);
    }

    @Override
    public RealmModel getRealm(String id) {
        if (id == null) return null;

        LOG.tracef("getRealm(%s)%s", id, getShortStackTrace());

        MapRealmEntity entity = tx.read(id);
        return entity == null ? null : entityToAdapter(entity);
    }

    @Override
    public RealmModel getRealmByName(String name) {
        if (name == null) return null;

        LOG.tracef("getRealmByName(%s)%s", name, getShortStackTrace());

        DefaultModelCriteria<RealmModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.NAME, Operator.EQ, name);

        String realmId = tx.read(withCriteria(mcb))
                .findFirst()
                .map(MapRealmEntity::getId)
                .orElse(null);
        //we need to go via session.realms() not to bypass cache
        return realmId == null ? null : session.realms().getRealm(realmId);
    }

    @Override
    public Stream<RealmModel> getRealmsStream() {
        return getRealmsStream(criteria());
    }

    @Override
    public Stream<RealmModel> getRealmsWithProviderTypeStream(Class<?> type) {
        DefaultModelCriteria<RealmModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.COMPONENT_PROVIDER_TYPE, Operator.EQ, type.getName());

        return getRealmsStream(mcb);
    }

    private Stream<RealmModel> getRealmsStream(DefaultModelCriteria<RealmModel> mcb) {
        return tx.read(withCriteria(mcb).orderBy(SearchableFields.NAME, ASCENDING))
                .map(this::entityToAdapter);
    }

    @Override
    public boolean removeRealm(String id) {
        LOG.tracef("removeRealm(%s)%s", id, getShortStackTrace());

        RealmModel realm = getRealm(id);

        if (realm == null) return false;

        session.invalidate(REALM_BEFORE_REMOVE, realm);

        tx.delete(id);

        session.invalidate(REALM_AFTER_REMOVE, realm);

        return true;
    }

    @Override
    public void removeExpiredClientInitialAccess() {
        DefaultModelCriteria<RealmModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.CLIENT_INITIAL_ACCESS, Operator.EXISTS);

        tx.read(withCriteria(mcb))
                .forEach(MapRealmEntity::removeExpiredClientInitialAccesses);
    }

    //TODO move the following method to adapter
    @Override
    public void saveLocalizationText(RealmModel realm, String locale, String key, String text) {
        if (locale == null || key == null || text == null) return;
        Map<String, String> texts = new HashMap<>();
        texts.put(key, text);
        realm.createOrUpdateRealmLocalizationTexts(locale, texts);
    }

    //TODO move the following method to adapter
    @Override
    public void saveLocalizationTexts(RealmModel realm, String locale, Map<String, String> localizationTexts) {
        if (locale == null || localizationTexts == null) return;
        realm.createOrUpdateRealmLocalizationTexts(locale, localizationTexts);
    }

    //TODO move the following method to adapter
    @Override
    public boolean updateLocalizationText(RealmModel realm, String locale, String key, String text) {
        if (locale == null || key == null || text == null || (! realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return false;
        saveLocalizationText(realm, locale, key, text);
        return true;
    }

    //TODO move the following method to adapter
    @Override
    public boolean deleteLocalizationTextsByLocale(RealmModel realm, String locale) {
        return realm.removeRealmLocalizationTexts(locale);
    }

    //TODO move the following method to adapter
    @Override
    public boolean deleteLocalizationText(RealmModel realm, String locale, String key) {
        if (locale == null || key == null || (! realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return false;

        Map<String, String> texts = new HashMap<>(realm.getRealmLocalizationTextsByLocale(locale));
        texts.remove(key);
        realm.removeRealmLocalizationTexts(locale);
        realm.createOrUpdateRealmLocalizationTexts(locale, texts);
        return true;
    }

    //TODO move the following method to adapter
    @Override
    public String getLocalizationTextsById(RealmModel realm, String locale, String key) {
        if (locale == null || key == null || (! realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return null;
        return realm.getRealmLocalizationTextsByLocale(locale).get(key);
    }

    @Override
    @Deprecated
    public ClientModel addClient(RealmModel realm, String id, String clientId) {
        return session.clients().addClient(realm, id, clientId);
    }

    @Override
    @Deprecated
    public long getClientsCount(RealmModel realm) {
        return session.clients().getClientsCount(realm);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> getClientsStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        return session.clients().getClientsStream(realm, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> getAlwaysDisplayInConsoleClientsStream(RealmModel realm) {
        return session.clients().getAlwaysDisplayInConsoleClientsStream(realm);
    }

    @Override
    @Deprecated
    public boolean removeClient(RealmModel realm, String id) {
        return session.clients().removeClient(realm, id);
    }

    @Override
    @Deprecated
    public void removeClients(RealmModel realm) {
        session.clients().removeClients(realm);
    }

    @Override
    @Deprecated
    public ClientModel getClientById(RealmModel realm, String id) {
        return session.clients().getClientById(realm, id);
    }

    @Override
    @Deprecated
    public ClientModel getClientByClientId(RealmModel realm, String clientId) {
        return session.clients().getClientByClientId(realm, clientId);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> searchClientsByClientIdStream(RealmModel realm, String clientId, Integer firstResult, Integer maxResults) {
        return session.clients().searchClientsByClientIdStream(realm, clientId, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> searchClientsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        return session.clients().searchClientsByAttributes(realm, attributes, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public void addClientScopes(RealmModel realm, ClientModel client, Set<ClientScopeModel> clientScopes, boolean defaultScope) {
        session.clients().addClientScopes(realm, client, clientScopes, defaultScope);
    }

    @Override
    @Deprecated
    public void removeClientScope(RealmModel realm, ClientModel client, ClientScopeModel clientScope) {
        session.clients().removeClientScope(realm, client, clientScope);
    }

    @Override
    @Deprecated
    public Map<String, ClientScopeModel> getClientScopes(RealmModel realm, ClientModel client, boolean defaultScopes) {
        return session.clients().getClientScopes(realm, client, defaultScopes);
    }

    @Override
    @Deprecated
    public ClientScopeModel getClientScopeById(RealmModel realm, String id) {
        return session.clientScopes().getClientScopeById(realm, id);
    }

    @Override
    @Deprecated
    public Stream<ClientScopeModel> getClientScopesStream(RealmModel realm) {
        return session.clientScopes().getClientScopesStream(realm);
    }

    @Override
    @Deprecated
    public ClientScopeModel addClientScope(RealmModel realm, String id, String name) {
        return session.clientScopes().addClientScope(realm, id, name);
    }

    @Override
    @Deprecated
    public boolean removeClientScope(RealmModel realm, String id) {
        return session.clientScopes().removeClientScope(realm, id);
    }

    @Override
    @Deprecated
    public void removeClientScopes(RealmModel realm) {
        session.clientScopes().removeClientScopes(realm);
    }

    @Override
    @Deprecated
    public Map<ClientModel, Set<String>> getAllRedirectUrisOfEnabledClients(RealmModel realm) {
        return session.clients().getAllRedirectUrisOfEnabledClients(realm);
    }

    @Override
    @Deprecated
    public void moveGroup(RealmModel realm, GroupModel group, GroupModel toParent) {
        session.groups().moveGroup(realm, group, toParent);
    }

    @Override
    @Deprecated
    public GroupModel getGroupById(RealmModel realm, String id) {
        return session.groups().getGroupById(realm, id);
    }

    @Override
    @Deprecated
    public Long getGroupsCount(RealmModel realm, Boolean onlyTopGroups) {
        return session.groups().getGroupsCount(realm, onlyTopGroups);
    }

    @Override
    @Deprecated
    public Long getGroupsCountByNameContaining(RealmModel realm, String search) {
        return session.groups().getGroupsCountByNameContaining(realm, search);
    }

    @Override
    @Deprecated
    public boolean removeGroup(RealmModel realm, GroupModel group) {
        return session.groups().removeGroup(realm, group);
    }

    @Override
    @Deprecated
    public GroupModel createGroup(RealmModel realm, String id, String name, GroupModel toParent) {
        return session.groups().createGroup(realm, id, name, toParent);
    }

    @Override
    @Deprecated
    public void addTopLevelGroup(RealmModel realm, GroupModel subGroup) {
        session.groups().addTopLevelGroup(realm, subGroup);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getGroupsStream(RealmModel realm) {
        return session.groups().getGroupsStream(realm);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getGroupsStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        return session.groups().getGroupsStream(realm,ids, search, first, max);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getGroupsByRoleStream(RealmModel realm, RoleModel role, Integer firstResult, Integer maxResults) {
        return session.groups().getGroupsByRoleStream(realm, role, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getTopLevelGroupsStream(RealmModel realm) {
        return session.groups().getTopLevelGroupsStream(realm);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getTopLevelGroupsStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        return session.groups().getTopLevelGroupsStream(realm, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> searchForGroupByNameStream(RealmModel realm, String search, Boolean exact, Integer firstResult, Integer maxResults) {
        return session.groups().searchForGroupByNameStream(realm, search, exact, firstResult, maxResults);
    }

    @Override
    public Stream<GroupModel> searchGroupsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        return session.groups().searchGroupsByAttributes(realm, attributes, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public RoleModel addRealmRole(RealmModel realm, String id, String name) {
        return session.roles().addRealmRole(realm, id, name);
    }

    @Override
    @Deprecated
    public RoleModel getRealmRole(RealmModel realm, String name) {
        return session.roles().getRealmRole(realm, name);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm, Integer first, Integer max) {
        return session.roles().getRealmRolesStream(realm, first, max);
    }

    @Override
    public Stream<RoleModel> getRolesStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        return session.roles().getRolesStream(realm, ids, search, first, max);
    }

    @Override
    @Deprecated
    public boolean removeRole(RoleModel role) {
        return session.roles().removeRole(role);
    }

    @Override
    @Deprecated
    public void removeRoles(RealmModel realm) {
        session.roles().removeRoles(realm);
    }

    @Override
    @Deprecated
    public RoleModel addClientRole(ClientModel client, String id, String name) {
        return session.roles().addClientRole(client, name);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> getClientRolesStream(ClientModel client, Integer first, Integer max) {
        return session.roles().getClientRolesStream(client, first, max);
    }

    @Override
    @Deprecated
    public void removeRoles(ClientModel client) {
        session.roles().removeRoles(client);
    }

    @Override
    @Deprecated
    public RoleModel getRoleById(RealmModel realm, String id) {
        return session.roles().getRoleById(realm, id);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> searchForRolesStream(RealmModel realm, String search, Integer first, Integer max) {
        return session.roles().searchForRolesStream(realm, search, first, max);
    }

    @Override
    @Deprecated
    public RoleModel getClientRole(ClientModel client, String name) {
        return session.roles().getClientRole(client, name);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> searchForClientRolesStream(ClientModel client, String search, Integer first, Integer max) {
        return session.roles().searchForClientRolesStream(client, search, first, max);
    }

    @Override
    public void close() {
    }
}
