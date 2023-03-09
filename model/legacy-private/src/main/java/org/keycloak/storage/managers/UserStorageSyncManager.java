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
package org.keycloak.storage.managers;

import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.cluster.ClusterListener;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.cluster.ExecutionResult;
import org.keycloak.common.util.Time;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.LegacyRealmModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;
import org.keycloak.timer.TimerProvider;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class UserStorageSyncManager {

    private static final String USER_STORAGE_TASK_KEY = "user-storage";

    private static final Logger logger = Logger.getLogger(UserStorageSyncManager.class);

    /**
     * Check federationProviderModel of all realms and possibly start periodic sync for them
     *
     * @param sessionFactory
     * @param timer
     */
    public static void bootstrapPeriodic(final KeycloakSessionFactory sessionFactory, final TimerProvider timer) {
        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                Stream<RealmModel> realms = session.realms().getRealmsWithProviderTypeStream(UserStorageProvider.class);
                realms.forEach(realm -> {
                    Stream<UserStorageProviderModel> providers = ((LegacyRealmModel) realm).getUserStorageProvidersStream();
                    providers.forEachOrdered(provider -> {
                        UserStorageProviderFactory factory = (UserStorageProviderFactory) session.getKeycloakSessionFactory().getProviderFactory(UserStorageProvider.class, provider.getProviderId());
                        if (factory instanceof ImportSynchronization && provider.isImportEnabled()) {
                            refreshPeriodicSyncForProvider(sessionFactory, timer, provider, realm.getId());
                        }
                    });
                });

                ClusterProvider clusterProvider = session.getProvider(ClusterProvider.class);
                clusterProvider.registerListener(USER_STORAGE_TASK_KEY, new UserStorageClusterListener(sessionFactory));
            }
        });
    }

    private static class Holder {
        ExecutionResult<SynchronizationResult> result;
    }

    public static SynchronizationResult syncAllUsers(final KeycloakSessionFactory sessionFactory, final String realmId, final UserStorageProviderModel provider) {
        UserStorageProviderFactory factory = (UserStorageProviderFactory) sessionFactory.getProviderFactory(UserStorageProvider.class, provider.getProviderId());
        if (!(factory instanceof ImportSynchronization) || !provider.isImportEnabled() || !provider.isEnabled()) {
            return SynchronizationResult.ignored();

        }

        final Holder holder = new Holder();

        // Ensure not executed concurrently on this or any other cluster node
        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                ClusterProvider clusterProvider = session.getProvider(ClusterProvider.class);
                // shared key for "full" and "changed" . Improve if needed
                String taskKey = provider.getId() + "::sync";

                // 30 seconds minimal timeout for now
                int timeout = Math.max(30, provider.getFullSyncPeriod());
                holder.result = clusterProvider.executeIfNotExecuted(taskKey, timeout, new Callable<SynchronizationResult>() {

                    @Override
                    public SynchronizationResult call() throws Exception {
                        int lastSync = Time.currentTime();
                        SynchronizationResult result = ((ImportSynchronization)factory).sync(sessionFactory, realmId, provider);
                        if (!result.isIgnored()) {
                            updateLastSyncInterval(sessionFactory, provider, realmId, lastSync);
                        }
                        return result;
                    }

                });
            }

        });

        if (holder.result == null || !holder.result.isExecuted()) {
            logger.debugf("syncAllUsers for federation provider %s was ignored as it's already in progress", provider.getName());
            return SynchronizationResult.ignored();
        } else {
            return holder.result.getResult();
        }
    }

    public static SynchronizationResult syncChangedUsers(final KeycloakSessionFactory sessionFactory, final String realmId, final UserStorageProviderModel provider) {
        UserStorageProviderFactory factory = (UserStorageProviderFactory) sessionFactory.getProviderFactory(UserStorageProvider.class, provider.getProviderId());
        if (!(factory instanceof ImportSynchronization) || !provider.isImportEnabled() || !provider.isEnabled()) {
            return SynchronizationResult.ignored();

        }
        final Holder holder = new Holder();

        // Ensure not executed concurrently on this or any other cluster node
        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                ClusterProvider clusterProvider = session.getProvider(ClusterProvider.class);
                // shared key for "full" and "changed" . Improve if needed
                String taskKey = provider.getId() + "::sync";

                // 30 seconds minimal timeout for now
                int timeout = Math.max(30, provider.getChangedSyncPeriod());
                holder.result = clusterProvider.executeIfNotExecuted(taskKey, timeout, new Callable<SynchronizationResult>() {

                    @Override
                    public SynchronizationResult call() throws Exception {
                        // See when we did last sync.
                        int oldLastSync = provider.getLastSync();
                        int lastSync = Time.currentTime();
                        SynchronizationResult result = ((ImportSynchronization)factory).syncSince(Time.toDate(oldLastSync), sessionFactory, realmId, provider);
                        if (!result.isIgnored()) {
                            updateLastSyncInterval(sessionFactory, provider, realmId, lastSync);
                        }
                        return result;
                    }

                });
            }

        });

        if (holder.result == null || !holder.result.isExecuted()) {
            logger.debugf("syncChangedUsers for federation provider %s was ignored as it's already in progress", provider.getName());
            return SynchronizationResult.ignored();
        } else {
            return holder.result.getResult();
        }
    }


    public static void notifyToRefreshPeriodicSyncAll(KeycloakSession session, RealmModel realm, boolean removed) {
        ((LegacyRealmModel) realm).getUserStorageProvidersStream().forEachOrdered(fedProvider ->
           notifyToRefreshPeriodicSync(session, realm, fedProvider, removed));
    }
    
    public static void notifyToRefreshPeriodicSyncSingle(KeycloakSession session, RealmModel realm, ComponentModel component, boolean removed) {
        notifyToRefreshPeriodicSync(session, realm, new UserStorageProviderModel(component), removed);
    }
    
    // Ensure all cluster nodes are notified
    public static void notifyToRefreshPeriodicSync(KeycloakSession session, RealmModel realm, UserStorageProviderModel provider, boolean removed) {
        UserStorageProviderFactory factory = (UserStorageProviderFactory) session.getKeycloakSessionFactory().getProviderFactory(UserStorageProvider.class, provider.getProviderId());
        if (!(factory instanceof ImportSynchronization) || !provider.isImportEnabled()) {
            return;

        }
        final ClusterProvider cp = session.getProvider(ClusterProvider.class);
        if (cp != null) {
            UserStorageProviderClusterEvent event = UserStorageProviderClusterEvent.createEvent(removed, realm.getId(), provider);
            cp.notify(USER_STORAGE_TASK_KEY, event, false, ClusterProvider.DCNotify.ALL_DCS);
        }
    }


    // Executed once it receives notification that some UserFederationProvider was created or updated
    protected static void refreshPeriodicSyncForProvider(final KeycloakSessionFactory sessionFactory, TimerProvider timer, final UserStorageProviderModel provider, final String realmId) {
        logger.debugf("Going to refresh periodic sync for provider '%s' . Full sync period: %d , changed users sync period: %d",
                provider.getName(), provider.getFullSyncPeriod(), provider.getChangedSyncPeriod());

        if (provider.getFullSyncPeriod() > 0) {
            // We want periodic full sync for this provider
            timer.schedule(new Runnable() {

                @Override
                public void run() {
                    try {
                        boolean shouldPerformSync = shouldPerformNewPeriodicSync(provider.getLastSync(), provider.getChangedSyncPeriod());
                        if (shouldPerformSync) {
                            syncAllUsers(sessionFactory, realmId, provider);
                        } else {
                            logger.debugf("Ignored periodic full sync with storage provider %s due small time since last sync", provider.getName());
                        }
                    } catch (Throwable t) {
                        logger.error("Error occurred during full sync of users", t);
                    }
                }

            }, provider.getFullSyncPeriod() * 1000, provider.getId() + "-FULL");
        } else {
            timer.cancelTask(provider.getId() + "-FULL");
        }

        if (provider.getChangedSyncPeriod() > 0) {
            // We want periodic sync of just changed users for this provider
            timer.schedule(new Runnable() {

                @Override
                public void run() {
                    try {
                        boolean shouldPerformSync = shouldPerformNewPeriodicSync(provider.getLastSync(), provider.getChangedSyncPeriod());
                        if (shouldPerformSync) {
                            syncChangedUsers(sessionFactory, realmId, provider);
                        } else {
                            logger.debugf("Ignored periodic changed-users sync with storage provider %s due small time since last sync", provider.getName());
                        }
                    } catch (Throwable t) {
                        logger.error("Error occurred during sync of changed users", t);
                    }
                }

            }, provider.getChangedSyncPeriod() * 1000, provider.getId() + "-CHANGED");

        } else {
            timer.cancelTask(provider.getId() + "-CHANGED");
        }
    }

    // Skip syncing if there is short time since last sync time.
    private static boolean shouldPerformNewPeriodicSync(int lastSyncTime, int period) {
        if (lastSyncTime <= 0) {
            return true;
        }

        int currentTime = Time.currentTime();
        int timeSinceLastSync = currentTime - lastSyncTime;

        return (timeSinceLastSync * 2 > period);
    }

    // Executed once it receives notification that some UserFederationProvider was removed
    protected static void removePeriodicSyncForProvider(TimerProvider timer, UserStorageProviderModel fedProvider) {
        logger.debugf("Removing periodic sync for provider %s", fedProvider.getName());
        timer.cancelTask(fedProvider.getId() + "-FULL");
        timer.cancelTask(fedProvider.getId() + "-CHANGED");
    }

    // Update interval of last sync for given UserFederationProviderModel. Do it in separate transaction
    private static void updateLastSyncInterval(final KeycloakSessionFactory sessionFactory, UserStorageProviderModel provider, final String realmId, final int lastSync) {
        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                RealmModel persistentRealm = session.realms().getRealm(realmId);
                ((LegacyRealmModel) persistentRealm).getUserStorageProvidersStream()
                        .filter(persistentFedProvider -> Objects.equals(provider.getId(), persistentFedProvider.getId()))
                        .forEachOrdered(persistentFedProvider -> {
                            // Update persistent provider in DB
                            persistentFedProvider.setLastSync(lastSync);
                            persistentRealm.updateComponent(persistentFedProvider);

                            // Update "cached" reference
                            provider.setLastSync(lastSync);
                        });
            }
        });
    }


    private static class UserStorageClusterListener implements ClusterListener {

        private final KeycloakSessionFactory sessionFactory;

        public UserStorageClusterListener(KeycloakSessionFactory sessionFactory) {
            this.sessionFactory = sessionFactory;
        }

        @Override
        public void eventReceived(ClusterEvent event) {
            final UserStorageProviderClusterEvent fedEvent = (UserStorageProviderClusterEvent) event;
            KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

                @Override
                public void run(KeycloakSession session) {
                    TimerProvider timer = session.getProvider(TimerProvider.class);
                    if (fedEvent.isRemoved()) {
                        removePeriodicSyncForProvider(timer, fedEvent.getStorageProvider());
                    } else {
                        refreshPeriodicSyncForProvider(sessionFactory, timer, fedEvent.getStorageProvider(), fedEvent.getRealmId());
                    }
                }

            });
        }
    }


    // Send to cluster during each update or remove of federationProvider, so all nodes can update sync periods
    public static class UserStorageProviderClusterEvent implements ClusterEvent {

        private boolean removed;
        private String realmId;
        private UserStorageProviderModel storageProvider;

        public boolean isRemoved() {
            return removed;
        }

        public void setRemoved(boolean removed) {
            this.removed = removed;
        }

        public String getRealmId() {
            return realmId;
        }

        public void setRealmId(String realmId) {
            this.realmId = realmId;
        }

        public UserStorageProviderModel getStorageProvider() {
            return storageProvider;
        }

        public void setStorageProvider(UserStorageProviderModel federationProvider) {
            this.storageProvider = federationProvider;
        }

        public static UserStorageProviderClusterEvent createEvent(boolean removed, String realmId, UserStorageProviderModel provider) {
            UserStorageProviderClusterEvent notification = new UserStorageProviderClusterEvent();
            notification.setRemoved(removed);
            notification.setRealmId(realmId);
            notification.setStorageProvider(provider);
            return notification;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserStorageProviderClusterEvent that = (UserStorageProviderClusterEvent) o;
            return removed == that.removed && Objects.equals(realmId, that.realmId) && Objects.equals(storageProvider.getId(), that.storageProvider.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(removed, realmId, storageProvider.getId());
        }
    }

}
