/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.models.map.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Determines getter of a field which is unique across a set of the same entities within the same context.
 * This field can be used as unique key in map-like access to a collection. For example, in the set of
 * user consents, this can be client ID.
 * 
 * @author hmlnarik
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CollectionKey {

    /**
     * Priority of this annotation: The higher the value, the more appropriate the annotation is.
     * @return
     */
    public int priority() default 0;

}
