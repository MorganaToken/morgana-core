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

package org.keycloak.forms.account.freemarker.model;

import org.keycloak.authentication.otp.OTPApplicationProvider;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.models.utils.HmacOTP;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.utils.TotpUtils;

import javax.ws.rs.core.UriBuilder;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.keycloak.utils.CredentialHelper.createUserStorageCredentialRepresentation;


/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class TotpBean {

    private final RealmModel realm;
    private final String totpSecret;
    private final String totpSecretEncoded;
    private final String totpSecretQrCode;
    private final boolean enabled;
    private KeycloakSession session;
    private final UriBuilder uriBuilder;
    private final List<CredentialModel> otpCredentials;
    private final List<String> supportedApplications;

    public TotpBean(KeycloakSession session, RealmModel realm, UserModel user, UriBuilder uriBuilder) {
        this.session = session;
        this.uriBuilder = uriBuilder;
        this.enabled = user.credentialManager().isConfiguredFor(OTPCredentialModel.TYPE);
        if (enabled) {
            List<CredentialModel> otpCredentials = user.credentialManager().getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE).collect(Collectors.toList());

            if (otpCredentials.isEmpty()) {
                // Credential is configured on userStorage side. Create the "fake" credential similar like we do for the new account console
                CredentialRepresentation credential = createUserStorageCredentialRepresentation(OTPCredentialModel.TYPE);
                this.otpCredentials = Collections.singletonList(RepresentationToModel.toModel(credential));
            } else {
                this.otpCredentials = otpCredentials;
            }
        } else {
            this.otpCredentials = Collections.EMPTY_LIST;
        }

        this.realm = realm;
        this.totpSecret = HmacOTP.generateSecret(20);
        this.totpSecretEncoded = TotpUtils.encode(totpSecret);
        this.totpSecretQrCode = TotpUtils.qrCode(totpSecret, realm, user);

        OTPPolicy otpPolicy = realm.getOTPPolicy();
        this.supportedApplications = session.getAllProviders(OTPApplicationProvider.class).stream()
                .filter(p -> p.supports(otpPolicy))
                .map(OTPApplicationProvider::getName)
                .collect(Collectors.toList());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public String getTotpSecretEncoded() {
        return totpSecretEncoded;
    }

    public String getTotpSecretQrCode() {
        return totpSecretQrCode;
    }

    public String getManualUrl() {
        return uriBuilder.replaceQueryParam("mode", "manual").build().toString();
    }

    public String getQrUrl() {
        return uriBuilder.replaceQueryParam("mode", "qr").build().toString();
    }

    public OTPPolicy getPolicy() {
        return realm.getOTPPolicy();
    }

    public List<String> getSupportedApplications() {
        return supportedApplications;
    }

    public List<CredentialModel> getOtpCredentials() {
        return otpCredentials;
    }

}

