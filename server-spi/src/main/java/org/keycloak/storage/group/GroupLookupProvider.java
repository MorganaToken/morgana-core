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
package org.keycloak.storage.group;

import org.keycloak.models.GroupModel;
import org.keycloak.models.RealmModel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface GroupLookupProvider {

    /**
     * Returns a group from the given realm with the corresponding id
     *
     * @param realm Realm.
     * @param id Id.
     * @return GroupModel with the corresponding id.
     */
    GroupModel getGroupById(RealmModel realm, String id);

    /**
     * Returns the group hierarchy with the given string in name for the given realm.
     *
     * For a matching group node the parent group is fetched by id (with all children) and added to the result stream.
     * This is done until the group node does not have a parent (root group)
     *
     * @param realm Realm.
     * @param search Case sensitive searched string.
     * @param firstResult First result to return. Ignored if negative or {@code null}.
     * @param maxResults Maximum number of results to return. Ignored if negative or {@code null}.
     * @return Stream of root groups that have the given string in their name themself or a group in their child-collection has.
     * The returned hierarchy contains siblings that do not necessarily have a matching name. Never returns {@code null}.
     * @deprecated Use {@link #searchForGroupByNameStream(RealmModel, String, Boolean, Integer, Integer)} instead.
     */
    @Deprecated
    default Stream<GroupModel> searchForGroupByNameStream(RealmModel realm, String search, Integer firstResult, Integer maxResults) {
        return searchForGroupByNameStream(realm, search, false, firstResult, maxResults);
    }

    /**
     * Returns the groups filtered by attribute names and attribute values for the given realm.
     *
     * @param realm Realm.
     * @param attributes name-value pairs that are compared to group attributes.
     * @param firstResult First result to return. Ignored if negative or {@code null}.
     * @param maxResults Maximum number of results to return. Ignored if negative or {@code null}.
     * @return Stream of groups with attributes matching all searched attributes. Never returns {@code null}.
     */
    Stream<GroupModel> searchGroupsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults);

    /**
     * Returns the group hierarchy with the given string in name for the given realm.
     *
     * For a matching group node the parent group is fetched by id (with all children) and added to the result stream.
     * This is done until the group node does not have a parent (root group)
     *
     * @param realm Realm.
     * @param search Case sensitive searched string.
     * @param exact Boolean which defines wheather search param should be matched exactly.
     * @param firstResult First result to return. Ignored if negative or {@code null}.
     * @param maxResults Maximum number of results to return. Ignored if negative or {@code null}.
     * @return Stream of root groups that have the given string in their name themself or a group in their child-collection has.
     * The returned hierarchy contains siblings that do not necessarily have a matching name. Never returns {@code null}.
     */
    Stream<GroupModel> searchForGroupByNameStream(RealmModel realm, String search, Boolean exact, Integer firstResult, Integer maxResults);

}
