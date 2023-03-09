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

package org.keycloak.forms.login.freemarker;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.resources.LoginActionsService;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Various util methods, so the logic is not hardcoded in freemarker beans
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class LoginFormsUtil {

    public static List<IdentityProviderModel> filterIdentityProvidersForTheme(Stream<IdentityProviderModel> providers, KeycloakSession session, AuthenticationFlowContext context) {
        if (context != null) {
            AuthenticationSessionModel authSession = context.getAuthenticationSession();
            String currentFlowPath = authSession.getAuthNote(AuthenticationProcessor.CURRENT_FLOW_PATH);
            UserModel currentUser = context.getUser();
            // Fixing #14173
            // If the current user is not null, then it's a re-auth, and we should filter the possible options with the pre-14173 logic
            // If the current user is null, then it's one of the following cases:
            //  - either connecting a new IdP to the user's account. 
	    //    - in this case the currentUser is null AND the current flow is the FIRST_BROKER_LOGIN_PATH
	    //    - so we should filter out the one they just used for login, as they need to re-auth themself with an already linked IdP account
            //  - or we're on the Login page
	    //    - in this case the current user is null AND the current flow is NOT the FIRST_BROKER_LOGIN_PATH
	    //    - so we should show all the possible IdPs to the user trying to log in (this is the bug in #14173)
	    //    - so we're skipping this branch, and retunring everything at the end of the method
            if (currentUser != null || Objects.equals(LoginActionsService.FIRST_BROKER_LOGIN_PATH, currentFlowPath)) {
                return filterIdentityProviders(providers, session, context);
            }
        }
        return providers.collect(Collectors.toList());
    }
    
    public static List<IdentityProviderModel> filterIdentityProviders(Stream<IdentityProviderModel> providers, KeycloakSession session, AuthenticationFlowContext context) {

        if (context != null) {
            AuthenticationSessionModel authSession = context.getAuthenticationSession();
            SerializedBrokeredIdentityContext serializedCtx = SerializedBrokeredIdentityContext.readFromAuthenticationSession(authSession, AbstractIdpAuthenticator.BROKERED_CONTEXT_NOTE);

            final IdentityProviderModel existingIdp = (serializedCtx == null) ? null : serializedCtx.deserialize(session, authSession).getIdpConfig();

            final Set<String> federatedIdentities;
            if (context.getUser() != null) {
                federatedIdentities = session.users().getFederatedIdentitiesStream(session.getContext().getRealm(), context.getUser())
                        .map(federatedIdentityModel -> federatedIdentityModel.getIdentityProvider())
                        .collect(Collectors.toSet());
            } else {
                federatedIdentities = null;
            }

            return providers
                    .filter(p -> { // Filter current IDP during first-broker-login flow. Re-authentication with the "linked" broker should not be possible
                        if (existingIdp == null) return true;
                        return !Objects.equals(p.getAlias(), existingIdp.getAlias());
                    })
                    .filter(idp -> { // In case that we already have user established in authentication session, we show just providers already linked to this user
                        if (federatedIdentities == null) return true;
                        return federatedIdentities.contains(idp.getAlias());
                    })
                    .collect(Collectors.toList());
        }
        return providers.collect(Collectors.toList());
    }
}
