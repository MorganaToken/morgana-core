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
package org.keycloak.models.map.userSession;

import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.device.DeviceActivityManager;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.map.common.DeepCloner;
import org.keycloak.models.map.common.HasRealmId;
import org.keycloak.models.map.common.TimeAdapter;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.ModelCriteriaBuilder.Operator;
import org.keycloak.models.map.storage.criteria.DefaultModelCriteria;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.UserSessionModel.CORRESPONDING_SESSION_ID;
import static org.keycloak.models.UserSessionModel.SessionPersistenceState.TRANSIENT;
import static org.keycloak.models.map.common.ExpirationUtils.isExpired;
import static org.keycloak.models.map.storage.QueryParameters.withCriteria;
import static org.keycloak.models.map.storage.criteria.DefaultModelCriteria.criteria;
import static org.keycloak.models.map.userSession.SessionExpiration.setClientSessionExpiration;
import static org.keycloak.models.map.userSession.SessionExpiration.setUserSessionExpiration;

/**
 * @author <a href="mailto:mkanis@redhat.com">Martin Kanis</a>
 */
public class MapUserSessionProvider implements UserSessionProvider {

    private static final Logger LOG = Logger.getLogger(MapUserSessionProvider.class);
    private final KeycloakSession session;
    protected final MapKeycloakTransaction<MapUserSessionEntity, UserSessionModel> userSessionTx;

    /**
     * Storage for transient user sessions which lifespan is limited to one request.
     */
    private final Map<String, MapUserSessionEntity> transientUserSessions = new HashMap<>();
    private final boolean txHasRealmId;

    public MapUserSessionProvider(KeycloakSession session, MapStorage<MapUserSessionEntity, UserSessionModel> userSessionStore) {
        this.session = session;
        userSessionTx = userSessionStore.createTransaction(session);

        session.getTransactionManager().enlistAfterCompletion(userSessionTx);
        this.txHasRealmId = userSessionTx instanceof HasRealmId;
    }

    private Function<MapUserSessionEntity, UserSessionModel> userEntityToAdapterFunc(RealmModel realm) {
        // Clone entity before returning back, to avoid giving away a reference to the live object to the caller
        return (origEntity) -> {
            if (origEntity == null) return null;
            if (isExpired(origEntity, false)) {
                if (TRANSIENT == origEntity.getPersistenceState()) {
                    transientUserSessions.remove(origEntity.getId());
                } else {
                    txInRealm(realm).delete(origEntity.getId());
                }
                return null;
            } else {
                return new MapUserSessionAdapter(session, realm, origEntity);
            }
        };
    }

    private MapKeycloakTransaction<MapUserSessionEntity, UserSessionModel> txInRealm(RealmModel realm) {
        if (txHasRealmId) {
            ((HasRealmId) userSessionTx).setRealmId(realm == null ? null : realm.getId());
        }
        return userSessionTx;
    }

    @Override
    public KeycloakSession getKeycloakSession() {
        return session;
    }

    @Override
    public AuthenticatedClientSessionModel createClientSession(RealmModel realm, ClientModel client, UserSessionModel userSession) {
        LOG.tracef("createClientSession(%s, %s, %s)%s", realm, client, userSession, getShortStackTrace());

        MapUserSessionEntity userSessionEntity = getUserSessionById(realm, userSession.getId());

        if (userSessionEntity == null) {
            throw new IllegalStateException("User session entity does not exist: " + userSession.getId());
        }

        if (userSessionEntity.getAuthenticatedClientSession(client.getId()).isPresent()) {
            userSessionEntity.removeAuthenticatedClientSession(client.getId());
        }

        MapAuthenticatedClientSessionEntity entity = createAuthenticatedClientSessionEntityInstance(null, userSession.getId(),
                realm.getId(), client.getId(), false);
        String started = entity.getTimestamp() != null ? String.valueOf(TimeAdapter.fromMilliSecondsToSeconds(entity.getTimestamp())) : String.valueOf(0);
        entity.setNote(AuthenticatedClientSessionModel.STARTED_AT_NOTE, started);
        setClientSessionExpiration(entity, realm, client);

        userSessionEntity.addAuthenticatedClientSession(entity);

        // We need to load the clientSession through userModel so we return an entity that is included within the
        // transaction and also, so we not avoid all the checks present in the adapter, for example expiration
        UserSessionModel userSessionModel = userEntityToAdapterFunc(realm).apply(userSessionEntity);
        return userSessionModel == null ? null : userSessionModel.getAuthenticatedClientSessionByClient(client.getId());
    }

    @Override
    public AuthenticatedClientSessionModel getClientSession(UserSessionModel userSession, ClientModel client,
                                                            String clientSessionId, boolean offline) {
        LOG.tracef("getClientSession(%s, %s, %s, %s)%s", userSession, client,
                clientSessionId, offline, getShortStackTrace());

        return userSession.getAuthenticatedClientSessionByClient(client.getId());
    }

    @Override
    public UserSessionModel createUserSession(String id, RealmModel realm, UserModel user, String loginUsername,
                                              String ipAddress, String authMethod, boolean rememberMe, String brokerSessionId,
                                              String brokerUserId, UserSessionModel.SessionPersistenceState persistenceState) {
        LOG.tracef("createUserSession(%s, %s, %s, %s)%s", id, realm, loginUsername, persistenceState, getShortStackTrace());

        MapUserSessionEntity entity = createUserSessionEntityInstance(id, realm.getId(), user.getId(), loginUsername, ipAddress, authMethod,
                    rememberMe, brokerSessionId, brokerUserId, false);

        if (TRANSIENT == persistenceState) {
            if (id == null) {
                entity.setId(UUID.randomUUID().toString());
            }
            transientUserSessions.put(entity.getId(), entity);
        } else {
            if (id != null && txInRealm(realm).exists(id)) {
                throw new ModelDuplicateException("User session exists: " + id);
            }
            entity = txInRealm(realm).create(entity);
        }

        entity.setPersistenceState(persistenceState);
        setUserSessionExpiration(entity, realm);
        UserSessionModel userSession = userEntityToAdapterFunc(realm).apply(entity);

        return userSession;
    }

    @Override
    public UserSessionModel getUserSession(RealmModel realm, String id) {
        Objects.requireNonNull(realm, "The provided realm can't be null!");

        LOG.tracef("getUserSession(%s, %s)%s", realm, id, getShortStackTrace());

        if (id == null) return null;

        MapUserSessionEntity userSessionEntity = transientUserSessions.get(id);
        if (userSessionEntity != null) {
            return userEntityToAdapterFunc(realm).apply(userSessionEntity);
        }

        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.ID, Operator.EQ, id);

        return txInRealm(realm).read(withCriteria(mcb))
                .findFirst()
                .map(userEntityToAdapterFunc(realm))
                .orElse(null);
    }

    @Override
    public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, UserModel user) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.USER_ID, Operator.EQ, user.getId());

        LOG.tracef("getUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull);
    }

    @Override
    public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, ClientModel client) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.CLIENT_ID, Operator.EQ, client.getId());

        LOG.tracef("getUserSessionsStream(%s, %s)%s", realm, client, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull);
    }

    @Override
    public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, ClientModel client,
                                                          Integer firstResult, Integer maxResults) {
        LOG.tracef("getUserSessionsStream(%s, %s, %s, %s)%s", realm, client, firstResult, maxResults, getShortStackTrace());

        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.CLIENT_ID, Operator.EQ, client.getId());


        return txInRealm(realm).read(withCriteria(mcb).pagination(firstResult, maxResults,
                        UserSessionModel.SearchableFields.LAST_SESSION_REFRESH))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull);
    }

    @Override
    public Stream<UserSessionModel> getUserSessionByBrokerUserIdStream(RealmModel realm, String brokerUserId) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.BROKER_USER_ID, Operator.EQ, brokerUserId);

        LOG.tracef("getUserSessionByBrokerUserIdStream(%s, %s)%s", realm, brokerUserId, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull);
    }

    @Override
    public UserSessionModel getUserSessionByBrokerSessionId(RealmModel realm, String brokerSessionId) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.BROKER_SESSION_ID, Operator.EQ, brokerSessionId);

        LOG.tracef("getUserSessionByBrokerSessionId(%s, %s)%s", realm, brokerSessionId, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .findFirst()
                .map(userEntityToAdapterFunc(realm))
                .orElse(null);
    }

    @Override
    public UserSessionModel getUserSessionWithPredicate(RealmModel realm, String id, boolean offline,
                                                        Predicate<UserSessionModel> predicate) {
        LOG.tracef("getUserSessionWithPredicate(%s, %s, %s)%s", realm, id, offline, getShortStackTrace());

        Stream<UserSessionModel> userSessionEntityStream;
        if (offline) {
            userSessionEntityStream = getOfflineUserSessionEntityStream(realm, id)
                    .map(userEntityToAdapterFunc(realm)).filter(Objects::nonNull);
        } else {
            UserSessionModel userSession = getUserSession(realm, id);
            userSessionEntityStream = userSession != null ? Stream.of(userSession) : Stream.empty();
        }

        return userSessionEntityStream
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    @Override
    public long getActiveUserSessions(RealmModel realm, ClientModel client) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.CLIENT_ID, Operator.EQ, client.getId());

        LOG.tracef("getActiveUserSessions(%s, %s)%s", realm, client, getShortStackTrace());

        return txInRealm(realm).getCount(withCriteria(mcb));
    }

    @Override
    public Map<String, Long> getActiveClientSessionStats(RealmModel realm, boolean offline) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, offline);

        LOG.tracef("getActiveClientSessionStats(%s, %s)%s", realm, offline, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull)
                .map(UserSessionModel::getAuthenticatedClientSessions)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    @Override
    public void removeUserSession(RealmModel realm, UserSessionModel session) {
        Objects.requireNonNull(session, "The provided user session can't be null!");

        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false)
                .compare(UserSessionModel.SearchableFields.ID, Operator.EQ, session.getId());

        LOG.tracef("removeUserSession(%s, %s)%s", realm, session, getShortStackTrace());

        txInRealm(realm).delete(withCriteria(mcb));
    }

    @Override
    public void removeUserSessions(RealmModel realm, UserModel user) {
        DefaultModelCriteria<UserSessionModel> mcb = criteria();
        mcb = mcb.compare(UserSessionModel.SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                .compare(UserSessionModel.SearchableFields.USER_ID, Operator.EQ, user.getId());

        LOG.tracef("removeUserSessions(%s, %s)%s", realm, user, getShortStackTrace());

        txInRealm(realm).delete(withCriteria(mcb));
    }

    @Override
    public void removeAllExpired() {
        LOG.tracef("removeAllExpired()%s", getShortStackTrace());
    }

    @Override
    public void removeExpired(RealmModel realm) {
        LOG.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
    }

    @Override
    public void removeUserSessions(RealmModel realm) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, false);

        LOG.tracef("removeUserSessions(%s)%s", realm, getShortStackTrace());

        txInRealm(realm).delete(withCriteria(mcb));
    }

    @Override
    public void onRealmRemoved(RealmModel realm) {
        LOG.tracef("onRealmRemoved(%s)%s", realm, getShortStackTrace());
    }

    @Override
    public void onClientRemoved(RealmModel realm, ClientModel client) {

    }

    @Override
    public UserSessionModel createOfflineUserSession(UserSessionModel userSession) {
        LOG.tracef("createOfflineUserSession(%s)%s", userSession, getShortStackTrace());

        MapUserSessionEntity offlineUserSession = createUserSessionEntityInstance(userSession, true);
        RealmModel realm = userSession.getRealm();
        offlineUserSession = txInRealm(realm).create(offlineUserSession);

        // set a reference for the offline user session to the original online user session
        userSession.setNote(CORRESPONDING_SESSION_ID, offlineUserSession.getId());

        long currentTime = Time.currentTimeMillis();
        offlineUserSession.setTimestamp(currentTime);
        offlineUserSession.setLastSessionRefresh(currentTime);
        setUserSessionExpiration(offlineUserSession, userSession.getRealm());

        return userEntityToAdapterFunc(userSession.getRealm()).apply(offlineUserSession);
    }

    @Override
    public UserSessionModel getOfflineUserSession(RealmModel realm, String userSessionId) {
        LOG.tracef("getOfflineUserSession(%s, %s)%s", realm, userSessionId, getShortStackTrace());

        return getOfflineUserSessionEntityStream(realm, userSessionId)
                .findFirst()
                .map(userEntityToAdapterFunc(realm))
                .orElse(null);
    }

    @Override
    public void removeOfflineUserSession(RealmModel realm, UserSessionModel userSession) {
        Objects.requireNonNull(userSession, "The provided user session can't be null!");

        LOG.tracef("removeOfflineUserSession(%s, %s)%s", realm, userSession, getShortStackTrace());

        DefaultModelCriteria<UserSessionModel> mcb;
        if (userSession.isOffline()) {
            txInRealm(realm).delete(userSession.getId());
        } else if (userSession.getNote(CORRESPONDING_SESSION_ID) != null) {
            String uk = userSession.getNote(CORRESPONDING_SESSION_ID);
            mcb = realmAndOfflineCriteriaBuilder(realm, true)
                    .compare(UserSessionModel.SearchableFields.ID, Operator.EQ, uk);
            txInRealm(realm).delete(withCriteria(mcb));
            userSession.removeNote(CORRESPONDING_SESSION_ID);
        }
    }

    @Override
    public AuthenticatedClientSessionModel createOfflineClientSession(AuthenticatedClientSessionModel clientSession,
                                                                      UserSessionModel offlineUserSession) {
        LOG.tracef("createOfflineClientSession(%s, %s)%s", clientSession, offlineUserSession, getShortStackTrace());

        MapAuthenticatedClientSessionEntity clientSessionEntity = createAuthenticatedClientSessionInstance(clientSession, offlineUserSession, true);
        int currentTime = Time.currentTime();
        clientSessionEntity.setNote(AuthenticatedClientSessionModel.STARTED_AT_NOTE, String.valueOf(currentTime));
        clientSessionEntity.setTimestamp(Time.currentTimeMillis());
        RealmModel realm = clientSession.getRealm();
        setClientSessionExpiration(clientSessionEntity, realm, clientSession.getClient());

        Optional<MapUserSessionEntity> userSessionEntity = getOfflineUserSessionEntityStream(realm, offlineUserSession.getId()).findFirst();
        if (userSessionEntity.isPresent()) {
            MapUserSessionEntity userSession = userSessionEntity.get();
            String clientId = clientSession.getClient().getId();
            if (userSession.getAuthenticatedClientSession(clientId).isPresent()) {
                userSession.removeAuthenticatedClientSession(clientId);
            }

            userSession.addAuthenticatedClientSession(clientSessionEntity);

            UserSessionModel userSessionModel = userEntityToAdapterFunc(realm).apply(userSession);
            return userSessionModel == null ? null : userSessionModel.getAuthenticatedClientSessionByClient(clientId);
        }

        return null;
    }

    @Override
    public Stream<UserSessionModel> getOfflineUserSessionsStream(RealmModel realm, UserModel user) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, true)
                .compare(UserSessionModel.SearchableFields.USER_ID, Operator.EQ, user.getId());

        LOG.tracef("getOfflineUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull);
    }

    @Override
    public UserSessionModel getOfflineUserSessionByBrokerSessionId(RealmModel realm, String brokerSessionId) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, true)
                .compare(UserSessionModel.SearchableFields.BROKER_SESSION_ID, Operator.EQ, brokerSessionId);

        LOG.tracef("getOfflineUserSessionByBrokerSessionId(%s, %s)%s", realm, brokerSessionId, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .findFirst()
                .map(userEntityToAdapterFunc(realm))
                .orElse(null);
    }

    @Override
    public Stream<UserSessionModel> getOfflineUserSessionByBrokerUserIdStream(RealmModel realm, String brokerUserId) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, true)
                .compare(UserSessionModel.SearchableFields.BROKER_USER_ID, Operator.EQ, brokerUserId);

        LOG.tracef("getOfflineUserSessionByBrokerUserIdStream(%s, %s)%s", realm, brokerUserId, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull);
    }

    @Override
    public long getOfflineSessionsCount(RealmModel realm, ClientModel client) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, true)
                .compare(UserSessionModel.SearchableFields.CLIENT_ID, Operator.EQ, client.getId());

        LOG.tracef("getOfflineSessionsCount(%s, %s)%s", realm, client, getShortStackTrace());

        return txInRealm(realm).getCount(withCriteria(mcb));
    }

    @Override
    public Stream<UserSessionModel> getOfflineUserSessionsStream(RealmModel realm, ClientModel client,
                                                                 Integer firstResult, Integer maxResults) {
        DefaultModelCriteria<UserSessionModel> mcb = realmAndOfflineCriteriaBuilder(realm, true)
                .compare(UserSessionModel.SearchableFields.CLIENT_ID, Operator.EQ, client.getId());

        LOG.tracef("getOfflineUserSessionsStream(%s, %s, %s, %s)%s", realm, client, firstResult, maxResults, getShortStackTrace());

        return txInRealm(realm).read(withCriteria(mcb).pagination(firstResult, maxResults,
                        UserSessionModel.SearchableFields.LAST_SESSION_REFRESH))
                .map(userEntityToAdapterFunc(realm))
                .filter(Objects::nonNull);
    }

    @Override
    public void importUserSessions(Collection<UserSessionModel> persistentUserSessions, boolean offline) {
        if (persistentUserSessions == null || persistentUserSessions.isEmpty()) {
            return;
        }

        persistentUserSessions.stream()
            .map(pus -> {
                MapUserSessionEntity userSessionEntity = createUserSessionEntityInstance(null, pus.getRealm().getId(),
                        pus.getUser().getId(), pus.getLoginUsername(), pus.getIpAddress(), pus.getAuthMethod(),
                        pus.isRememberMe(), pus.getBrokerSessionId(), pus.getBrokerUserId(), offline);

                for (Map.Entry<String, AuthenticatedClientSessionModel> entry : pus.getAuthenticatedClientSessions().entrySet()) {
                    MapAuthenticatedClientSessionEntity clientSession = createAuthenticatedClientSessionInstance(entry.getValue(), entry.getValue().getUserSession(), offline);

                    // Update timestamp to same value as userSession. LastSessionRefresh of userSession from DB will have correct value
                    clientSession.setTimestamp(userSessionEntity.getLastSessionRefresh());
                    userSessionEntity.addAuthenticatedClientSession(clientSession);
                }

                return userSessionEntity;
            })
            .forEach(userSessionTx::create);
    }

    @Override
    public void close() {

    }

    @Override
    public int getStartupTime(RealmModel realm) {
        return realm.getNotBefore();
    }

    /**
     * Removes all online and offline user sessions that belong to the provided {@link RealmModel}.
     * @param realm
     */
    protected void removeAllUserSessions(RealmModel realm) {
        DefaultModelCriteria<UserSessionModel> mcb = criteria();
        mcb = mcb.compare(UserSessionModel.SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        LOG.tracef("removeAllUserSessions(%s)%s", realm, getShortStackTrace());

        txInRealm(realm).delete(withCriteria(mcb));
    }

    private Stream<MapUserSessionEntity> getOfflineUserSessionEntityStream(RealmModel realm, String userSessionId) {
        if (userSessionId == null) {
            return Stream.empty();
        }

        // first get a user entity by ID
        DefaultModelCriteria<UserSessionModel> mcb = criteria();
        mcb = mcb.compare(UserSessionModel.SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                .compare(UserSessionModel.SearchableFields.ID, Operator.EQ, userSessionId);

        // check if it's an offline user session
        MapUserSessionEntity userSessionEntity = txInRealm(realm).read(withCriteria(mcb)).findFirst().orElse(null);
        if (userSessionEntity != null) {
            if (Boolean.TRUE.equals(userSessionEntity.isOffline())) {
                return Stream.of(userSessionEntity);
            }
        } else {
            // no session found by the given ID, try to find by corresponding session ID
            mcb = realmAndOfflineCriteriaBuilder(realm, true)
                    .compare(UserSessionModel.SearchableFields.CORRESPONDING_SESSION_ID, Operator.EQ, userSessionId);
            return txInRealm(realm).read(withCriteria(mcb));
        }

        // it's online user session so lookup offline user session by corresponding session id reference
        String offlineUserSessionId = userSessionEntity.getNote(CORRESPONDING_SESSION_ID);
        if (offlineUserSessionId != null) {
            mcb = realmAndOfflineCriteriaBuilder(realm, true)
                    .compare(UserSessionModel.SearchableFields.ID, Operator.EQ, offlineUserSessionId);
            return txInRealm(realm).read(withCriteria(mcb));
        }

        return Stream.empty();
    }

    private DefaultModelCriteria<UserSessionModel> realmAndOfflineCriteriaBuilder(RealmModel realm, boolean offline) {
        return DefaultModelCriteria.<UserSessionModel>criteria()
                .compare(UserSessionModel.SearchableFields.REALM_ID, Operator.EQ, realm.getId())
                .compare(UserSessionModel.SearchableFields.IS_OFFLINE, Operator.EQ, offline);
    }

    private MapUserSessionEntity getUserSessionById(RealmModel realm, String id) {
        if (id == null) return null;

        MapUserSessionEntity userSessionEntity = transientUserSessions.get(id);

        if (userSessionEntity == null) {
            MapUserSessionEntity userSession = txInRealm(realm).read(id);
            return userSession;
        }
        return userSessionEntity;
    }

    private MapUserSessionEntity createUserSessionEntityInstance(UserSessionModel userSession, boolean offline) {
        MapUserSessionEntity entity = createUserSessionEntityInstance(null, userSession.getRealm().getId(), userSession.getUser().getId(),
                userSession.getLoginUsername(), userSession.getIpAddress(), userSession.getAuthMethod(), userSession.isRememberMe(),
                userSession.getBrokerSessionId(), userSession.getBrokerUserId(), offline);

        entity.setNotes(new ConcurrentHashMap<>(userSession.getNotes()));
        entity.setNote(CORRESPONDING_SESSION_ID, userSession.getId());
        entity.setState(userSession.getState());
        entity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(userSession.getStarted()));
        entity.setLastSessionRefresh(TimeAdapter.fromSecondsToMilliseconds(userSession.getLastSessionRefresh()));

        return entity;
    }

    private MapAuthenticatedClientSessionEntity createAuthenticatedClientSessionInstance(AuthenticatedClientSessionModel clientSession,
                                                                                         UserSessionModel userSession, boolean offline) {
        MapAuthenticatedClientSessionEntity entity = createAuthenticatedClientSessionEntityInstance(null, userSession.getId(),
                clientSession.getRealm().getId(), clientSession.getClient().getId(), offline);

        entity.setAction(clientSession.getAction());
        entity.setAuthMethod(clientSession.getProtocol());

        entity.setNotes(new ConcurrentHashMap<>(clientSession.getNotes()));
        entity.setRedirectUri(clientSession.getRedirectUri());
        entity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(clientSession.getTimestamp()));

        return entity;
    }

    private MapUserSessionEntity createUserSessionEntityInstance(String id, String realmId, String userId, String loginUsername, String ipAddress,
                                                                 String authMethod, boolean rememberMe, String brokerSessionId, String brokerUserId,
                                                                 boolean offline) {
        MapUserSessionEntity userSessionEntity = DeepCloner.DUMB_CLONER.newInstance(MapUserSessionEntity.class);
        userSessionEntity.setId(id);
        userSessionEntity.setRealmId(realmId);
        userSessionEntity.setUserId(userId);
        userSessionEntity.setLoginUsername(loginUsername);
        userSessionEntity.setIpAddress(ipAddress);
        userSessionEntity.setAuthMethod(authMethod);
        userSessionEntity.setRememberMe(rememberMe);
        userSessionEntity.setBrokerSessionId(brokerSessionId);
        userSessionEntity.setBrokerUserId(brokerUserId);
        userSessionEntity.setOffline(offline);
        userSessionEntity.setTimestamp(Time.currentTimeMillis());
        userSessionEntity.setLastSessionRefresh(userSessionEntity.getTimestamp());
        return userSessionEntity;
    }

    private MapAuthenticatedClientSessionEntity createAuthenticatedClientSessionEntityInstance(String id, String userSessionId, String realmId,
                                                                                               String clientId, boolean offline) {
        MapAuthenticatedClientSessionEntity clientSessionEntity = DeepCloner.DUMB_CLONER.newInstance(MapAuthenticatedClientSessionEntity.class);
        clientSessionEntity.setId(id == null ? KeycloakModelUtils.generateId() : id);
        clientSessionEntity.setRealmId(realmId);
        clientSessionEntity.setClientId(clientId);
        clientSessionEntity.setOffline(offline);
        clientSessionEntity.setTimestamp(Time.currentTimeMillis());
        return clientSessionEntity;
    }

}
