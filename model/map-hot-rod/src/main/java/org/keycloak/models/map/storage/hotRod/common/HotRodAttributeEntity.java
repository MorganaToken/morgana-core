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

package org.keycloak.models.map.storage.hotRod.common;

import org.infinispan.api.annotations.indexing.Basic;
import org.infinispan.api.annotations.indexing.Indexed;
import org.infinispan.protostream.annotations.ProtoField;

import java.util.List;
import java.util.Objects;

/**
 * !!! Please do not change this class !!!
 *
 * If some change is needed please create a new version of this class and solve the migration on top-level entities.
 *
 */
@Indexed
public class HotRodAttributeEntity {
    @Basic(sortable = true)
    @ProtoField(number = 1)
    public String name;

    @Basic(sortable = true)
    @ProtoField(number = 2)
    public List<String> values;

    public HotRodAttributeEntity() {
    }

    public HotRodAttributeEntity(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HotRodAttributeEntity that = (HotRodAttributeEntity) o;
        return Objects.equals(name, that.name) && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, values);
    }
}
