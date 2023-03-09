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
package org.keycloak.models.map.storage.file;

import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.MapStorageProvider;
import org.keycloak.models.map.storage.MapStorageProviderFactory;

/**
 * File-based {@link MapStorageProvider} implementation.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class FileMapStorageProvider implements MapStorageProvider {

    private final FileMapStorageProviderFactory factory;

    public FileMapStorageProvider(FileMapStorageProviderFactory factory) {
        this.factory = factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends AbstractEntity, M> MapStorage<V, M> getStorage(Class<M> modelType, MapStorageProviderFactory.Flag... flags) {
        FileMapStorage storage = factory.getStorage(modelType, flags);
        return (MapStorage<V, M>) storage;
    }

    @Override
    public void close() {
    }
}
