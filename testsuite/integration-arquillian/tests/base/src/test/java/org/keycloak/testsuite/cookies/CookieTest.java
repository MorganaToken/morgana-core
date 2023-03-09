/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.testsuite.cookies;

import org.apache.http.Header;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.graphene.page.Page;
import org.junit.Test;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.common.Profile;
import org.keycloak.models.Constants;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.arquillian.annotation.DisableFeature;
import org.keycloak.testsuite.auth.page.AuthRealm;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.util.ContainerAssume;
import org.keycloak.testsuite.util.OAuthClient;
import org.keycloak.testsuite.util.OAuthClient.AuthorizationEndpointResponse;
import org.keycloak.testsuite.util.RealmBuilder;
import org.openqa.selenium.Cookie;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.keycloak.services.managers.AuthenticationManager.KEYCLOAK_IDENTITY_COOKIE;
import static org.keycloak.services.managers.AuthenticationManager.KEYCLOAK_SESSION_COOKIE;
import static org.keycloak.services.managers.AuthenticationSessionManager.AUTH_SESSION_ID;
import static org.keycloak.services.util.CookieHelper.LEGACY_COOKIE;
import static org.keycloak.testsuite.admin.AbstractAdminTest.loadJson;
import static org.keycloak.testsuite.util.URLAssert.assertCurrentUrlStartsWithLoginUrlOf;

import javax.ws.rs.core.HttpHeaders;

/**
 *
 * @author hmlnarik
 * @author Vaclav Muzikar <vmuzikar@redhat.com>
 */
@DisableFeature(value = Profile.Feature.ACCOUNT2, skipRestart = true) // TODO remove this (KEYCLOAK-16228)
public class CookieTest extends AbstractKeycloakTest {

    @Page
    protected LoginPage loginPage;

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmRepresentation realmRepresentation = loadJson(getClass().getResourceAsStream("/testrealm.json"), RealmRepresentation.class);
        RealmBuilder realm = RealmBuilder.edit(realmRepresentation).testEventListener();
        RealmRepresentation testRealm = realm.build();
        testRealms.add(testRealm);
    }

    @Override
    public void setDefaultPageUriParameters() {
        super.setDefaultPageUriParameters();
        accountPage.setAuthRealm(AuthRealm.TEST);
    }

    @Test
    public void testCookieValue() throws Exception {
        testCookieValue(KEYCLOAK_IDENTITY_COOKIE);
    }

    @Test
    public void testLegacyCookieValue() throws Exception {
        testCookieValue(KEYCLOAK_IDENTITY_COOKIE + LEGACY_COOKIE);
    }

    private void testCookieValue(String cookieName) throws Exception {
        final String accountClientId = realmsResouce().realm("test").clients().findByClientId("account").get(0).getId();
        final String clientSecret = realmsResouce().realm("test").clients().get(accountClientId).getSecret().getValue();

        AuthorizationEndpointResponse codeResponse = oauth.clientId("account").redirectUri(accountPage.buildUri().toString()).doLogin("test-user@localhost", "password");
        OAuthClient.AccessTokenResponse accTokenResp = oauth.doAccessTokenRequest(codeResponse.getCode(), clientSecret);
        String accessToken = accTokenResp.getAccessToken();

        accountPage.navigateTo();
        accountPage.assertCurrent();

        try (CloseableHttpClient hc = OAuthClient.newCloseableHttpClient()) {
            BasicCookieStore cookieStore = new BasicCookieStore();
            BasicClientCookie cookie = new BasicClientCookie(cookieName, accessToken);
            cookie.setDomain("localhost");
            cookie.setPath("/");
            cookieStore.addCookie(cookie);

            HttpContext localContext = new BasicHttpContext();
            localContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

            HttpGet get = new HttpGet(oauth.clientId("account").redirectUri(accountPage.buildUri().toString()).getLoginFormUrl());
            try (CloseableHttpResponse resp = hc.execute(get, localContext)) {
                final String pageContent = EntityUtils.toString(resp.getEntity());

                // Ensure that we did not get to the account page ...
                assertThat(pageContent, not(containsString("First name")));
                assertThat(pageContent, not(containsString("Last name")));

                // ... but were redirected to login page
                assertThat(pageContent, containsString("Sign In"));
                assertThat(pageContent, containsString("Forgot Password?"));
            }
        }
    }

    @Test
    public void testCookieValueLoggedOut() throws Exception {
        final String accountClientId = realmsResouce().realm("test").clients().findByClientId("account").get(0).getId();
        final String clientSecret = realmsResouce().realm("test").clients().get(accountClientId).getSecret().getValue();

        AuthorizationEndpointResponse codeResponse = oauth.clientId("account").redirectUri(accountPage.buildUri().toString()).doLogin("test-user@localhost", "password");
        OAuthClient.AccessTokenResponse accTokenResp = oauth.doAccessTokenRequest(codeResponse.getCode(), clientSecret);
        String accessToken = accTokenResp.getAccessToken();

        accountPage.navigateTo();
        accountPage.assertCurrent();
        accountPage.logOut();

        try (CloseableHttpClient hc = OAuthClient.newCloseableHttpClient()) {
            BasicCookieStore cookieStore = new BasicCookieStore();
            BasicClientCookie cookie = new BasicClientCookie(KEYCLOAK_IDENTITY_COOKIE, accessToken);
            cookie.setDomain("localhost");
            cookie.setPath("/");
            cookieStore.addCookie(cookie);

            HttpContext localContext = new BasicHttpContext();
            localContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

            HttpGet get = new HttpGet(oauth.clientId("account").redirectUri(accountPage.buildUri().toString()).getLoginFormUrl());
            try (CloseableHttpResponse resp = hc.execute(get, localContext)) {
                final String pageContent = EntityUtils.toString(resp.getEntity());

                // Ensure that we did not get to the account page ...
                assertThat(pageContent, not(containsString("First name")));
                assertThat(pageContent, not(containsString("Last name")));

                // ... but were redirected to login page
                assertThat(pageContent, containsString("Sign In"));
                assertThat(pageContent, containsString("Forgot Password?"));
            }
        }
    }

    @Test
    public void legacyCookiesTest() {
        ContainerAssume.assumeAuthServerSSL();

        accountPage.navigateTo();
        assertCurrentUrlStartsWithLoginUrlOf(accountPage);

        loginPage.login("test-user@localhost", "password");

        Cookie sameSiteIdentityCookie = driver.manage().getCookieNamed(KEYCLOAK_IDENTITY_COOKIE);
        Cookie legacyIdentityCookie = driver.manage().getCookieNamed(KEYCLOAK_IDENTITY_COOKIE + LEGACY_COOKIE);
        Cookie sameSiteSessionCookie = driver.manage().getCookieNamed(KEYCLOAK_SESSION_COOKIE);
        Cookie legacySessionCookie = driver.manage().getCookieNamed(KEYCLOAK_SESSION_COOKIE + LEGACY_COOKIE);
        Cookie sameSiteAuthSessionIdCookie = driver.manage().getCookieNamed(AUTH_SESSION_ID);
        Cookie legacyAuthSessionIdCookie = driver.manage().getCookieNamed(AUTH_SESSION_ID + LEGACY_COOKIE);

        assertSameSiteCookies(sameSiteIdentityCookie, legacyIdentityCookie);
        assertSameSiteCookies(sameSiteSessionCookie, legacySessionCookie);
        assertSameSiteCookies(sameSiteAuthSessionIdCookie, legacyAuthSessionIdCookie);
    }

    @Test
    public void testNoDuplicationsWhenExpiringCookies() throws IOException {
        ContainerAssume.assumeAuthServerSSL();

        accountPage.navigateTo();
        assertCurrentUrlStartsWithLoginUrlOf(accountPage);

        loginPage.login("test-user@localhost", "password");

        UsersResource usersResource = realmsResouce().realm(AuthRealm.TEST).users();
        UserRepresentation user = usersResource.search("test-user@localhost").get(0);

        usersResource.get(user.getId()).logout();

        Cookie invalidIdentityCookie = driver.manage().getCookieNamed(KEYCLOAK_IDENTITY_COOKIE);
        CookieStore cookieStore = new BasicCookieStore();

        BasicClientCookie invalidClientIdentityCookie = new BasicClientCookie(invalidIdentityCookie.getName(), invalidIdentityCookie.getValue());

        invalidClientIdentityCookie.setDomain(invalidIdentityCookie.getDomain());
        invalidClientIdentityCookie.setPath(invalidClientIdentityCookie.getPath());

        cookieStore.addCookie(invalidClientIdentityCookie);

        try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
            HttpGet get = new HttpGet(
                    suiteContext.getAuthServerInfo().getContextRoot() + "/auth/realms/" + AuthRealm.TEST + "/protocol/openid-connect/auth?response_type=code&client_id=" + Constants.ACCOUNT_CONSOLE_CLIENT_ID +
                            "&redirect_uri=" + suiteContext.getAuthServerInfo().getContextRoot() + "/auth/realms/" + AuthRealm.TEST + "/account&scope=openid");

            try (CloseableHttpResponse response = client.execute(get)) {
                Header[] headers = response.getHeaders(HttpHeaders.SET_COOKIE);
                Set<String> cookies = new HashSet<>();

                for (Header header : headers) {
                    assertTrue("Cookie '" + header.getValue() + "' is duplicated", cookies.add(header.getValue()));
                }

                assertFalse(cookies.isEmpty());
            }
        }
    }

    private void assertSameSiteCookies(Cookie sameSiteCookie, Cookie legacyCookie) {
        assertNotNull("SameSite cookie shouldn't be null", sameSiteCookie);
        assertNotNull("Legacy cookie shouldn't be null", legacyCookie);

        assertEquals(sameSiteCookie.getValue(), legacyCookie.getValue());
        assertEquals(sameSiteCookie.getDomain(), legacyCookie.getDomain());
        assertEquals(sameSiteCookie.getPath(), legacyCookie.getPath());
        assertEquals(sameSiteCookie.getExpiry(), legacyCookie.getExpiry());
        assertTrue("SameSite cookie should always have Secure attribute", sameSiteCookie.isSecure());
        assertFalse("Legacy cookie shouldn't have Secure attribute", legacyCookie.isSecure()); // this relies on test realm config
        assertEquals(sameSiteCookie.isHttpOnly(), legacyCookie.isHttpOnly());
        // WebDriver currently doesn't support SameSite attribute therefore we cannot check it's present in the cookie
    }

}
