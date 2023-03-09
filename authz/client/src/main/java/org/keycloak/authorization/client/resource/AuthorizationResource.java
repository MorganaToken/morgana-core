/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.authorization.client.resource;


import java.util.List;
import java.util.concurrent.Callable;

import org.keycloak.authorization.client.AuthorizationDeniedException;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.authorization.client.util.HttpMethod;
import org.keycloak.authorization.client.util.HttpMethodResponse;
import org.keycloak.authorization.client.util.Throwables;
import org.keycloak.authorization.client.util.TokenCallable;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;
import org.keycloak.representations.idm.authorization.Permission;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * An entry point for obtaining permissions from the server.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class AuthorizationResource {

    private Configuration configuration;
    private ServerConfiguration serverConfiguration;
    private Http http;
    private TokenCallable token;

    public AuthorizationResource(Configuration configuration, ServerConfiguration serverConfiguration, Http http, TokenCallable token) {
        this.configuration = configuration;
        this.serverConfiguration = serverConfiguration;
        this.http = http;
        this.token = token;
    }

    /**
     * Query the server for all permissions.
     *
     * @return an {@link AuthorizationResponse} with a RPT holding all granted permissions
     * @throws AuthorizationDeniedException in case the request was denied by the server
     */
    public AuthorizationResponse authorize() throws AuthorizationDeniedException {
        return authorize(new AuthorizationRequest());
    }

    /**
     * Query the server for permissions given an {@link AuthorizationRequest}.
     *
     * @param request an {@link AuthorizationRequest} (not {@code null})
     * @return an {@link AuthorizationResponse} with a RPT holding all granted permissions
     * @throws AuthorizationDeniedException in case the request was denied by the server
     */
    public AuthorizationResponse authorize(final AuthorizationRequest request) throws AuthorizationDeniedException {
        return invoke(request);
    }

    /**
     * Query the server for a list of permissions given an {@link AuthorizationRequest}.
     *
     * @param request an {@link AuthorizationRequest} (not {@code null})
     * @return a list of permissions granted by the server
     * @throws AuthorizationDeniedException in case the request was denied by the server
     */
    public List<Permission> getPermissions(final AuthorizationRequest request) throws AuthorizationDeniedException {
        AuthorizationRequest.Metadata metadata;

        if (request.getMetadata() == null) {
            metadata = new AuthorizationRequest.Metadata();
        } else {
            metadata = request.getMetadata();
        }

        metadata.setResponseMode("permissions");

        return invoke(request);
    }

    private <T> T invoke(AuthorizationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Authorization request must not be null");
        }

        Callable<T> callable = new Callable<T>() {
            @Override
            public T call() throws Exception {
                if (request.getAudience() == null) {
                    request.setAudience(configuration.getResource());
                }

                HttpMethod<T> method = http.post(serverConfiguration.getTokenEndpoint());

                if (token != null) {
                    method = method.authorizationBearer(token.call());
                }

                HttpMethodResponse<T> response = method
                        .authentication()
                        .uma(request)
                        .response();

                if (request.getMetadata() != null && "permissions".equals(request.getMetadata().getResponseMode())) {
                    response = response.json(new TypeReference<T>(){});
                } else {
                    response = response.json((Class<T>) AuthorizationResponse.class);
                }

                return response.execute();
            }
        };
        try {
            return callable.call();
        } catch (Exception cause) {
            return Throwables.retryAndWrapExceptionIfNecessary(callable, token, "Failed to obtain authorization data", cause);
        }
    }
}
