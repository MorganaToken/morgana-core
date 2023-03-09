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
package org.keycloak.services.filters;

import org.keycloak.common.util.Resteasy;
import org.keycloak.headers.SecurityHeadersProvider;
import org.keycloak.models.KeycloakSession;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
@PreMatching
@Priority(10)
public class KeycloakSecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) {
        KeycloakSession session = Resteasy.getContextData(KeycloakSession.class);

        if (session != null) {
            SecurityHeadersProvider securityHeadersProvider = session.getProvider(SecurityHeadersProvider.class);
            securityHeadersProvider.addHeaders(containerRequestContext, containerResponseContext);
        }
    }
}
