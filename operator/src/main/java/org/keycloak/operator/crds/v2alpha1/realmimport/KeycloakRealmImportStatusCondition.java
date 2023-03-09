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

package org.keycloak.operator.crds.v2alpha1.realmimport;

import java.util.Objects;

public class KeycloakRealmImportStatusCondition {
    public static final String DONE = "Done";
    public static final String STARTED = "Started";
    public static final String HAS_ERRORS = "HasErrors";

    // string to avoid enums in CRDs
    private String type;
    private Boolean status;
    private String message;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeycloakRealmImportStatusCondition that = (KeycloakRealmImportStatusCondition) o;
        return getType() == that.getType() && Objects.equals(getStatus(), that.getStatus()) && Objects.equals(getMessage(), that.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType(), getStatus(), getMessage());
    }

    @Override
    public String toString() {
        return "KeycloakRealmImportStatusCondition{" +
                "type='" + type + '\'' +
                ", status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}
