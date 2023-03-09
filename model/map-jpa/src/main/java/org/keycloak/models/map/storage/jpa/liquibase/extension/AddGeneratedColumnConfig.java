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

package org.keycloak.models.map.storage.jpa.liquibase.extension;

import liquibase.change.AddColumnConfig;
import liquibase.parser.core.ParsedNode;
import liquibase.parser.core.ParsedNodeException;
import liquibase.resource.ResourceAccessor;

/**
 * A {@link liquibase.change.ColumnConfig} extension that contains attributes either to specify 
 *  - a JSON column and the property to be selected from the JSON file 
 *  - a hashOf property with column name to be used for the generating a column with hash value of it.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class AddGeneratedColumnConfig extends AddColumnConfig {

    private String jsonColumn;
    private String jsonProperty;
    private String hashOf;

    /**
     * Obtains the name of the column that contains JSON files.
     *
     * @return the JSON column name.
     */
    public String getJsonColumn() {
        return this.jsonColumn;
    }

    /**
     * Sets the name of the column that contains JSON files.
     *
     * @param jsonColumn the name of the JSON column.
     */
    public void setJsonColumn(final String jsonColumn) {
        this.jsonColumn = jsonColumn;
    }

    /**
     * Obtains the name of the property inside the JSON file.
     *
     * @return the name of the JSON property.
     */
    public String getJsonProperty() {
        return this.jsonProperty;
    }

    /**
     * Sets the name of the property inside the JSON file.
     *
     * @param jsonProperty the name of the JSON property.
     */
    public void setJsonProperty(final String jsonProperty) {
        this.jsonProperty = jsonProperty;
    }

    /**
     * Obtains the column name to be used for the generating a column with hash value of it.
     * 
     * @return the name of the column
     */
    public String getHashOf() {
        return hashOf;
    }

    /**
     * Sets the column name to be used for the generating a column with hash value of it.
     * 
     * @param hashOf the column name for hash
     */
    public void setHashOf(String hashOf) {
        this.hashOf = hashOf;
    }

    @Override
    public void load(ParsedNode parsedNode, ResourceAccessor resourceAccessor) throws ParsedNodeException {
        // load the standard column attributs and then load the JSON attributes.
        super.load(parsedNode, resourceAccessor);
        this.jsonColumn = parsedNode.getChildValue(null, "jsonColumn", String.class);
        this.jsonProperty = parsedNode.getChildValue(null, "jsonProperty", String.class);
        this.hashOf = parsedNode.getChildValue(null, "hashOf", String.class);
    }
}
