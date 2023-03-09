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
package org.keycloak.models.map.storage;

import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.map.common.AbstractEntity;

import java.util.stream.Stream;

public interface MapKeycloakTransaction<V extends AbstractEntity, M> extends KeycloakTransaction {

    /**
     * Instructs this transaction to add a new value into the underlying store on commit.
     * <p>
     * Updates to the returned instances of {@code V} would be visible in the current transaction
     * and will propagate into the underlying store upon commit.
     *
     * The ID of the entity passed in the parameter might change to a different value in the returned value
     * if the underlying storage decided this was necessary.
     * If the ID of the entity was null before, it will be set on the returned value.
     *
     * @param value the value
     * @return Entity representing the {@code value} in the store. It may or may not be the same instance as {@code value}.
     */
    V create(V value);

    /**
     * Provides possibility to lookup for values by a {@code key} in the underlying store with respect to changes done
     * in current transaction. Updates to the returned instance would be visible in the current transaction
     * and will propagate into the underlying store upon commit.
     *
     * If {@code V} implements {@link org.keycloak.models.map.common.ExpirableEntity} this method should not return
     * entities that are expired. See {@link org.keycloak.models.map.common.ExpirableEntity} JavaDoc for more details.
     *
     * @param key identifier of a value
     * @return a value associated with the given {@code key}
     */
    V read(String key);

    /**
     * Returns a stream of values from underlying storage that are updated based on the current transaction changes;
     * i.e. the result contains updates and excludes of records that have been created, updated or deleted in this
     * transaction by methods {@link MapKeycloakTransaction#create}, {@link MapKeycloakTransaction#create},
     * {@link MapKeycloakTransaction#delete}, etc.
     * <p>
     * Updates to the returned instances of {@code V} would be visible in the current transaction
     * and will propagate into the underlying store upon commit.
     *
     * If {@code V} implements {@link org.keycloak.models.map.common.ExpirableEntity} this method should not return
     * entities that are expired. See {@link org.keycloak.models.map.common.ExpirableEntity} JavaDoc for more details.
     *
     * @param queryParameters parameters for the query like firstResult, maxResult, requested ordering, etc.
     * @return values that fulfill the given criteria, that are updated based on changes in the current transaction
     */
    Stream<V> read(QueryParameters<M> queryParameters);

    /**
     * Returns a number of values present in the underlying storage that fulfill the given criteria with respect to
     * changes done in the current transaction.
     *
     * @param queryParameters parameters for the query like firstResult, maxResult, requested ordering, etc.
     * @return number of values present in the storage that fulfill the given criteria
     */
    long getCount(QueryParameters<M> queryParameters);

    /**
     * Instructs this transaction to delete a value associated with the identifier {@code key} from the underlying store
     * on commit.
     *
     * @return Returns {@code true} if the object has been deleted or result cannot be determined, {@code false} otherwise.
     * @param key identifier of a value
     */
    boolean delete(String key);

    /**
     * Instructs this transaction to remove values (identified by {@code mcb} filter) from the underlying store on commit.
     *
     * @param queryParameters parameters for the query like firstResult, maxResult, requested ordering, etc.
     * @return number of removed objects (might return {@code -1} if not supported)
     */
    long delete(QueryParameters<M> queryParameters);

    /**
     * Returns {@code true} if the object with the given {@code key} exists in the underlying storage with respect to changes done
     * in current transaction. {@code false} otherwise.
     *
     * @param key Key of the object. Must not be {@code null}.
     * @return See description
     * @throws NullPointerException if the {@code key} is {@code null}
     */
    default boolean exists(String key) {
        return read(key) != null;
    }

    /**
     * Returns {@code true} if at least one object is satisfying given {@code criteria} from the underlying storage with respect to changes done
     * in current transaction. {@code false} otherwise.
     * The criteria are specified in the given criteria builder based on model properties.
     *
     * @param queryParameters parameters for the query
     * @return See description
     */
    default boolean exists(QueryParameters<M> queryParameters) {
        return getCount(queryParameters) > 0;
    }
}
