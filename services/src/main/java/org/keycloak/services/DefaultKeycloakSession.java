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
package org.keycloak.services;

import org.jboss.logging.Logger;
import org.keycloak.common.util.StackUtil;
import org.keycloak.component.ComponentFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.jose.jws.DefaultTokenManager;
import org.keycloak.keys.DefaultKeyManager;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.ClientScopeProvider;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakTransactionManager;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleProvider;
import org.keycloak.models.ThemeManager;
import org.keycloak.models.TokenManager;
import org.keycloak.models.UserCredentialManager;
import org.keycloak.models.UserLoginFailureProvider;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.InvalidationHandler.InvalidableObjectType;
import org.keycloak.provider.InvalidationHandler.ObjectType;
import org.keycloak.services.clientpolicy.ClientPolicyManager;
import org.keycloak.models.LegacySessionSupportProvider;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.storage.DatastoreProvider;
import org.keycloak.vault.DefaultVaultTranscriber;
import org.keycloak.vault.VaultProvider;
import org.keycloak.vault.VaultTranscriber;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class DefaultKeycloakSession implements KeycloakSession {

    private final DefaultKeycloakSessionFactory factory;
    private final Map<Integer, Provider> providers = new HashMap<>();
    private final List<Provider> closable = new LinkedList<>();
    private final DefaultKeycloakTransactionManager transactionManager;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<InvalidableObjectType, Set<Object>> invalidationMap = new HashMap<>();
    private DatastoreProvider datastoreProvider;
    @Deprecated
    private UserCredentialManager userCredentialStorageManager;
    private UserSessionProvider sessionProvider;
    private UserLoginFailureProvider userLoginFailureProvider;
    private AuthenticationSessionProvider authenticationSessionProvider;
    private final KeycloakContext context;
    private KeyManager keyManager;
    private ThemeManager themeManager;
    private TokenManager tokenManager;
    private VaultTranscriber vaultTranscriber;
    private ClientPolicyManager clientPolicyManager;
    private boolean closed = false;

    public DefaultKeycloakSession(DefaultKeycloakSessionFactory factory) {
        this.factory = factory;
        this.transactionManager = new DefaultKeycloakTransactionManager(this);
        context = createKeycloakContext(this);
        LOG.tracef("Created %s%s", this, StackUtil.getShortStackTrace());
    }

    @Override
    public KeycloakContext getContext() {
        return context;
    }

    private DatastoreProvider getDatastoreProvider() {
        if (this.datastoreProvider == null) {
            this.datastoreProvider = getProvider(DatastoreProvider.class);
        }
        return this.datastoreProvider;
    }

    @Override
    @Deprecated
    public UserProvider userCache() {
        LegacySessionSupportProvider provider = this.getProvider(LegacySessionSupportProvider.class);
        if (provider == null) {
            throw new IllegalStateException("legacy support for userCache is not enabled");
        }
        return provider.userCache();
    }

    @Override
    public void invalidate(InvalidableObjectType type, Object... ids) {
        factory.invalidate(this, type, ids);
        if (type == ObjectType.PROVIDER_FACTORY) {
            invalidationMap.computeIfAbsent(type, o -> new HashSet<>()).addAll(Arrays.asList(ids));
        }
    }

    @Override
    public void enlistForClose(Provider provider) {
        for (Provider p : closable) {
            if (p == provider) {    // Do not add the same provider twice
                return;
            }
        }
        closable.add(provider);
    }

    @Override
    public Object getAttribute(String attribute) {
        return attributes.get(attribute);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String attribute, Class<T> clazz) {
        Object value = getAttribute(attribute);
        return clazz.isInstance(value) ? (T) value : null;
    }

    @Override
    public Object removeAttribute(String attribute) {
        return attributes.remove(attribute);
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public KeycloakTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public KeycloakSessionFactory getKeycloakSessionFactory() {
        return factory;
    }

    @Override
    @Deprecated
    public UserProvider userLocalStorage() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public RealmProvider realmLocalStorage() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public ClientProvider clientLocalStorage() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public ClientScopeProvider clientScopeLocalStorage() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public GroupProvider groupLocalStorage() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public ClientProvider clientStorageManager() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public ClientScopeProvider clientScopeStorageManager() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public RoleProvider roleLocalStorage() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public RoleProvider roleStorageManager() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public GroupProvider groupStorageManager() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    @Deprecated
    public UserProvider userStorageManager() {
        throw new IllegalStateException("Access to the legacy store is no longer possible via this method. Adjust your code according to the Keycloak 19 Upgrading Guide.");
    }

    @Override
    public UserProvider users() {
        return getDatastoreProvider().users();
    }

    @Override
    @Deprecated
    public UserCredentialManager userCredentialManager() {
        if (userCredentialStorageManager == null) {
            LegacySessionSupportProvider provider = this.getProvider(LegacySessionSupportProvider.class);
            if (provider == null) {
                throw new IllegalStateException("legacy support for a UserCredentialManager is not enabled");
            }
            userCredentialStorageManager = provider.userCredentialManager();
        }
        return userCredentialStorageManager;
    }

    @SuppressWarnings("unchecked")
    public <T extends Provider> T getProvider(Class<T> clazz) {
        Integer hash = clazz.hashCode();
        T provider = (T) providers.get(hash);
        // KEYCLOAK-11890 - Avoid using HashMap.computeIfAbsent() to implement logic in outer if() block below,
        // since per JDK-8071667 the remapping function should not modify the map during computation. While
        // allowed on JDK 1.8, attempt of such a modification throws ConcurrentModificationException with JDK 9+
        if (provider == null) {
            ProviderFactory<T> providerFactory = factory.getProviderFactory(clazz);
            if (providerFactory != null) {
                provider = providerFactory.create(DefaultKeycloakSession.this);
                providers.put(hash, provider);
            }
        }
        return provider;
    }

    @SuppressWarnings("unchecked")
    public <T extends Provider> T getProvider(Class<T> clazz, String id) {
        Integer hash = clazz.hashCode() + id.hashCode();
        T provider = (T) providers.get(hash);
        // KEYCLOAK-11890 - Avoid using HashMap.computeIfAbsent() to implement logic in outer if() block below,
        // since per JDK-8071667 the remapping function should not modify the map during computation. While
        // allowed on JDK 1.8, attempt of such a modification throws ConcurrentModificationException with JDK 9+
        if (provider == null) {
            ProviderFactory<T> providerFactory = factory.getProviderFactory(clazz, id);
            if (providerFactory != null) {
                provider = providerFactory.create(DefaultKeycloakSession.this);
                providers.put(hash, provider);
            }
        }
        return provider;
    }

    @Override
    public <T extends Provider> T getComponentProvider(Class<T> clazz, String componentId) {
        final RealmModel realm = getContext().getRealm();
        if (realm == null) {
            throw new IllegalArgumentException("Realm not set in the context.");
        }

        // Loads componentModel from the realm
        return this.getComponentProvider(clazz, componentId, KeycloakModelUtils.componentModelGetter(realm.getId(), componentId));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Provider> T getComponentProvider(Class<T> clazz, String componentId, Function<KeycloakSessionFactory, ComponentModel> modelGetter) {
        Integer hash = clazz.hashCode() + componentId.hashCode();
        T provider = (T) providers.get(hash);
        final RealmModel realm = getContext().getRealm();

        // KEYCLOAK-11890 - Avoid using HashMap.computeIfAbsent() to implement logic in outer if() block below,
        // since per JDK-8071667 the remapping function should not modify the map during computation. While
        // allowed on JDK 1.8, attempt of such a modification throws ConcurrentModificationException with JDK 9+
        if (provider == null) {
            final String realmId = realm == null ? null : realm.getId();
            ProviderFactory<T> providerFactory = factory.getProviderFactory(clazz, realmId, componentId, modelGetter);
            if (providerFactory != null) {
                provider = providerFactory.create(this);
                providers.put(hash, provider);
            }
        }
        return provider;
    }

    @Override
    public <T extends Provider> T getProvider(Class<T> clazz, ComponentModel componentModel) {
        String modelId = componentModel.getId();

        Object found = getAttribute(modelId);
        if (found != null) {
            return clazz.cast(found);
        }

        ProviderFactory<T> providerFactory = factory.getProviderFactory(clazz, componentModel.getProviderId());
        if (providerFactory == null) {
            return null;
        }

        ComponentFactory<T, T> componentFactory = (ComponentFactory<T, T>) providerFactory;
        T provider = componentFactory.create(this, componentModel);
        enlistForClose(provider);
        setAttribute(modelId, provider);

        return provider;
    }

    public <T extends Provider> Set<String> listProviderIds(Class<T> clazz) {
        return factory.getAllProviderIds(clazz);
    }

    @Override
    public <T extends Provider> Set<T> getAllProviders(Class<T> clazz) {
        return listProviderIds(clazz).stream()
            .map(id -> getProvider(clazz, id))
            .collect(Collectors.toSet());
    }

    @Override
    public Class<? extends Provider> getProviderClass(String providerClassName) {
        return factory.getProviderClass(providerClassName);
    }

    @Override
    public RealmProvider realms() {
        return getDatastoreProvider().realms();
    }

    @Override
    public ClientProvider clients() {
        return getDatastoreProvider().clients();
    }

    @Override
    public ClientScopeProvider clientScopes() {
        return getDatastoreProvider().clientScopes();
    }

    @Override
    public GroupProvider groups() {
        return getDatastoreProvider().groups();
    }

    @Override
    public RoleProvider roles() {
        return getDatastoreProvider().roles();
    }


    @Override
    public UserSessionProvider sessions() {
        if (sessionProvider == null) {
            sessionProvider = getProvider(UserSessionProvider.class);
        }
        return sessionProvider;
    }

    @Override
    public UserLoginFailureProvider loginFailures() {
        if (userLoginFailureProvider == null) {
            userLoginFailureProvider = getProvider(UserLoginFailureProvider.class);
        }
        return userLoginFailureProvider;
    }

    @Override
    public AuthenticationSessionProvider authenticationSessions() {
        if (authenticationSessionProvider == null) {
            authenticationSessionProvider = getProvider(AuthenticationSessionProvider.class);
        }
        return authenticationSessionProvider;
    }

    @Override
    public KeyManager keys() {
        if (keyManager == null) {
            keyManager = new DefaultKeyManager(this);
        }
        return keyManager;
    }

    @Override
    public ThemeManager theme() {
        if (themeManager == null) {
            themeManager = factory.getThemeManagerFactory().create(this);
        }
        return themeManager;
    }

    @Override
    public TokenManager tokens() {
        if (tokenManager == null) {
            tokenManager = new DefaultTokenManager(this);
        }
        return tokenManager;
    }

    @Override
    public VaultTranscriber vault() {
        if (this.vaultTranscriber == null) {
            this.vaultTranscriber = new DefaultVaultTranscriber(this.getProvider(VaultProvider.class));
        }
        return this.vaultTranscriber;
    }

    @Override
    public ClientPolicyManager clientPolicy() {
        if (clientPolicyManager == null) {
            clientPolicyManager = getProvider(ClientPolicyManager.class);
        }
        return clientPolicyManager;
    }

    private static final Logger LOG = Logger.getLogger(DefaultKeycloakSession.class);

    @Override
    public void close() {
        if (LOG.isTraceEnabled()) {
            LOG.tracef("Closing %s%s%s", this,
              getTransactionManager().isActive() ? " (transaction active" + (getTransactionManager().getRollbackOnly() ? ", ROLLBACK-ONLY" : "") + ")" : "",
              StackUtil.getShortStackTrace());
        }

        if (closed) {
            throw new IllegalStateException("Illegal call to #close() on already closed " + this);
        }

        RuntimeException re = closeTransactionManager();

        try {
            Consumer<? super Provider> safeClose = p -> {
                try {
                    p.close();
                } catch (Exception e) {
                    // Ignore exception
                }
            };
            providers.values().forEach(safeClose);
            closable.forEach(safeClose);
            for (Entry<InvalidableObjectType, Set<Object>> me : invalidationMap.entrySet()) {
                factory.invalidate(this, me.getKey(), me.getValue().toArray());
            }
        } finally {
            this.closed = true;
        }

        if (re != null) {
            throw re;
        }
    }

    protected RuntimeException closeTransactionManager() {
        if (! this.transactionManager.isActive()) {
            return null;
        }

        try {
            if (this.transactionManager.getRollbackOnly()) {
                this.transactionManager.rollback();
            } else {
                this.transactionManager.commit();
            }
        } catch (RuntimeException re) {
            return re;
        }

        return null;
    }

    @Override
    public String toString() {
        return String.format("session @ %08x", System.identityHashCode(this));
    }

    protected DefaultKeycloakContext createKeycloakContext(KeycloakSession session) {
        return new DefaultKeycloakContext(session);
    }

    public boolean isClosed() {
        return closed;
    }
}
