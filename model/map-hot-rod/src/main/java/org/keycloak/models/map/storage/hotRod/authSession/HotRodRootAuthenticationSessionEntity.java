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

package org.keycloak.models.map.storage.hotRod.authSession;

import org.infinispan.api.annotations.indexing.Basic;
import org.infinispan.api.annotations.indexing.Indexed;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;
import org.infinispan.protostream.annotations.ProtoDoc;
import org.infinispan.protostream.annotations.ProtoField;
import org.keycloak.models.map.annotations.GenerateHotRodEntityImplementation;
import org.keycloak.models.map.annotations.IgnoreForEntityImplementationGenerator;
import org.keycloak.models.map.authSession.MapAuthenticationSessionEntity;
import org.keycloak.models.map.authSession.MapRootAuthenticationSessionEntity;
import org.keycloak.models.map.common.UpdatableEntity;
import org.keycloak.models.map.storage.hotRod.common.AbstractHotRodEntity;
import org.keycloak.models.map.storage.hotRod.common.CommonPrimitivesProtoSchemaInitializer;
import org.keycloak.models.map.storage.hotRod.common.UpdatableHotRodEntityDelegateImpl;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@GenerateHotRodEntityImplementation(
        implementInterface = "org.keycloak.models.map.authSession.MapRootAuthenticationSessionEntity",
        inherits = "org.keycloak.models.map.storage.hotRod.authSession.HotRodRootAuthenticationSessionEntity.AbstractHotRodRootAuthenticationSessionEntityDelegate",
        topLevelEntity = true,
        modelClass = "org.keycloak.sessions.RootAuthenticationSessionModel"
)
@Indexed
@ProtoDoc("schema-version: " + HotRodRootAuthenticationSessionEntity.VERSION)
public class HotRodRootAuthenticationSessionEntity extends AbstractHotRodEntity {

    @IgnoreForEntityImplementationGenerator
    public static final int VERSION = 1;

    @AutoProtoSchemaBuilder(
            includeClasses = {
                    HotRodRootAuthenticationSessionEntity.class,
                    HotRodAuthenticationSessionEntity.class
            },
            schemaFilePath = "proto/",
            schemaPackageName = CommonPrimitivesProtoSchemaInitializer.HOT_ROD_ENTITY_PACKAGE,
            dependsOn = {CommonPrimitivesProtoSchemaInitializer.class}
    )
    public interface HotRodRootAuthenticationSessionEntitySchema extends GeneratedSchema {
        HotRodRootAuthenticationSessionEntitySchema INSTANCE = new HotRodRootAuthenticationSessionEntitySchemaImpl();
    }


    @Basic(projectable = true)
    @ProtoField(number = 1)
    public Integer entityVersion = VERSION;

    @ProtoField(number = 2)
    public String id;

    @Basic(sortable = true)
    @ProtoField(number = 3)
    public String realmId;

    @ProtoField(number = 4)
    public Long timestamp;

    @ProtoField(number = 5)
    public Long expiration;

    @ProtoField(number = 6)
    public Set<HotRodAuthenticationSessionEntity> authenticationSessions;

    public static abstract class AbstractHotRodRootAuthenticationSessionEntityDelegate extends UpdatableHotRodEntityDelegateImpl<HotRodRootAuthenticationSessionEntity> implements MapRootAuthenticationSessionEntity {

        @Override
        public String getId() {
            return getHotRodEntity().id;
        }

        @Override
        public void setId(String id) {
            HotRodRootAuthenticationSessionEntity entity = getHotRodEntity();
            if (entity.id != null) throw new IllegalStateException("Id cannot be changed");
            entity.id = id;
            entity.updated |= id != null;
        }

        @Override
        public boolean isUpdated() {
            HotRodRootAuthenticationSessionEntity rootAuthSession = getHotRodEntity();
            return rootAuthSession.updated ||
                    Optional.ofNullable(getAuthenticationSessions()).orElseGet(Collections::emptySet).stream().anyMatch(MapAuthenticationSessionEntity::isUpdated);
        }

        @Override
        public void clearUpdatedFlag() {
            HotRodRootAuthenticationSessionEntity rootAuthSession = getHotRodEntity();
            rootAuthSession.updated = false;
            Optional.ofNullable(getAuthenticationSessions()).orElseGet(Collections::emptySet).forEach(UpdatableEntity::clearUpdatedFlag);
        }
    }

    @Override
    public boolean equals(Object o) {
        return HotRodRootAuthenticationSessionEntityDelegate.entityEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HotRodRootAuthenticationSessionEntityDelegate.entityHashCode(this);
    }
}
