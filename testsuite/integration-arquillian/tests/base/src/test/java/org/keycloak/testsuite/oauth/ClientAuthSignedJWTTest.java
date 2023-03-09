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

package org.keycloak.testsuite.oauth;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.adapters.AdapterUtils;
import org.keycloak.adapters.authentication.JWTClientCredentialsProvider;
import org.keycloak.admin.client.resource.ClientAttributeCertificateResource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.client.JWTClientAuthenticator;
import org.keycloak.common.constants.ServiceAccountConstants;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.common.util.Base64;
import org.keycloak.common.util.Base64Url;
import org.keycloak.common.util.KeyUtils;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.common.util.KeystoreUtil;
import org.keycloak.common.util.PemUtils;
import org.keycloak.common.util.Time;
import org.keycloak.common.util.UriUtils;
import org.keycloak.common.util.KeystoreUtil.KeystoreFormat;
import org.keycloak.constants.ServiceUrlConstants;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.ECDSASignatureProvider;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.SignatureSignerContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.oidc.OIDCAdvancedConfigWrapper;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.representations.KeyStoreConfig;
import org.keycloak.representations.RefreshToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.services.util.CertificateInfoHelper;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.Assert;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.auth.page.AuthRealm;
import org.keycloak.testsuite.client.resources.TestApplicationResourceUrls;
import org.keycloak.testsuite.client.resources.TestOIDCEndpointsApplicationResource;
import org.keycloak.testsuite.rest.resource.TestingOIDCEndpointsApplicationResource;
import org.keycloak.testsuite.util.ClientBuilder;
import org.keycloak.testsuite.util.ClientManager;
import org.keycloak.testsuite.util.KeystoreUtils;
import org.keycloak.testsuite.util.OAuthClient;
import org.keycloak.testsuite.util.RealmBuilder;
import org.keycloak.testsuite.util.UserBuilder;
import org.keycloak.util.JsonSerialization;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 * @author Vaclav Muzikar <vmuzikar@redhat.com>
 */
public class ClientAuthSignedJWTTest extends AbstractKeycloakTest {

    @Rule
    public AssertEvents events = new AssertEvents(this);

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static KeystoreUtils.KeystoreInfo generatedKeystoreClient1;
    private static KeyPair keyPairClient1;

    @BeforeClass
    public static void generateClient1KeyPair() throws Exception {
        generatedKeystoreClient1 = KeystoreUtils.generateKeystore(folder, KeystoreFormat.JKS, "clientkey", "storepass", "keypass");
        PublicKey publicKey = PemUtils.decodePublicKey(generatedKeystoreClient1.getCertificateInfo().getPublicKey());
        PrivateKey privateKey = PemUtils.decodePrivateKey(generatedKeystoreClient1.getCertificateInfo().getPrivateKey());
        keyPairClient1 = new KeyPair(publicKey, privateKey);
    }

    private static String client1SAUserId;

    private static RealmRepresentation testRealm;
    private static ClientRepresentation app1, app2, app3;
    private static UserRepresentation defaultUser, serviceAccountUser;

    @Override
    public void beforeAbstractKeycloakTest() throws Exception {
        super.beforeAbstractKeycloakTest();
    }

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmBuilder realmBuilder = RealmBuilder.create().name("test")
                .testEventListener();

        app1 = ClientBuilder.create()
                .id(KeycloakModelUtils.generateId())
                .clientId("client1")
                .attribute(JWTClientAuthenticator.CERTIFICATE_ATTR, generatedKeystoreClient1.getCertificateInfo().getCertificate())
                .attribute(OIDCConfigAttributes.USE_REFRESH_TOKEN_FOR_CLIENT_CREDENTIALS_GRANT, "true")
                .authenticatorType(JWTClientAuthenticator.PROVIDER_ID)
                .serviceAccountsEnabled(true)
                .build();

        realmBuilder.client(app1);

        app2 = ClientBuilder.create()
                .id(KeycloakModelUtils.generateId())
                .clientId("client2")
                .directAccessGrants()
                .serviceAccountsEnabled(true)
                .redirectUris(OAuthClient.APP_ROOT + "/auth")
                .attribute(JWTClientAuthenticator.CERTIFICATE_ATTR, "MIICnTCCAYUCBgFPPQDGxTANBgkqhkiG9w0BAQsFADASMRAwDgYDVQQDDAdjbGllbnQxMB4XDTE1MDgxNzE4NTAwNVoXDTI1MDgxNzE4NTE0NVowEjEQMA4GA1UEAwwHY2xpZW50MTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMMw3PaBffWxgS2PYSDDBp6As+cNvv9kt2C4f/RDAGmvSIHPFev9kuQiKs3Oaws3ZsV4JG3qHEuYgnh9W4vfe3DwNwtD1bjL5FYBhPBFTw0lAQECYxaBHnkjHwUKp957FqdSPPICm3LjmTcEdlH+9dpp9xHCMbbiNiWDzWI1xSxC8Fs2d0hwz1sd+Q4QeTBPIBWcPM+ICZtNG5MN+ORfayu4X+Me5d0tXG2fQO//rAevk1i5IFjKZuOjTwyKB5SJIY4b8QTeg0g/50IU7Ht00Pxw6CK02dHS+FvXHasZlD3ckomqCDjStTBWdhJo5dST0CbOqalkkpLlCCbGA1yEQRsCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAUIMeJ+EAo8eNpCG/nXImacjrKakbFnZYBGD/gqeTGaZynkX+jgBSructTHR83zSH+yELEhsAy+3BfK4EEihp+PEcRnK2fASVkHste8AQ7rlzC+HGGirlwrVhWCdizNUCGK80DE537IZ7nmZw6LFG9P5/Q2MvCsOCYjRUvMkukq6TdXBXR9tETwZ+0gpSfsOxjj0ZF7ftTRUSzx4rFfcbM9fRNdVizdOuKGc8HJPA5lLOxV6CyaYIvi3y5RlQI1OHeS34lE4w9CNPRFa/vdxXvN7ClyzA0HMFNWxBN7pC/Ht/FbhSvaAagJBHg+vCrcY5C26Oli7lAglf/zZrwUPs0w==")
                .authenticatorType(JWTClientAuthenticator.PROVIDER_ID)
                .build();

        realmBuilder.client(app2);

        defaultUser = UserBuilder.create()
                .id(KeycloakModelUtils.generateId())
                //.serviceAccountId(app1.getClientId())
                .username("test-user@localhost")
                .password("password")
                .build();
        realmBuilder.user(defaultUser);

        client1SAUserId = KeycloakModelUtils.generateId();

        serviceAccountUser = UserBuilder.create()
                .id(client1SAUserId)
                .username(ServiceAccountConstants.SERVICE_ACCOUNT_USER_PREFIX + app1.getClientId())
                .serviceAccountId(app1.getClientId())
                .build();
        realmBuilder.user(serviceAccountUser);

        testRealm = realmBuilder.build();
        testRealms.add(testRealm);
    }

    @Before
    public void recreateApp3() {
        app3 = ClientBuilder.create()
                .id(KeycloakModelUtils.generateId())
                .clientId("client3")
                .directAccessGrants()
                .authenticatorType(JWTClientAuthenticator.PROVIDER_ID)
                .build();

        Response resp = adminClient.realm("test").clients().create(app3);
        getCleanup().addClientUuid(ApiUtil.getCreatedId(resp));
        resp.close();
    }

    // TEST SUCCESS

    @Test
    public void testServiceAccountAndLogoutSuccess() throws Exception {
        String client1Jwt = getClient1SignedJWT();
        OAuthClient.AccessTokenResponse response = doClientCredentialsGrantRequest(client1Jwt);

        assertEquals(200, response.getStatusCode());
        AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
        RefreshToken refreshToken = oauth.parseRefreshToken(response.getRefreshToken());

        events.expectClientLogin()
                .client("client1")
                .user(client1SAUserId)
                .session(accessToken.getSessionState())
                .detail(Details.TOKEN_ID, accessToken.getId())
                .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                .detail(Details.USERNAME, ServiceAccountConstants.SERVICE_ACCOUNT_USER_PREFIX + "client1")
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .assertEvent();

        assertEquals(accessToken.getSessionState(), refreshToken.getSessionState());

        client1Jwt = getClient1SignedJWT();
        OAuthClient.AccessTokenResponse refreshedResponse = doRefreshTokenRequest(response.getRefreshToken(), client1Jwt);
        AccessToken refreshedAccessToken = oauth.verifyToken(refreshedResponse.getAccessToken());
        RefreshToken refreshedRefreshToken = oauth.parseRefreshToken(refreshedResponse.getRefreshToken());

        assertEquals(accessToken.getSessionState(), refreshedAccessToken.getSessionState());
        assertEquals(accessToken.getSessionState(), refreshedRefreshToken.getSessionState());

        events.expectRefresh(refreshToken.getId(), refreshToken.getSessionState())
                .user(client1SAUserId)
                .client("client1")
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .assertEvent();

        // Logout and assert refresh will fail
        HttpResponse logoutResponse = doLogout(response.getRefreshToken(), getClient1SignedJWT());
        assertEquals(204, logoutResponse.getStatusLine().getStatusCode());
        events.expectLogout(accessToken.getSessionState())
                .client("client1")
                .user(client1SAUserId)
                .removeDetail(Details.REDIRECT_URI)
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .assertEvent();

        response = doRefreshTokenRequest(response.getRefreshToken(), getClient1SignedJWT());
        assertEquals(400, response.getStatusCode());
        assertEquals("invalid_grant", response.getError());

        events.expectRefresh(refreshToken.getId(), refreshToken.getSessionState())
                .client("client1")
                .user(client1SAUserId)
                .removeDetail(Details.TOKEN_ID)
                .removeDetail(Details.UPDATED_REFRESH_TOKEN_ID)
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .error(Errors.INVALID_TOKEN).assertEvent();

    }

    public void testCodeToTokenRequestSuccess(String algorithm) throws Exception {
        oauth.clientId("client2");
        oauth.doLogin("test-user@localhost", "password");
        EventRepresentation loginEvent = events.expectLogin()
                .client("client2")
                .assertEvent();

        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        OAuthClient.AccessTokenResponse response = doAccessTokenRequest(code, getClient2SignedJWT(algorithm));

        assertEquals(200, response.getStatusCode());
        oauth.verifyToken(response.getAccessToken());
        oauth.parseRefreshToken(response.getRefreshToken());
        events.expectCodeToToken(loginEvent.getDetails().get(Details.CODE_ID), loginEvent.getSessionId())
                .client("client2")
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .assertEvent();
    }

    public void testCodeToTokenRequestSuccessForceAlgInClient(String algorithm) throws Exception {
        ClientManager.realm(adminClient.realm("test")).clientId("client2")
                .updateAttribute(OIDCConfigAttributes.TOKEN_ENDPOINT_AUTH_SIGNING_ALG, algorithm);
        try {
            testCodeToTokenRequestSuccess(algorithm);
        } finally {
            ClientManager.realm(adminClient.realm("test")).clientId("client2")
                    .updateAttribute(OIDCConfigAttributes.TOKEN_ENDPOINT_AUTH_SIGNING_ALG, null);
        }
    }

    @Test
    public void testCodeToTokenRequestSuccess() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.RS256);
    }

    @Test
    public void testCodeToTokenRequestSuccess512() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.RS512);
    }

    @Test
    public void testCodeToTokenRequestSuccessPS384() throws Exception {
        testCodeToTokenRequestSuccessForceAlgInClient(Algorithm.PS384);
    }

    @Test
    public void testCodeToTokenRequestSuccessPS512() throws Exception {
        testCodeToTokenRequestSuccessForceAlgInClient(Algorithm.PS512);
    }

    @Test
    public void testCodeToTokenRequestSuccessES256usingJwksUri() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.ES256, true);
    }

    @Test
    public void testCodeToTokenRequestSuccessES256usingJwks() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.ES256, false);
    }

    @Test
    public void testCodeToTokenRequestSuccessRS256usingJwksUri() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.RS256, true);
    }

    @Test
    public void testCodeToTokenRequestSuccessRS256usingJwks() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.RS256, false);
    }

    @Test
    public void testCodeToTokenRequestSuccessPS256usingJwksUri() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.PS256, true);
    }

    @Test
    public void testCodeToTokenRequestSuccessPS256usingJwks() throws Exception {
        testCodeToTokenRequestSuccess(Algorithm.PS256, false);
    }

    @Test
    public void testECDSASignature() throws Exception {
        testECDSASignatureLength(getClientSignedToken(Algorithm.ES256), Algorithm.ES256);
        testECDSASignatureLength(getClientSignedToken(Algorithm.ES384), Algorithm.ES384);
        testECDSASignatureLength(getClientSignedToken(Algorithm.ES512), Algorithm.ES512);
    }

    @Test
    public void testCodeToTokenRequestSuccessES256Enforced() throws Exception {
        ClientResource clientResource = null;
        ClientRepresentation clientRep = null;
        try {
            clientResource = ApiUtil.findClientByClientId(adminClient.realm("test"), "client2");
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setTokenEndpointAuthSigningAlg(Algorithm.ES256);
            clientResource.update(clientRep);

            testCodeToTokenRequestSuccess(Algorithm.ES256, true);
        } catch (Exception e) {
            Assert.fail();
        } finally {
            clientResource = ApiUtil.findClientByClientId(adminClient.realm("test"), "client2");
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setTokenEndpointAuthSigningAlg(null);
            clientResource.update(clientRep);
        }
    }

    private void testECDSASignatureLength(String clientSignedToken, String alg) {
        String encodedSignature = clientSignedToken.split("\\.",3)[2];
        byte[] signature = Base64Url.decode(encodedSignature);
        assertEquals(ECDSASignatureProvider.ECDSA.valueOf(alg).getSignatureLength(), signature.length);
    }

    private String getClientSignedToken(String alg) throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        String clientSignedToken;
        try {
            // setup Jwks
            KeyPair keyPair = setupJwksUrl(alg, clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // test
            oauth.clientId("client2");
            oauth.doLogin("test-user@localhost", "password");

            String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
            clientSignedToken = createSignedRequestToken("client2", getRealmInfoUrl(), privateKey, publicKey, alg);
            OAuthClient.AccessTokenResponse response = doAccessTokenRequest(code, clientSignedToken);

            assertEquals(200, response.getStatusCode());
            oauth.verifyToken(response.getAccessToken());
            oauth.idTokenHint(response.getIdToken()).openLogout();
            return clientSignedToken;
        } finally {
            // Revert jwks_url settings
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    private void testCodeToTokenRequestSuccess(String algorithm, boolean useJwksUri) throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        try {
            // setup Jwks
            KeyPair keyPair;
            if (useJwksUri) {
                keyPair = setupJwksUrl(algorithm, clientRepresentation, clientResource);
            } else {
                keyPair = setupJwks(algorithm, clientRepresentation, clientResource);
            }
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // test
            oauth.clientId("client2");
            oauth.doLogin("test-user@localhost", "password");
            EventRepresentation loginEvent = events.expectLogin()
                    .client("client2")
                    .assertEvent();

            String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
            OAuthClient.AccessTokenResponse response = doAccessTokenRequest(code, createSignedRequestToken("client2", getRealmInfoUrl(), privateKey, publicKey, algorithm));

            assertEquals(200, response.getStatusCode());
            oauth.verifyToken(response.getAccessToken());
            oauth.parseRefreshToken(response.getRefreshToken());
            events.expectCodeToToken(loginEvent.getDetails().get(Details.CODE_ID), loginEvent.getSessionId())
                    .client("client2")
                    .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                    .assertEvent();
        } finally {
            // Revert jwks settings
            if (useJwksUri) {
                revertJwksUriSettings(clientRepresentation, clientResource);
            } else {
                revertJwksSettings(clientRepresentation, clientResource);
            }
        }
    }

    @Test
    public void testDirectGrantRequestSuccess() throws Exception {
        oauth.clientId("client2");
        OAuthClient.AccessTokenResponse response = doGrantAccessTokenRequest("test-user@localhost", "password", getClient2SignedJWT());

        assertEquals(200, response.getStatusCode());
        AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
        RefreshToken refreshToken = oauth.parseRefreshToken(response.getRefreshToken());

        events.expectLogin()
                .client("client2")
                .session(accessToken.getSessionState())
                .detail(Details.GRANT_TYPE, OAuth2Constants.PASSWORD)
                .detail(Details.TOKEN_ID, accessToken.getId())
                .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                .detail(Details.USERNAME, "test-user@localhost")
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .removeDetail(Details.CODE_ID)
                .removeDetail(Details.REDIRECT_URI)
                .removeDetail(Details.CONSENT)
                .assertEvent();
    }

    @Test
    public void testSuccessWhenNoAlgSetInJWK() throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        try {
            // setup Jwks
            String signingAlgorithm = Algorithm.PS256;
            KeyPair keyPair = setupJwksUrl(signingAlgorithm, false, false, null, clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // test
            oauth.clientId("client2");
            OAuthClient.AccessTokenResponse response = doGrantAccessTokenRequest("test-user@localhost", "password", createSignedRequestToken("client2", getRealmInfoUrl(), privateKey, publicKey, signingAlgorithm));

            assertEquals(200, response.getStatusCode());
        } finally {
            // Revert jwks_url settings
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    @Test
    public void testSuccessDefaultAlgWhenNoAlgSetInJWK() throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        try {
            // send a JWS using the default algorithm
            String signingAlgorithm = Algorithm.RS256;
            KeyPair keyPair = setupJwksUrl(signingAlgorithm, false, false, null, clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();
            oauth.clientId("client2");
            OAuthClient.AccessTokenResponse response = doGrantAccessTokenRequest("test-user@localhost", "password", createSignedRequestToken("client2", getRealmInfoUrl(), privateKey, publicKey, signingAlgorithm));
            assertEquals(200, response.getStatusCode());

            // sending a JWS using another RSA based alg (PS256) should work as alg is not specified
            publicKey = keyPair.getPublic();
            privateKey = keyPair.getPrivate();
            oauth.clientId("client2");
            response = doGrantAccessTokenRequest("test-user@localhost", "password", createSignedRequestToken("client2", getRealmInfoUrl(), privateKey, publicKey, Algorithm.PS256));
            assertEquals(200, response.getStatusCode());

            // sending an invalid EC (ES256) one should not work
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setTokenEndpointAuthSigningAlg(Algorithm.ES256);
            clientResource.update(clientRepresentation);
            response = doGrantAccessTokenRequest("test-user@localhost", "password", createSignedRequestToken("client2", getRealmInfoUrl(), privateKey, publicKey, Algorithm.PS256));
            assertEquals(400, response.getStatusCode());
            assertEquals("invalid signature algorithm", response.getErrorDescription());
        } finally {
            // Revert jwks_url settings
            revertJwksUriSettings(clientRepresentation, clientResource);
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setTokenEndpointAuthSigningAlg(null);
            clientResource.update(clientRepresentation);
        }
    }

    // GH issue 14794
    @Test
    public void testSuccessWhenMultipleKeysWithSameKid() throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        String origAccessTokenSignedResponseAlg = clientRepresentation.getAttributes().get(OIDCConfigAttributes.ACCESS_TOKEN_SIGNED_RESPONSE_ALG);
        try {
            clientRepresentation.getAttributes().put(OIDCConfigAttributes.ACCESS_TOKEN_SIGNED_RESPONSE_ALG, Algorithm.RS512);
            // setup Jwks
            String signingAlgorithm = Algorithm.RS256;
            KeyPair keyPair = setupJwksUrl(signingAlgorithm, true, true, "my-kid", clientRepresentation, clientResource);

            signingAlgorithm = Algorithm.RS512;
            keyPair = setupJwksUrl(signingAlgorithm, true, true, "my-kid", clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // test
            oauth.clientId("client2");
            JsonWebToken clientAuthJwt = createRequestToken("client2", getRealmInfoUrl());
            OAuthClient.AccessTokenResponse response = doGrantAccessTokenRequest("test-user@localhost", "password",
                    createSignledRequestToken(privateKey, publicKey, signingAlgorithm, "my-kid", clientAuthJwt));

            assertEquals(200, response.getStatusCode());
        } finally {
            // Revert jwks_url settings and signing algorithm
            clientRepresentation.getAttributes().put(OIDCConfigAttributes.ACCESS_TOKEN_SIGNED_RESPONSE_ALG, origAccessTokenSignedResponseAlg);
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    @Test
    public void testDirectGrantRequestSuccessES256() throws Exception {
        testDirectGrantRequestSuccess(Algorithm.ES256);
    }

    @Test
    public void testDirectGrantRequestSuccessRS256() throws Exception {
        testDirectGrantRequestSuccess(Algorithm.RS256);
    }

    @Test
    public void testDirectGrantRequestSuccessPS256() throws Exception {
        testDirectGrantRequestSuccess(Algorithm.PS256);
    }

    private void testDirectGrantRequestSuccess(String algorithm) throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        try {
            // setup Jwks
            KeyPair keyPair = setupJwksUrl(algorithm, clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // test
            oauth.clientId("client2");
            OAuthClient.AccessTokenResponse response = doGrantAccessTokenRequest("test-user@localhost", "password", createSignedRequestToken("client2", getRealmInfoUrl(), privateKey, publicKey, algorithm));

            assertEquals(200, response.getStatusCode());
            AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
            RefreshToken refreshToken = oauth.parseRefreshToken(response.getRefreshToken());

            events.expectLogin()
                    .client("client2")
                    .session(accessToken.getSessionState())
                    .detail(Details.GRANT_TYPE, OAuth2Constants.PASSWORD)
                    .detail(Details.TOKEN_ID, accessToken.getId())
                    .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                    .detail(Details.USERNAME, "test-user@localhost")
                    .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                    .removeDetail(Details.CODE_ID)
                    .removeDetail(Details.REDIRECT_URI)
                    .removeDetail(Details.CONSENT)
                    .assertEvent();
        } finally {
            // Revert jwks_url settings
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    @Test
    public void testClientWithGeneratedKeysJKS() throws Exception {
        KeystoreUtils.assumeKeystoreTypeSupported(KeystoreFormat.JKS);
        testClientWithGeneratedKeys("JKS");
    }

    @Test
    public void testClientWithGeneratedKeysPKCS12() throws Exception {
        KeystoreUtils.assumeKeystoreTypeSupported(KeystoreFormat.PKCS12);
        testClientWithGeneratedKeys("PKCS12");
    }

    @Test
    public void testClientWithGeneratedKeysBCFKS() throws Exception {
        KeystoreUtils.assumeKeystoreTypeSupported(KeystoreFormat.BCFKS);
        testClientWithGeneratedKeys(KeystoreFormat.BCFKS.toString());
    }

    private void testClientWithGeneratedKeys(String format) throws Exception {
        ClientRepresentation client = app3;
        UserRepresentation user = defaultUser;
        final String keyAlias = "somekey";
        final String keyPassword = "pwd1";
        final String storePassword = "pwd2";


        // Generate new keystore (which is intended for sending to the user and store in a client app)
        // with public/private keys; in KC, store the certificate itself

        KeyStoreConfig keyStoreConfig = new KeyStoreConfig();
        keyStoreConfig.setFormat(format);
        keyStoreConfig.setKeyPassword(keyPassword);
        keyStoreConfig.setStorePassword(storePassword);
        keyStoreConfig.setKeyAlias(keyAlias);

        client = getClient(testRealm.getRealm(), client.getId()).toRepresentation();
        final String certOld = client.getAttributes().get(JWTClientAuthenticator.CERTIFICATE_ATTR);

        // Generate the keystore and save the new certificate in client (in KC)
        byte[] keyStoreBytes = getClientAttributeCertificateResource(testRealm.getRealm(), client.getId())
                .generateAndGetKeystore(keyStoreConfig);

        ByteArrayInputStream keyStoreIs = new ByteArrayInputStream(keyStoreBytes);
        KeyStore keyStore = getKeystore(keyStoreIs, storePassword, format);
        keyStoreIs.close();

        client = getClient(testRealm.getRealm(), client.getId()).toRepresentation();
        X509Certificate x509Cert = (X509Certificate) keyStore.getCertificate(keyAlias);

        assertCertificate(client, certOld,
                KeycloakModelUtils.getPemFromCertificate(x509Cert));


        // Try to login with the new keys

        oauth.clientId(client.getClientId());
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
        KeyPair keyPair = new KeyPair(x509Cert.getPublicKey(), privateKey);

        OAuthClient.AccessTokenResponse response = doGrantAccessTokenRequest(user.getUsername(),
                                                      user.getCredentials().get(0).getValue(),
                                                        getClientSignedJWT(keyPair, client.getClientId()));

        assertEquals(200, response.getStatusCode());

        AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
        RefreshToken refreshToken = oauth.parseRefreshToken(response.getRefreshToken());

        events.expectLogin()
                .client(client.getClientId())
                .session(accessToken.getSessionState())
                .detail(Details.GRANT_TYPE, OAuth2Constants.PASSWORD)
                .detail(Details.TOKEN_ID, accessToken.getId())
                .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                .detail(Details.USERNAME, user.getUsername())
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .removeDetail(Details.CODE_ID)
                .removeDetail(Details.REDIRECT_URI)
                .removeDetail(Details.CONSENT)
                .assertEvent();
    }

    @Test
    public void testUploadKeystoreJKS() throws Exception {
        KeystoreUtils.assumeKeystoreTypeSupported(KeystoreFormat.JKS);
        testUploadKeystore("JKS", generatedKeystoreClient1.getKeystoreFile().getAbsolutePath(), "clientkey", "storepass");
    }

    @Test
    public void testUploadKeystorePKCS12() throws Exception {
        KeystoreUtils.assumeKeystoreTypeSupported(KeystoreFormat.PKCS12);
        KeystoreUtils.KeystoreInfo ksInfo = KeystoreUtils.generateKeystore(folder, KeystoreFormat.PKCS12, "clientkey", "pwd2", "keypass");
        testUploadKeystore(KeystoreFormat.PKCS12.toString(), ksInfo.getKeystoreFile().getAbsolutePath(), "clientkey", "pwd2");
    }

    @Test
    public void testUploadKeystoreBCFKS() throws Exception {
        KeystoreUtils.assumeKeystoreTypeSupported(KeystoreFormat.BCFKS);
        KeystoreUtils.KeystoreInfo ksInfo = KeystoreUtils.generateKeystore(folder, KeystoreFormat.BCFKS, "clientkey", "pwd2", "keypass");
        testUploadKeystore(KeystoreFormat.BCFKS.toString(), ksInfo.getKeystoreFile().getAbsolutePath(), "clientkey", "pwd2");
    }

    @Test
    public void testUploadCertificatePEM() throws Exception {
        testUploadKeystore(org.keycloak.services.resources.admin.ClientAttributeCertificateResource.CERTIFICATE_PEM, "client-auth-test/certificate.pem", "undefined", "undefined");
    }

    @Test
    public void testUploadPublicKeyPEM() throws Exception {
        testUploadKeystore(org.keycloak.services.resources.admin.ClientAttributeCertificateResource.PUBLIC_KEY_PEM, "client-auth-test/publickey.pem", "undefined", "undefined");
    }

    @Test
    public void testUploadJWKS() throws Exception {
        testUploadKeystore(org.keycloak.services.resources.admin.ClientAttributeCertificateResource.JSON_WEB_KEY_SET, "clientreg-test/jwks.json", "undefined", "undefined");
    }

    // We need to test this as a genuine REST API HTTP request
    // since there's no easy and direct way to call ClientAttributeCertificateResource.uploadJksCertificate
    // (and especially to create MultipartFormDataInput)
    private void testUploadKeystore(String keystoreFormat, String filePath, String keyAlias, String storePassword) throws Exception {
        ClientRepresentation client = getClient(testRealm.getRealm(), app3.getId()).toRepresentation();
        final String certOld = client.getAttributes().get(JWTClientAuthenticator.CERTIFICATE_ATTR);

        // Load the keystore file
        URL fileUrl = (getClass().getClassLoader().getResource(filePath));
        File keystoreFile = fileUrl != null ? new File(fileUrl.getFile()) : new File(filePath);
        if (!keystoreFile.exists()) {
            throw new IOException("File not found: " + keystoreFile.getAbsolutePath());
        }

        // Get admin access token, no matter it's master realm's admin
        OAuthClient.AccessTokenResponse accessTokenResponse = oauth.doGrantAccessTokenRequest(
                AuthRealm.MASTER, AuthRealm.ADMIN, AuthRealm.ADMIN, null, "admin-cli", null);
        assertEquals(200, accessTokenResponse.getStatusCode());

        final String url = suiteContext.getAuthServerInfo().getContextRoot()
                + "/auth/admin/realms/" + testRealm.getRealm()
                + "/clients/" + client.getId() + "/certificates/jwt.credential/upload-certificate";

        // Prepare the HTTP request
        FileBody fileBody = new FileBody(keystoreFile);
        HttpEntity entity = MultipartEntityBuilder.create()
                .addPart("file", fileBody)
                .addTextBody("keystoreFormat", keystoreFormat)
                .addTextBody("keyAlias", keyAlias)
                .addTextBody("storePassword", storePassword)
                .addTextBody("keyPassword", "undefined")
                .build();
        HttpPost httpRequest = new HttpPost(url);
        httpRequest.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenResponse.getAccessToken());
        httpRequest.setEntity(entity);

        // Send the request
        HttpClient httpClient = HttpClients.createDefault();
        HttpResponse httpResponse = httpClient.execute(httpRequest);
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());

        client = getClient(testRealm.getRealm(), client.getId()).toRepresentation();

        // Assert the uploaded certificate
        if (keystoreFormat.equals(org.keycloak.services.resources.admin.ClientAttributeCertificateResource.PUBLIC_KEY_PEM)) {
            String pem = new String(Files.readAllBytes(keystoreFile.toPath()));
            final String publicKeyNew = client.getAttributes().get(JWTClientAuthenticator.ATTR_PREFIX + "." + CertificateInfoHelper.PUBLIC_KEY);
            assertEquals("Certificates don't match", pem, publicKeyNew);
        } else if (keystoreFormat.equals(org.keycloak.services.resources.admin.ClientAttributeCertificateResource.JSON_WEB_KEY_SET)) {
            final String publicKeyNew = client.getAttributes().get(JWTClientAuthenticator.ATTR_PREFIX + "." + CertificateInfoHelper.PUBLIC_KEY);
            // Just assert it's valid public key
            PublicKey pk = KeycloakModelUtils.getPublicKey(publicKeyNew);
            Assert.assertNotNull(pk);
        } else if (keystoreFormat.equals(org.keycloak.services.resources.admin.ClientAttributeCertificateResource.CERTIFICATE_PEM)) {
            String pem = new String(Files.readAllBytes(keystoreFile.toPath()));
            assertCertificate(client, certOld, pem);
        } else {
            InputStream keystoreIs = new FileInputStream(keystoreFile);
            KeyStore keyStore = getKeystore(keystoreIs, storePassword, keystoreFormat);
            keystoreIs.close();
            String pem = KeycloakModelUtils.getPemFromCertificate((X509Certificate) keyStore.getCertificate(keyAlias));
            assertCertificate(client, certOld, pem);
        }
    }

    // TEST ERRORS

    @Test
    public void testMissingClientAssertionType() throws Exception {
        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response, 401, null, "invalid_client", Errors.CLIENT_NOT_FOUND);
    }

    @Test
    public void testInvalidClientAssertionType() throws Exception {
        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, "invalid"));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response,401, null, "invalid_client", Errors.CLIENT_NOT_FOUND);

    }

    @Test
    public void testMissingClientAssertion() throws Exception {
        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response, 401,null, "invalid_client", Errors.CLIENT_NOT_FOUND);
    }

    @Test
    public void testAssertionMissingIssuer() throws Exception {
        String invalidJwt = getClientSignedJWT(keyPairClient1, null);

        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, invalidJwt));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response,401, null, "invalid_client", Errors.CLIENT_NOT_FOUND);
    }

    @Test
    public void testAssertionUnknownClient() throws Exception {
        String invalidJwt = getClientSignedJWT(keyPairClient1, "unknown-client");

        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, invalidJwt));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response,401, "unknown-client", "invalid_client", Errors.CLIENT_NOT_FOUND);
    }

    @Test
    public void testAssertionDisabledClient() throws Exception {

        ClientManager.realm(adminClient.realm("test")).clientId("client1").enabled(false);

        String invalidJwt = getClient1SignedJWT();

        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, invalidJwt));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response,401, "client1", "invalid_client", Errors.CLIENT_DISABLED);

        ClientManager.realm(adminClient.realm("test")).clientId("client1").enabled(true);
    }

    @Test
    public void testAssertionUnconfiguredClientCertificate() throws Exception {
        class CertificateHolder {
            String certificate;
        }
        final CertificateHolder backupClient1Cert = new CertificateHolder();

        backupClient1Cert.certificate = ApiUtil.findClientByClientId(adminClient.realm("test"), "client1")
                .toRepresentation().getAttributes().get(JWTClientAuthenticator.CERTIFICATE_ATTR);

        ClientManager.realm(adminClient.realm("test")).clientId("client1")
                .updateAttribute(JWTClientAuthenticator.CERTIFICATE_ATTR, null);


        String invalidJwt = getClient1SignedJWT();

        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, invalidJwt));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response,400, "client1", OAuthErrorException.INVALID_CLIENT, "client_credentials_setup_required");

        ClientManager.realm(adminClient.realm("test")).clientId("client1").updateAttribute(JWTClientAuthenticator.CERTIFICATE_ATTR, backupClient1Cert.certificate);
    }

    @Test
    public void testAssertionInvalidSignature() throws Exception {
        // JWT for client1, but signed by privateKey of client2
        String invalidJwt = getClientSignedJWT(getClient2KeyPair(), "client1");

        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, invalidJwt));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        assertError(response, "client1", OAuthErrorException.INVALID_CLIENT, AuthenticationFlowError.CLIENT_CREDENTIALS_SETUP_REQUIRED.toString().toLowerCase());
    }


    @Test
    public void testAssertionExpired() throws Exception {
        String invalidJwt = getClient1SignedJWT();

        setTimeOffset(1000);

        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, invalidJwt));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        setTimeOffset(0);

        assertError(response, "client1", OAuthErrorException.INVALID_CLIENT, Errors.INVALID_CLIENT_CREDENTIALS);
    }

    @Test
    public void testParEndpointAsAudience() throws Exception {
        testEndpointAsAudience(oauth.getParEndpointUrl());
    }

    @Test
    public void testBackchannelAuthenticationEndpointAsAudience() throws Exception {
        testEndpointAsAudience(oauth.getBackchannelAuthenticationUrl());
    }

    private void testEndpointAsAudience(String endpointUrl) throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        try {
            KeyPair keyPair = setupJwksUrl(Algorithm.PS256, clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();
            JsonWebToken assertion = createRequestToken(app2.getClientId(), getRealmInfoUrl());

            assertion.audience(endpointUrl);

            List<NameValuePair> parameters = new LinkedList<NameValuePair>();
            parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
            parameters
                .add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
            parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION,
                createSignledRequestToken(privateKey, publicKey, Algorithm.PS256, null, assertion)));

            try (CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters)) {
                OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);
                assertNotNull(response.getAccessToken());
            }
        } finally {
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    @Test
    public void testInvalidAudience() throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();

        try {
            KeyPair keyPair = setupJwksUrl(Algorithm.PS256, clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();
            JsonWebToken assertion = createRequestToken(app2.getClientId(), getRealmInfoUrl());

            assertion.audience("https://as.other.org");

            List<NameValuePair> parameters = new LinkedList<NameValuePair>();
            parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
            parameters
                .add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
            parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION,
                createSignledRequestToken(privateKey, publicKey, Algorithm.PS256, null, assertion)));

            try (CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters)) {
                OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);
                assertNull(response.getAccessToken());
            }
        } finally {
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    @Test
    public void testAssertionInvalidNotBefore() throws Exception {
        String invalidJwt = getClient1SignedJWT();

        setTimeOffset(-1000);

        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, invalidJwt));

        CloseableHttpResponse resp = sendRequest(oauth.getServiceAccountUrl(), parameters);
        OAuthClient.AccessTokenResponse response = new OAuthClient.AccessTokenResponse(resp);

        setTimeOffset(0);

        assertError(response, "client1", OAuthErrorException.INVALID_CLIENT, Errors.INVALID_CLIENT_CREDENTIALS);
        
        
    }


    @Test
    public void testAssertionReuse() throws Exception {
        String clientJwt = getClient1SignedJWT();

        OAuthClient.AccessTokenResponse response = doClientCredentialsGrantRequest(clientJwt);

        assertEquals(200, response.getStatusCode());
        AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
        Assert.assertNotNull(accessToken);
        Assert.assertNull(response.getError());

        // 2nd attempt to reuse same JWT should fail
        response = doClientCredentialsGrantRequest(clientJwt);

        assertEquals(400, response.getStatusCode());
        assertEquals(OAuthErrorException.INVALID_CLIENT, response.getError());
    }


    @Test
    public void testMissingIdClaim() throws Exception {
        OAuthClient.AccessTokenResponse response = testMissingClaim("id");
        assertError(response, app1.getClientId(), OAuthErrorException.INVALID_CLIENT, Errors.INVALID_CLIENT_CREDENTIALS);
    }

    @Test
    public void testMissingIssuerClaim() throws Exception {
        OAuthClient.AccessTokenResponse response = testMissingClaim("issuer");
        assertError(response,401, null, OAuthErrorException.INVALID_CLIENT, Errors.CLIENT_NOT_FOUND);
    }

    @Test
    public void testMissingSubjectClaim() throws Exception {
        OAuthClient.AccessTokenResponse response = testMissingClaim("subject");
        assertError(response,401, null, "invalid_client", Errors.CLIENT_NOT_FOUND);
    }

    @Test
    public void testMissingAudienceClaim() throws Exception {
        OAuthClient.AccessTokenResponse response = testMissingClaim("audience");
        assertError(response,400, app1.getClientId(), OAuthErrorException.INVALID_CLIENT, Errors.INVALID_CLIENT_CREDENTIALS);
    }

    @Test
    public void testMissingIssuedAtClaim() throws Exception {
        OAuthClient.AccessTokenResponse response = testMissingClaim("issuedAt");
        assertSuccess(response, app1.getClientId(), serviceAccountUser.getId(), serviceAccountUser.getUsername());
    }

    @Test
    // KEYCLOAK-2986
    public void testMissingExpirationClaim() throws Exception {
        // Missing only exp; the lifespan should be calculated from issuedAt
        OAuthClient.AccessTokenResponse response = testMissingClaim("expiration");
        assertSuccess(response, app1.getClientId(), serviceAccountUser.getId(), serviceAccountUser.getUsername());

        // Test expired lifespan
        response = testMissingClaim(-11, "expiration");
        assertError(response, app1.getClientId(), OAuthErrorException.INVALID_CLIENT, Errors.INVALID_CLIENT_CREDENTIALS);

        // Missing exp and issuedAt should return error
        response = testMissingClaim("expiration", "issuedAt");
        assertError(response, app1.getClientId(), OAuthErrorException.INVALID_CLIENT, Errors.INVALID_CLIENT_CREDENTIALS);
    }

    @Test
    public void testMissingNotBeforeClaim() throws Exception {
        OAuthClient.AccessTokenResponse response = testMissingClaim("notBefore");
        assertSuccess(response, app1.getClientId(), serviceAccountUser.getId(), serviceAccountUser.getUsername());
    }

    private OAuthClient.AccessTokenResponse testMissingClaim(String... claims) throws Exception {
        return testMissingClaim(0, claims);
    }

    private OAuthClient.AccessTokenResponse testMissingClaim(int tokenTimeOffset, String... claims) throws Exception {
        CustomJWTClientCredentialsProvider jwtProvider = new CustomJWTClientCredentialsProvider();
        jwtProvider.setupKeyPair(keyPairClient1);
        jwtProvider.setTokenTimeout(10);

        for (String claim : claims) {
            jwtProvider.enableClaim(claim, false);
        }

        Time.setOffset(tokenTimeOffset);
        String jwt;
        try {
            jwt = jwtProvider.createSignedRequestToken(app1.getClientId(), getRealmInfoUrl());
        } finally {
            Time.setOffset(0);
        }
        return doClientCredentialsGrantRequest(jwt);
    }

    private void assertError(OAuthClient.AccessTokenResponse response, String clientId, String responseError, String eventError) {
        assertEquals(400, response.getStatusCode());
        assertMessageError(response,clientId,responseError,eventError);
    }

    private void assertError(OAuthClient.AccessTokenResponse response, int erroCode, String clientId, String responseError, String eventError) {
        assertEquals(erroCode, response.getStatusCode());
        assertMessageError(response, clientId, responseError, eventError);
    }

    private void assertMessageError(OAuthClient.AccessTokenResponse response, String clientId, String responseError, String eventError) {
        assertEquals(responseError, response.getError());

        events.expectClientLogin()
                .client(clientId)
                .session((String) null)
                .clearDetails()
                .error(eventError)
                .user((String) null)
                .assertEvent();
    }


    private void assertSuccess(OAuthClient.AccessTokenResponse response, String clientId, String userId, String userName) {
        assertEquals(200, response.getStatusCode());

        AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
        RefreshToken refreshToken = oauth.parseRefreshToken(response.getRefreshToken());

        events.expectClientLogin()
                .client(clientId)
                .user(userId)
                .session(accessToken.getSessionState())
                .detail(Details.TOKEN_ID, accessToken.getId())
                .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                .detail(Details.USERNAME, userName)
                .detail(Details.CLIENT_AUTH_METHOD, JWTClientAuthenticator.PROVIDER_ID)
                .assertEvent();
    }

    private static void assertCertificate(ClientRepresentation client, String certOld, String pem) {
        pem = PemUtils.removeBeginEnd(pem);
        final String certNew = client.getAttributes().get(JWTClientAuthenticator.CERTIFICATE_ATTR);
        assertNotEquals("The old and new certificates shouldn't match", certOld, certNew);
        assertEquals("Certificates don't match", pem, certNew);
    }

    @Test
    public void testCodeToTokenRequestFailureRS256() throws Exception {
        testCodeToTokenRequestFailure(Algorithm.RS256, OAuthErrorException.INVALID_CLIENT, "client_credentials_setup_required");
    }

    @Test
    public void testCodeToTokenRequestFailureES256Enforced() throws Exception {
        ClientResource clientResource = null;
        ClientRepresentation clientRep = null;
        try {
            clientResource = ApiUtil.findClientByClientId(adminClient.realm("test"), "client2");
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setTokenEndpointAuthSigningAlg(Algorithm.ES256);
            clientResource.update(clientRep);

            testCodeToTokenRequestFailure(Algorithm.RS256, "invalid_client", "invalid_client_credentials");
        } catch (Exception e) {
            Assert.fail();
        } finally {
            clientResource = ApiUtil.findClientByClientId(adminClient.realm("test"), "client2");
            clientRep = clientResource.toRepresentation();
            OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRep).setTokenEndpointAuthSigningAlg(null);
            clientResource.update(clientRep);
        }
    }

    private void testCodeToTokenRequestFailure(String algorithm, String error, String description) throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        try {
            // setup Jwks
            KeyPair keyPair = setupJwksUrl(algorithm, clientRepresentation, clientResource);
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // test
            oauth.clientId("client2");
            oauth.doLogin("test-user@localhost", "password");
            EventRepresentation loginEvent = events.expectLogin()
                    .client("client2")
                    .assertEvent();

            String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
            OAuthClient.AccessTokenResponse response = doAccessTokenRequest(code, getClient2SignedJWT());

            assertEquals(400, response.getStatusCode());
            assertEquals(error, response.getError());

            events.expect(EventType.CODE_TO_TOKEN_ERROR)
                    .client("client2")
                    .session((String) null)
                    .clearDetails()
                    .error(description)
                    .user((String) null)
                    .assertEvent();
        } finally {
            // Revert jwks_url settings
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    @Test
    public void testDirectGrantRequestFailureES256() throws Exception {
        testDirectGrantRequestFailure(Algorithm.ES256);
    }

    private void testDirectGrantRequestFailure(String algorithm) throws Exception {
        ClientRepresentation clientRepresentation = app2;
        ClientResource clientResource = getClient(testRealm.getRealm(), clientRepresentation.getId());
        clientRepresentation = clientResource.toRepresentation();
        try {
            // setup Jwks
            setupJwksUrl(algorithm, clientRepresentation, clientResource);

            // test
            oauth.clientId("client2");
            OAuthClient.AccessTokenResponse response = doGrantAccessTokenRequest("test-user@localhost", "password", getClient2SignedJWT());

            assertEquals(400, response.getStatusCode());
            assertEquals(OAuthErrorException.INVALID_CLIENT, response.getError());

            events.expect(EventType.LOGIN_ERROR)
                    .client("client2")
                    .session((String) null)
                    .clearDetails()
                    .error("client_credentials_setup_required")
                    .user((String) null)
                    .assertEvent();
        } finally {
            // Revert jwks_url settings
            revertJwksUriSettings(clientRepresentation, clientResource);
        }
    }

    // HELPER METHODS

    private OAuthClient.AccessTokenResponse doAccessTokenRequest(String code, String signedJwt) throws Exception {
        List<NameValuePair> parameters = new LinkedList<>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.AUTHORIZATION_CODE));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CODE, code));
        parameters.add(new BasicNameValuePair(OAuth2Constants.REDIRECT_URI, oauth.getRedirectUri()));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, signedJwt));

        CloseableHttpResponse response = sendRequest(oauth.getAccessTokenUrl(), parameters);
        return new OAuthClient.AccessTokenResponse(response);
    }

    private OAuthClient.AccessTokenResponse doRefreshTokenRequest(String refreshToken, String signedJwt) throws Exception {
        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.REFRESH_TOKEN));
        parameters.add(new BasicNameValuePair(OAuth2Constants.REFRESH_TOKEN, refreshToken));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, signedJwt));

        CloseableHttpResponse response = sendRequest(oauth.getRefreshTokenUrl(), parameters);
        return new OAuthClient.AccessTokenResponse(response);
    }

    private HttpResponse doLogout(String refreshToken, String signedJwt) throws Exception {
        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.REFRESH_TOKEN));
        parameters.add(new BasicNameValuePair(OAuth2Constants.REFRESH_TOKEN, refreshToken));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, signedJwt));

        return sendRequest(oauth.getLogoutUrl().build(), parameters);
    }

    private OAuthClient.AccessTokenResponse doClientCredentialsGrantRequest(String signedJwt) throws Exception {
        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.CLIENT_CREDENTIALS));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, signedJwt));

        CloseableHttpResponse response = sendRequest(oauth.getServiceAccountUrl(), parameters);
        return new OAuthClient.AccessTokenResponse(response);
    }

    private OAuthClient.AccessTokenResponse doGrantAccessTokenRequest(String username, String password, String signedJwt) throws Exception {
        List<NameValuePair> parameters = new LinkedList<NameValuePair>();
        parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD));
        parameters.add(new BasicNameValuePair("username", username));
        parameters.add(new BasicNameValuePair("password", password));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION_TYPE, OAuth2Constants.CLIENT_ASSERTION_TYPE_JWT));
        parameters.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ASSERTION, signedJwt));

        CloseableHttpResponse response = sendRequest(oauth.getResourceOwnerPasswordCredentialGrantUrl(), parameters);
        return new OAuthClient.AccessTokenResponse(response);
    }

    private CloseableHttpResponse sendRequest(String requestUrl, List<NameValuePair> parameters) throws Exception {
        CloseableHttpClient client = new DefaultHttpClient();
        try {
            HttpPost post = new HttpPost(requestUrl);
            UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(parameters, "UTF-8");
            post.setEntity(formEntity);
            return client.execute(post);
        } finally {
            oauth.closeClient(client);
        }
    }

    private String getClient2SignedJWT(String algorithm) {
        return getClientSignedJWT(getClient2KeyPair(), "client2", algorithm);
    }

    private String getClient1SignedJWT() throws Exception {
        return getClientSignedJWT(keyPairClient1, "client1", Algorithm.RS256);
    }

    private String getClient2SignedJWT() {
        return getClientSignedJWT(getClient2KeyPair(), "client2", Algorithm.RS256);
    }

    private KeyPair getClient2KeyPair() {
        return KeystoreUtil.loadKeyPairFromKeystore("classpath:client-auth-test/keystore-client2.jks",
                "storepass", "keypass", "clientkey", KeystoreUtil.KeystoreFormat.JKS);
    }

    private String getClientSignedJWT(KeyPair keyPair, String clientId) {
        return getClientSignedJWT(keyPair, clientId, Algorithm.RS256);
    }

    private String getClientSignedJWT(KeyPair keyPair, String clientId, String algorithm) {
        JWTClientCredentialsProvider jwtProvider = new JWTClientCredentialsProvider();
        jwtProvider.setupKeyPair(keyPair, algorithm);
        jwtProvider.setTokenTimeout(10);
        return jwtProvider.createSignedRequestToken(clientId, getRealmInfoUrl());
    }

    private String getRealmInfoUrl() {
        String authServerBaseUrl = UriUtils.getOrigin(oauth.getRedirectUri()) + "/auth";
        return KeycloakUriBuilder.fromUri(authServerBaseUrl).path(ServiceUrlConstants.REALM_INFO_PATH).build("test").toString();
    }

    private ClientAttributeCertificateResource getClientAttributeCertificateResource(String realm, String clientId) {
        return getClient(realm, clientId).getCertficateResource("jwt.credential");
    }

    private ClientResource getClient(String realm, String clientId) {
        return realmsResouce().realm(realm).clients().get(clientId);
    }

    /**
     * Custom JWTClientCredentialsProvider with support for missing JWT claims
     */
    protected class CustomJWTClientCredentialsProvider extends JWTClientCredentialsProvider {
        private Map<String, Boolean> enabledClaims = new HashMap<>();

        public CustomJWTClientCredentialsProvider() {
            super();

            final String[] claims = {"id", "issuer", "subject", "audience", "expiration", "notBefore", "issuedAt"};
            for (String claim : claims) {
                enabledClaims.put(claim, true);
            }
        }

        public void enableClaim(String claim, boolean value) {
            if (!enabledClaims.containsKey(claim)) {
                throw new IllegalArgumentException("Claim \"" + claim + "\" doesn't exist");
            }
            enabledClaims.put(claim, value);
        }

        public boolean isClaimEnabled(String claim) {
            Boolean value = enabledClaims.get(claim);
            if (value == null) {
                throw new IllegalArgumentException("Claim \"" + claim + "\" doesn't exist");
            }
            return value;
        }

        public Set<String> getClaims() {
            return enabledClaims.keySet();
        }

        @Override
        protected JsonWebToken createRequestToken(String clientId, String realmInfoUrl) {
            JsonWebToken reqToken = new JsonWebToken();
            if (isClaimEnabled("id")) reqToken.id(AdapterUtils.generateId());
            if (isClaimEnabled("issuer")) reqToken.issuer(clientId);
            if (isClaimEnabled("subject")) reqToken.subject(clientId);
            if (isClaimEnabled("audience")) reqToken.audience(realmInfoUrl);

            int now = Time.currentTime();
            if (isClaimEnabled("issuedAt")) reqToken.issuedAt(now);
            if (isClaimEnabled("expiration")) reqToken.expiration(now + getTokenTimeout());
            if (isClaimEnabled("notBefore")) reqToken.notBefore(now);

            return reqToken;
        }
    }

    private static KeyStore getKeystore(InputStream is, String storePassword, String format) throws Exception {
        KeyStore keyStore = CryptoIntegration.getProvider().getKeyStore(KeystoreFormat.valueOf(format));
        keyStore.load(is, storePassword.toCharArray());
        return keyStore;
    }

    private KeyPair setupJwksUrl(String algorithm, ClientRepresentation clientRepresentation, ClientResource clientResource) throws Exception {
        return setupJwksUrl(algorithm, true, false, null, clientRepresentation, clientResource);
    }

    private KeyPair setupJwksUrl(String algorithm, boolean advertiseJWKAlgorithm, boolean keepExistingKeys, String kid,
                                 ClientRepresentation clientRepresentation, ClientResource clientResource) throws Exception {
        // generate and register client keypair
        TestOIDCEndpointsApplicationResource oidcClientEndpointsResource = testingClient.testApp().oidcClientEndpoints();
        oidcClientEndpointsResource.generateKeys(algorithm, advertiseJWKAlgorithm, keepExistingKeys, kid);
        Map<String, String> generatedKeys = oidcClientEndpointsResource.getKeysAsBase64();
        KeyPair keyPair = getKeyPairFromGeneratedBase64(generatedKeys, algorithm);

        // use and set jwks_url
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setUseJwksUrl(true);
        String jwksUrl = TestApplicationResourceUrls.clientJwksUri();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setJwksUrl(jwksUrl);
        clientResource.update(clientRepresentation);

        // set time offset, so that new keys are downloaded
        setTimeOffset(20);

        return keyPair;
    }

    private KeyPair setupJwks(String algorithm, ClientRepresentation clientRepresentation, ClientResource clientResource)
        throws Exception {
        // generate and register client keypair
        TestOIDCEndpointsApplicationResource oidcClientEndpointsResource = testingClient.testApp().oidcClientEndpoints();
        oidcClientEndpointsResource.generateKeys(algorithm);
        Map<String, String> generatedKeys = oidcClientEndpointsResource.getKeysAsBase64();
        KeyPair keyPair = getKeyPairFromGeneratedBase64(generatedKeys, algorithm);

        // use and set JWKS
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setUseJwksString(true);
        JSONWebKeySet keySet = oidcClientEndpointsResource.getJwks();
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation)
            .setJwksString(JsonSerialization.writeValueAsString(keySet));
        clientResource.update(clientRepresentation);

        // set time offset, so that new keys are downloaded
        setTimeOffset(20);

        return keyPair;
    }

    private void revertJwksUriSettings(ClientRepresentation clientRepresentation, ClientResource clientResource) {
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setUseJwksUrl(false);
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setJwksUrl(null);
        clientResource.update(clientRepresentation);
    }

    private void revertJwksSettings(ClientRepresentation clientRepresentation, ClientResource clientResource) {
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setUseJwksString(false);
        OIDCAdvancedConfigWrapper.fromClientRepresentation(clientRepresentation).setJwksString(null);
        clientResource.update(clientRepresentation);
    }

    private KeyPair getKeyPairFromGeneratedBase64(Map<String, String> generatedKeys, String algorithm) throws Exception {
        // It seems that PemUtils.decodePrivateKey, decodePublicKey can only treat RSA type keys, not EC type keys. Therefore, these are not used.
        String privateKeyBase64 = generatedKeys.get(TestingOIDCEndpointsApplicationResource.PRIVATE_KEY);
        String publicKeyBase64 =  generatedKeys.get(TestingOIDCEndpointsApplicationResource.PUBLIC_KEY);
        PrivateKey privateKey = decodePrivateKey(Base64.decode(privateKeyBase64), algorithm);
        PublicKey publicKey = decodePublicKey(Base64.decode(publicKeyBase64), algorithm);
        return new KeyPair(publicKey, privateKey);
    }

    private static PrivateKey decodePrivateKey(byte[] der, String algorithm) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        String keyAlg = getKeyAlgorithmFromJwaAlgorithm(algorithm);
        KeyFactory kf = CryptoIntegration.getProvider().getKeyFactory(keyAlg);
        return kf.generatePrivate(spec);
    }

    private static PublicKey decodePublicKey(byte[] der, String algorithm) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        String keyAlg = getKeyAlgorithmFromJwaAlgorithm(algorithm);
        KeyFactory kf = CryptoIntegration.getProvider().getKeyFactory(keyAlg);
        return kf.generatePublic(spec);
    }

    private String createSignedRequestToken(String clientId, String realmInfoUrl, PrivateKey privateKey, PublicKey publicKey, String algorithm) {
        return createSignledRequestToken(privateKey, publicKey, algorithm, null, createRequestToken(clientId, realmInfoUrl));
    }

    private String createSignledRequestToken(PrivateKey privateKey, PublicKey publicKey, String algorithm, String kid, JsonWebToken jwt) {
        if (kid == null) {
            kid = KeyUtils.createKeyId(publicKey);
        }
        SignatureSignerContext signer = oauth.createSigner(privateKey, kid, algorithm);
        String ret = new JWSBuilder().kid(kid).jsonContent(jwt).sign(signer);
        return ret;
    }

    private JsonWebToken createRequestToken(String clientId, String realmInfoUrl) {
        JsonWebToken reqToken = new JsonWebToken();
        reqToken.id(AdapterUtils.generateId());
        reqToken.issuer(clientId);
        reqToken.subject(clientId);
        reqToken.audience(realmInfoUrl);

        int now = Time.currentTime();
        reqToken.issuedAt(now);
        reqToken.expiration(now + 10);
        reqToken.notBefore(now);

        return reqToken;
    }

    private static String getKeyAlgorithmFromJwaAlgorithm(String jwaAlgorithm) {
        String keyAlg = null;
        switch (jwaAlgorithm) {
            case Algorithm.RS256:
            case Algorithm.RS384:
            case Algorithm.RS512:
            case Algorithm.PS256:
            case Algorithm.PS384:
            case Algorithm.PS512:
                keyAlg = KeyType.RSA;
                break;
            case Algorithm.ES256:
            case Algorithm.ES384:
            case Algorithm.ES512:
                keyAlg = KeyType.EC;
                break;
            default :
                throw new RuntimeException("Unsupported signature algorithm");
        }
        return keyAlg;
    }
}
