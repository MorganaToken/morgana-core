/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.map.storage.hotRod.user;

import org.infinispan.api.annotations.indexing.Basic;
import org.infinispan.api.annotations.indexing.Indexed;
import org.infinispan.api.annotations.indexing.Keyword;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;
import org.infinispan.protostream.annotations.ProtoDoc;
import org.infinispan.protostream.annotations.ProtoField;
import org.jboss.logging.Logger;
import org.keycloak.models.map.annotations.GenerateHotRodEntityImplementation;
import org.keycloak.models.map.annotations.IgnoreForEntityImplementationGenerator;
import org.keycloak.models.map.common.UpdatableEntity;
import org.keycloak.models.map.storage.hotRod.common.AbstractHotRodEntity;
import org.keycloak.models.map.storage.hotRod.common.CommonPrimitivesProtoSchemaInitializer;
import org.keycloak.models.map.storage.hotRod.common.HotRodAttributeEntity;
import org.keycloak.models.map.storage.hotRod.common.UpdatableHotRodEntityDelegateImpl;
import org.keycloak.models.map.user.MapUserConsentEntity;
import org.keycloak.models.map.user.MapUserCredentialEntity;
import org.keycloak.models.map.user.MapUserEntity;
import org.keycloak.models.map.user.MapUserFederatedIdentityEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


@GenerateHotRodEntityImplementation(
        implementInterface = "org.keycloak.models.map.user.MapUserEntity",
        inherits = "org.keycloak.models.map.storage.hotRod.user.HotRodUserEntity.AbstractHotRodUserEntityDelegate",
        topLevelEntity = true,
        modelClass = "org.keycloak.models.UserModel"
)
@Indexed
@ProtoDoc("schema-version: " + HotRodUserEntity.VERSION)
public class HotRodUserEntity extends AbstractHotRodEntity {

    @IgnoreForEntityImplementationGenerator
    public static final int VERSION = 1;

    @AutoProtoSchemaBuilder(
            includeClasses = {
                    HotRodUserEntity.class,
                    HotRodUserConsentEntity.class,
                    HotRodUserCredentialEntity.class,
                    HotRodUserFederatedIdentityEntity.class},
            schemaFilePath = "proto/",
            schemaPackageName = CommonPrimitivesProtoSchemaInitializer.HOT_ROD_ENTITY_PACKAGE,
            dependsOn = {CommonPrimitivesProtoSchemaInitializer.class}
    )
    public interface HotRodUserEntitySchema extends GeneratedSchema {
        HotRodUserEntitySchema INSTANCE = new HotRodUserEntitySchemaImpl();
    }

    @IgnoreForEntityImplementationGenerator
    private static final Logger LOG = Logger.getLogger(HotRodUserEntity.class);

    @Basic(projectable = true)
    @ProtoField(number = 1)
    public Integer entityVersion = VERSION;

    @Basic(projectable = true, sortable = true)
    @ProtoField(number = 2)
    public String id;

    @Basic(sortable = true)
    @ProtoField(number = 3)
    public String realmId;

    @Basic(sortable = true)
    @ProtoField(number = 4)
    public String username;

    @Keyword(sortable = true, normalizer = "lowercase")
    @ProtoField(number = 5)
    public String usernameLowercase;

    @Keyword(sortable = true, normalizer = "lowercase")
    @ProtoField(number = 6)
    public String firstName;

    @ProtoField(number = 7)
    public Long createdTimestamp;

    @Keyword(sortable = true, normalizer = "lowercase")
    @ProtoField(number = 8)
    public String lastName;

    @Keyword(sortable = true, normalizer = "lowercase")
    @ProtoField(number = 9)
    public String email;

    /**
     * TODO: Workaround for ISPN-8584
     *
     * This index shouldn't be there as majority of object will be enabled == true
     *
     * When this index is missing Ickle queries like following:
     *  FROM kc.HotRodUserEntity c WHERE (c.realmId = "admin-client-test" AND c.enabled = true AND c.email : "user*")
     * fail with:
     *  Error: {"error":{"message":"Error executing search","cause":"Unexpected condition type (FullTextTermExpr): PROP(email):'user*'"}}
     *
     * In other words it is not possible to combine searching for Analyzed field and non-indexed field in one Ickle query
     */
    @Basic(sortable = true)
    @ProtoField(number = 10)
    public Boolean enabled;

    /**
     * TODO: Workaround for ISPN-8584
     *
     * When this index is missing Ickle queries like following:
     *  FROM kc.HotRodUserEntity c WHERE (c.realmId = "admin-client-test" AND c.enabled = true AND c.email : "user*")
     * fail with:
     *  Error: {"error":{"message":"Error executing search","cause":"Unexpected condition type (FullTextTermExpr): PROP(email):'user*'"}}
     *
     * In other words it is not possible to combine searching for Analyzed field and non-indexed field in one Ickle query
     */
    @Basic(sortable = true)
    @ProtoField(number = 11)
    public Boolean emailVerified;

    // This is necessary to be able to dynamically switch unique email constraints on and off in the realm settings
    @ProtoField(number = 12)
    public String emailConstraint;

    @Basic(sortable = true)
    @ProtoField(number = 13)
    public Set<HotRodAttributeEntity> attributes;

    @ProtoField(number = 14)
    public Set<String> requiredActions;

    @ProtoField(number = 15)
    public List<HotRodUserCredentialEntity> credentials;

    @Basic(sortable = true)
    @ProtoField(number = 16)
    public Set<HotRodUserFederatedIdentityEntity> federatedIdentities;

    @Basic(sortable = true)
    @ProtoField(number = 17)
    public Set<HotRodUserConsentEntity> userConsents;

    @Basic(sortable = true)
    @ProtoField(number = 18)
    public Set<String> groupsMembership = new HashSet<>();

    @Basic(sortable = true)
    @ProtoField(number = 19)
    public Set<String> rolesMembership = new HashSet<>();

    @Basic(sortable = true)
    @ProtoField(number = 20)
    public String federationLink;

    @Basic(sortable = true)
    @ProtoField(number = 21)
    public String serviceAccountClientLink;

    @ProtoField(number = 22)
    public Long notBefore;

    public static abstract class AbstractHotRodUserEntityDelegate extends UpdatableHotRodEntityDelegateImpl<HotRodUserEntity> implements MapUserEntity {

        @Override
        public String getId() {
            return getHotRodEntity().id;
        }

        @Override
        public void setId(String id) {
            HotRodUserEntity entity = getHotRodEntity();
            if (entity.id != null) throw new IllegalStateException("Id cannot be changed");
            entity.id = id;
            entity.updated |= id != null;
        }

        @Override
        public void setEmail(String email, boolean duplicateEmailsAllowed) {
            this.setEmail(email);
            this.setEmailConstraint(email == null || duplicateEmailsAllowed ? KeycloakModelUtils.generateId() : email);
        }

        @Override
        public void setUsername(String username) {
            HotRodUserEntity entity = getHotRodEntity();
            entity.updated |= ! Objects.equals(entity.username, username);
            entity.username = username;
            entity.usernameLowercase = username == null ? null : username.toLowerCase();
        }

        @Override
        public boolean isUpdated() {
            return getHotRodEntity().updated
                    || Optional.ofNullable(getUserConsents()).orElseGet(Collections::emptySet).stream().anyMatch(MapUserConsentEntity::isUpdated)
                    || Optional.ofNullable(getCredentials()).orElseGet(Collections::emptyList).stream().anyMatch(MapUserCredentialEntity::isUpdated)
                    || Optional.ofNullable(getFederatedIdentities()).orElseGet(Collections::emptySet).stream().anyMatch(MapUserFederatedIdentityEntity::isUpdated);
        }

        @Override
        public void clearUpdatedFlag() {
            getHotRodEntity().updated = false;
            Optional.ofNullable(getUserConsents()).orElseGet(Collections::emptySet).forEach(UpdatableEntity::clearUpdatedFlag);
            Optional.ofNullable(getCredentials()).orElseGet(Collections::emptyList).forEach(UpdatableEntity::clearUpdatedFlag);
            Optional.ofNullable(getFederatedIdentities()).orElseGet(Collections::emptySet).forEach(UpdatableEntity::clearUpdatedFlag);
        }

        @Override
        public Boolean moveCredential(String credentialId, String newPreviousCredentialId) {
            // 1 - Get all credentials from the entity.
            List<HotRodUserCredentialEntity> credentialsList = getHotRodEntity().credentials;

            // 2 - Find indexes of our and newPrevious credential
            int ourCredentialIndex = -1;
            int newPreviousCredentialIndex = -1;
            HotRodUserCredentialEntity ourCredential = null;
            int i = 0;
            for (HotRodUserCredentialEntity credential : credentialsList) {
                if (credentialId.equals(credential.id)) {
                    ourCredentialIndex = i;
                    ourCredential = credential;
                } else if(newPreviousCredentialId != null && newPreviousCredentialId.equals(credential.id)) {
                    newPreviousCredentialIndex = i;
                }
                i++;
            }

            if (ourCredentialIndex == -1) {
                LOG.warnf("Not found credential with id [%s] of user [%s]", credentialId, getUsername());
                return false;
            }

            if (newPreviousCredentialId != null && newPreviousCredentialIndex == -1) {
                LOG.warnf("Can't move up credential with id [%s] of user [%s]", credentialId, getUsername());
                return false;
            }

            // 3 - Compute index where we move our credential
            int toMoveIndex = newPreviousCredentialId==null ? 0 : newPreviousCredentialIndex + 1;

            // 4 - Insert our credential to new position, remove it from the old position
            if (toMoveIndex == ourCredentialIndex) return true;
            credentialsList.add(toMoveIndex, ourCredential);
            int indexToRemove = toMoveIndex < ourCredentialIndex ? ourCredentialIndex + 1 : ourCredentialIndex;
            credentialsList.remove(indexToRemove);

            getHotRodEntity().updated = true;
            return true;
        }

    }

    @Override
    public boolean equals(Object o) {
        return HotRodUserEntityDelegate.entityEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HotRodUserEntityDelegate.entityHashCode(this);
    }
}
