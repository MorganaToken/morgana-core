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

package org.keycloak.models.map.group;

import org.jboss.logging.Logger;
import org.keycloak.models.GroupModel;
import org.keycloak.models.GroupModel.SearchableFields;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.map.common.DeepCloner;
import org.keycloak.models.map.common.HasRealmId;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.MapStorage;

import org.keycloak.models.map.storage.ModelCriteriaBuilder.Operator;
import org.keycloak.models.map.storage.QueryParameters;

import org.keycloak.models.map.storage.criteria.DefaultModelCriteria;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.GROUP_AFTER_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.GROUP_BEFORE_REMOVE;
import static org.keycloak.models.map.storage.QueryParameters.Order.ASCENDING;
import static org.keycloak.models.map.storage.QueryParameters.withCriteria;
import static org.keycloak.models.map.storage.criteria.DefaultModelCriteria.criteria;

public class MapGroupProvider implements GroupProvider {

    private static final Logger LOG = Logger.getLogger(MapGroupProvider.class);
    private final KeycloakSession session;
    final MapKeycloakTransaction<MapGroupEntity, GroupModel> tx;
    private final boolean txHasRealmId;

    public MapGroupProvider(KeycloakSession session, MapStorage<MapGroupEntity, GroupModel> groupStore) {
        this.session = session;
        this.tx = groupStore.createTransaction(session);
        session.getTransactionManager().enlist(tx);
        this.txHasRealmId = tx instanceof HasRealmId;
    }

    private MapKeycloakTransaction<MapGroupEntity, GroupModel> txInRealm(RealmModel realm) {
        if (txHasRealmId) {
            ((HasRealmId) tx).setRealmId(realm == null ? null : realm.getId());
        }
        return tx;
    }

    private Function<MapGroupEntity, GroupModel> entityToAdapterFunc(RealmModel realm) {
        // Clone entity before returning back, to avoid giving away a reference to the live object to the caller
        return origEntity -> new MapGroupAdapter(session, realm, origEntity) {
            @Override
            public Stream<GroupModel> getSubGroupsStream() {
                return getGroupsByParentId(realm, this.getId());
            }
        };
    }

    @Override
    public GroupModel getGroupById(RealmModel realm, String id) {
        if (id == null || realm == null) {
            return null;
        }

        LOG.tracef("getGroupById(%s, %s)%s", realm, id, getShortStackTrace());

        String realmId = realm.getId();
        MapGroupEntity entity = txInRealm(realm).read(id);
        return (entity == null || ! Objects.equals(realmId, entity.getRealmId()))
                ? null
                : entityToAdapterFunc(realm).apply(entity);
    }

    @Override
    public Stream<GroupModel> getGroupsStream(RealmModel realm) {
        return getGroupsStreamInternal(realm, null, null);
    }

    private Stream<GroupModel> getGroupsStreamInternal(RealmModel realm, UnaryOperator<DefaultModelCriteria<GroupModel>> modifier, UnaryOperator<QueryParameters<GroupModel>> queryParametersModifier) {
        LOG.tracef("getGroupsStream(%s)%s", realm, getShortStackTrace());
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        if (modifier != null) {
            mcb = modifier.apply(mcb);
        }

        QueryParameters<GroupModel> queryParameters = withCriteria(mcb).orderBy(SearchableFields.NAME, ASCENDING);
        if (queryParametersModifier != null) {
            queryParameters = queryParametersModifier.apply(queryParameters);
        }

        return txInRealm(realm).read(queryParameters)
                .map(entityToAdapterFunc(realm))
                ;
    }

    @Override
    public Stream<GroupModel> getGroupsStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.ID, Operator.IN, ids)
          .compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        if (search != null) {
            mcb = mcb.compare(SearchableFields.NAME, Operator.ILIKE, "%" + search + "%");
        }

        return txInRealm(realm).read(withCriteria(mcb).pagination(first, max, SearchableFields.NAME))
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public Long getGroupsCount(RealmModel realm, Boolean onlyTopGroups) {
        LOG.tracef("getGroupsCount(%s, %s)%s", realm, onlyTopGroups, getShortStackTrace());
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        if (Objects.equals(onlyTopGroups, Boolean.TRUE)) {
            mcb = mcb.compare(SearchableFields.PARENT_ID, Operator.NOT_EXISTS);
        }

        return txInRealm(realm).getCount(withCriteria(mcb));
    }

    @Override
    public Long getGroupsCountByNameContaining(RealmModel realm, String search) {
        return searchForGroupByNameStream(realm, search, false, null, null).count();
    }

    @Override
    public Stream<GroupModel> getGroupsByRoleStream(RealmModel realm, RoleModel role, Integer firstResult, Integer maxResults) {
        LOG.tracef("getGroupsByRole(%s, %s, %d, %d)%s", realm, role, firstResult, maxResults, getShortStackTrace());
        return getGroupsStreamInternal(realm,
          (DefaultModelCriteria<GroupModel> mcb) -> mcb.compare(SearchableFields.ASSIGNED_ROLE, Operator.EQ, role.getId()),
          qp -> qp.offset(firstResult).limit(maxResults)
        );
    }

    @Override
    public Stream<GroupModel> getTopLevelGroupsStream(RealmModel realm) {
        LOG.tracef("getTopLevelGroupsStream(%s)%s", realm, getShortStackTrace());
        return getGroupsStreamInternal(realm,
          (DefaultModelCriteria<GroupModel> mcb) -> mcb.compare(SearchableFields.PARENT_ID, Operator.NOT_EXISTS),
          null
        );
    }

    @Override
    public Stream<GroupModel> getTopLevelGroupsStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        LOG.tracef("getTopLevelGroupsStream(%s, %s, %s)%s", realm, firstResult, maxResults, getShortStackTrace());
        return getGroupsStreamInternal(realm,
                (DefaultModelCriteria<GroupModel> mcb) -> mcb.compare(SearchableFields.PARENT_ID, Operator.NOT_EXISTS),
                qp -> qp.offset(firstResult).limit(maxResults)
        );
    }

    @Override
    public Stream<GroupModel> searchForGroupByNameStream(RealmModel realm, String search, Boolean exact, Integer firstResult, Integer maxResults) {
        LOG.tracef("searchForGroupByNameStream(%s, %s, %s, %b, %d, %d)%s", realm, session, search, exact, firstResult, maxResults, getShortStackTrace());


        DefaultModelCriteria<GroupModel> mcb = criteria();
        if (exact != null && exact.equals(Boolean.TRUE)) {
            mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                    .compare(SearchableFields.NAME, Operator.EQ, search);
        } else {
            mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                    .compare(SearchableFields.NAME, Operator.ILIKE, "%" + search + "%");
        }


        return txInRealm(realm).read(withCriteria(mcb).pagination(firstResult, maxResults, SearchableFields.NAME))
                .map(MapGroupEntity::getId)
                .map(id -> {
                    GroupModel groupById = session.groups().getGroupById(realm, id);
                    while (Objects.nonNull(groupById.getParentId())) {
                        groupById = session.groups().getGroupById(realm, groupById.getParentId());
                    }
                    return groupById;
                }).sorted(GroupModel.COMPARE_BY_NAME).distinct();
    }

    @Override
    public Stream<GroupModel> searchGroupsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb =  mcb.compare(GroupModel.SearchableFields.REALM_ID, Operator.EQ, realm.getId());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            mcb = mcb.compare(GroupModel.SearchableFields.ATTRIBUTE, Operator.EQ, entry.getKey(), entry.getValue());
        }

        return txInRealm(realm).read(withCriteria(mcb).pagination(firstResult, maxResults, SearchableFields.NAME))
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public GroupModel createGroup(RealmModel realm, String id, String name, GroupModel toParent) {
        LOG.tracef("createGroup(%s, %s, %s, %s)%s", realm, id, name, toParent, getShortStackTrace());
        // Check Db constraint: uniqueConstraints = { @UniqueConstraint(columnNames = {"REALM_ID", "PARENT_GROUP", "NAME"})}
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
          .compare(SearchableFields.NAME, Operator.EQ, name);

        mcb = toParent == null ? 
                mcb.compare(SearchableFields.PARENT_ID, Operator.NOT_EXISTS) : 
                mcb.compare(SearchableFields.PARENT_ID, Operator.EQ, toParent.getId());

        if (txInRealm(realm).exists(withCriteria(mcb))) {
            throw new ModelDuplicateException("Group with name '" + name + "' in realm " + realm.getName() + " already exists for requested parent" );
        }

        MapGroupEntity entity = DeepCloner.DUMB_CLONER.newInstance(MapGroupEntity.class);
        entity.setId(id);
        entity.setRealmId(realm.getId());
        entity.setName(name);
        entity.setParentId(toParent == null ? null : toParent.getId());
        if (id != null && txInRealm(realm).exists(id)) {
            throw new ModelDuplicateException("Group exists: " + id);
        }
        entity = txInRealm(realm).create(entity);

        return entityToAdapterFunc(realm).apply(entity);
    }

    @Override
    public boolean removeGroup(RealmModel realm, GroupModel group) {
        LOG.tracef("removeGroup(%s, %s)%s", realm, group, getShortStackTrace());
        if (group == null) return false;

        session.invalidate(GROUP_BEFORE_REMOVE, realm, group);

        txInRealm(realm).delete(group.getId());
        
        session.invalidate(GROUP_AFTER_REMOVE, realm, group);

        return true;
    }

    /* TODO: investigate following two methods, it seems they could be moved to model layer */

    @Override
    public void moveGroup(RealmModel realm, GroupModel group, GroupModel toParent) {
        LOG.tracef("moveGroup(%s, %s, %s)%s", realm, group, toParent, getShortStackTrace());

        GroupModel previousParent = group.getParent();

        if (toParent != null && group.getId().equals(toParent.getId())) {
            return;
        }
        
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
          .compare(SearchableFields.NAME, Operator.EQ, group.getName());

        mcb = toParent == null ? 
                mcb.compare(SearchableFields.PARENT_ID, Operator.NOT_EXISTS) : 
                mcb.compare(SearchableFields.PARENT_ID, Operator.EQ, toParent.getId());

        try (Stream<MapGroupEntity> possibleSiblings = txInRealm(realm).read(withCriteria(mcb))) {
            if (possibleSiblings.findAny().isPresent()) {
                throw new ModelDuplicateException("Parent already contains subgroup named '" + group.getName() + "'");
            }
        }

        if (group.getParentId() != null) {
            group.getParent().removeChild(group);
        }
        group.setParent(toParent);
        if (toParent != null) toParent.addChild(group);

        String newPath = KeycloakModelUtils.buildGroupPath(group);
        String previousPath = KeycloakModelUtils.buildGroupPath(group, previousParent);

        GroupModel.GroupPathChangeEvent event =
                new GroupModel.GroupPathChangeEvent() {
                    @Override
                    public RealmModel getRealm() {
                        return realm;
                    }

                    @Override
                    public String getNewPath() {
                        return newPath;
                    }

                    @Override
                    public String getPreviousPath() {
                        return previousPath;
                    }

                    @Override
                    public KeycloakSession getKeycloakSession() {
                        return session;
                    }
                };
        session.getKeycloakSessionFactory().publish(event);
    }

    @Override
    public void addTopLevelGroup(RealmModel realm, GroupModel subGroup) {
        LOG.tracef("addTopLevelGroup(%s, %s)%s", realm, subGroup, getShortStackTrace());

        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
          .compare(SearchableFields.PARENT_ID, Operator.EQ, (Object) null)
          .compare(SearchableFields.NAME, Operator.EQ, subGroup.getName());

        try (Stream<MapGroupEntity> possibleSiblings = txInRealm(realm).read(withCriteria(mcb))) {
            if (possibleSiblings.findAny().isPresent()) {
                throw new ModelDuplicateException("There is already a top level group named '" + subGroup.getName() + "'");
            }
        }

        subGroup.setParent(null);
    }

    public void preRemove(RealmModel realm, RoleModel role) {
        LOG.tracef("preRemove(%s, %s)%s", realm, role, getShortStackTrace());
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
          .compare(SearchableFields.ASSIGNED_ROLE, Operator.EQ, role.getId());
        try (Stream<MapGroupEntity> toRemove = txInRealm(realm).read(withCriteria(mcb))) {
            toRemove
                .map(groupEntity -> session.groups().getGroupById(realm, groupEntity.getId()))
                .forEach(groupModel -> groupModel.deleteRoleMapping(role));
        }
    }

    public void preRemove(RealmModel realm) {
        LOG.tracef("preRemove(%s)%s", realm, getShortStackTrace());
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        txInRealm(realm).delete(withCriteria(mcb));
    }

    @Override
    public void close() {
    }

    private Stream<GroupModel> getGroupsByParentId(RealmModel realm, String parentId) {
        LOG.tracef("getGroupsByParentId(%s)%s", parentId, getShortStackTrace());
        DefaultModelCriteria<GroupModel> mcb = criteria();
        mcb = mcb
                .compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                .compare(SearchableFields.PARENT_ID, Operator.EQ, parentId);

        return txInRealm(realm).read(withCriteria(mcb)).map(entityToAdapterFunc(realm));
    }
}
