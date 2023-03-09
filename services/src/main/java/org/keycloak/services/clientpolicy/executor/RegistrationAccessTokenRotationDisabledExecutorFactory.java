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

package org.keycloak.services.clientpolicy.executor;

import java.util.Collections;
import java.util.List;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class RegistrationAccessTokenRotationDisabledExecutorFactory implements ClientPolicyExecutorProviderFactory {

	public static final String PROVIDER_ID = "registration-access-token-rotation-disabled";

	@Override
	public String getHelpText() {
		return "Disables registration access rotation for the client.";
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return Collections.emptyList();
	}

	@Override
	public ClientPolicyExecutorProvider create(KeycloakSession session) {
		return new RegistrationAccessTokenRotationDisabledExecutor(getId(), session);
	}

	@Override
	public void init(Config.Scope config) {

	}

	@Override
	public void postInit(KeycloakSessionFactory factory) {

	}

	@Override
	public void close() {

	}

	@Override
	public String getId() {
		return PROVIDER_ID;
	}

	@Override
	public boolean isSupported() {
		return true;
	}
}
