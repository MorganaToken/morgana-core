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

package org.keycloak.models.jpa.entities;

import org.hibernate.annotations.Nationalized;

import javax.persistence.*;
import java.util.Collection;
import java.util.LinkedList;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@NamedQueries({
        @NamedQuery(name="getGroupIdsByParent", query="select u.id from GroupEntity u where u.parentId = :parent"),
        @NamedQuery(name="getGroupIdsByRealm", query="select u.id from GroupEntity u where u.realm = :realm  order by u.name ASC"),
        @NamedQuery(name="getGroupIdsByNameContaining", query="select u.id from GroupEntity u where u.realm = :realm and u.name like concat('%',:search,'%') order by u.name ASC"),
        @NamedQuery(name="getGroupIdsByNameContainingFromIdList", query="select u.id from GroupEntity u where u.realm = :realm and lower(u.name) like lower(concat('%',:search,'%')) and u.id in :ids order by u.name ASC"),
        @NamedQuery(name="getGroupIdsByName", query="select u.id from GroupEntity u where u.realm = :realm and u.name = :search order by u.name ASC"),
        @NamedQuery(name="getGroupIdsFromIdList", query="select u.id from GroupEntity u where u.realm = :realm and u.id in :ids order by u.name ASC"),
        @NamedQuery(name="getGroupCountByNameContainingFromIdList", query="select count(u) from GroupEntity u where u.realm = :realm and lower(u.name) like lower(concat('%',:search,'%')) and u.id in :ids"),
        @NamedQuery(name="getTopLevelGroupIds", query="select u.id from GroupEntity u where u.parentId = :parent and u.realm = :realm order by u.name ASC"),
        @NamedQuery(name="getGroupCount", query="select count(u) from GroupEntity u where u.realm = :realm"),
        @NamedQuery(name="getTopLevelGroupCount", query="select count(u) from GroupEntity u where u.realm = :realm and u.parentId = :parent")
})
@Entity
@Table(name="KEYCLOAK_GROUP",
        uniqueConstraints = { @UniqueConstraint(columnNames = {"REALM_ID", "PARENT_GROUP", "NAME"})}
)
public class GroupEntity {

    /**
     * ID set in the PARENT column to mark the group as top level.
     */
    public static String TOP_PARENT_ID = " ";

    @Id
    @Column(name="ID", length = 36)
    @Access(AccessType.PROPERTY) // we do this because relationships often fetch id, but not entity.  This avoids an extra SQL
    protected String id;

    @Nationalized
    @Column(name = "NAME")
    protected String name;

    @Column(name = "PARENT_GROUP")
    private String parentId;

    @Column(name = "REALM_ID")
    private String realm;

    @OneToMany(
            cascade = CascadeType.REMOVE,
            orphanRemoval = true, mappedBy="group")
    protected Collection<GroupAttributeEntity> attributes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Collection<GroupAttributeEntity> getAttributes() {
        if (attributes == null) {
            attributes = new LinkedList<>();
        }
        return attributes;
    }

    public void setAttributes(Collection<GroupAttributeEntity> attributes) {
        this.attributes = attributes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof GroupEntity)) return false;

        GroupEntity that = (GroupEntity) o;

        if (!id.equals(that.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
