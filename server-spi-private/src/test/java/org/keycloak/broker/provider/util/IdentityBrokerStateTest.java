package org.keycloak.broker.provider.util;

import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.*;


public class IdentityBrokerStateTest {

    @Test
    public void testDecodedWithClientIdNotUuid() {

        // Given
        String state = "gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk";
        String clientId = "something not a uuid";
        String clientClientId = "http://i.am.an.url";
        String tabId = "vpISZLVDAc0";

        // When
        IdentityBrokerState encodedState = IdentityBrokerState.decoded(state, clientId, clientClientId, tabId);

        // Then
        Assert.assertNotNull(encodedState);
        Assert.assertEquals(clientClientId, encodedState.getClientId());
        Assert.assertEquals(tabId, encodedState.getTabId());
        Assert.assertEquals("gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk.vpISZLVDAc0.http://i.am.an.url", encodedState.getEncoded());
    }

    @Test
    public void testDecodedWithClientIdAnActualUuid() {

        // Given
        String state = "gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk";
        String clientId = "ed49448c-14cf-471e-a83a-063d0dc3bc8c";
        String clientClientId = "http://i.am.an.url";
        String tabId = "vpISZLVDAc0";

        // When
        IdentityBrokerState encodedState = IdentityBrokerState.decoded(state, clientId, clientClientId, tabId);

        // Then
        Assert.assertNotNull(encodedState);
        Assert.assertEquals(clientClientId, encodedState.getClientId());
        Assert.assertEquals(tabId, encodedState.getTabId());
        Assert.assertEquals("gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk.vpISZLVDAc0.7UlEjBTPRx6oOgY9DcO8jA", encodedState.getEncoded());
    }

    @Test
    public void testDecodedWithClientIdAnActualUuidBASE64UriFriendly() {

        // Given
        String state = "gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk";
        String clientId = "c5ac1ea7-6c28-4be1-b7cd-d63a1ba57f78";
        String clientClientId = "http://i.am.an.url";
        String tabId = "vpISZLVDAc0";

        // When
        IdentityBrokerState encodedState = IdentityBrokerState.decoded(state, clientId, clientClientId, tabId);

        // Then
        Assert.assertNotNull(encodedState);
        Assert.assertEquals(clientClientId, encodedState.getClientId());
        Assert.assertEquals(tabId, encodedState.getTabId());
        Assert.assertEquals("gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk.vpISZLVDAc0.xawep2woS-G3zdY6G6V_eA", encodedState.getEncoded());
    }

    @Test
    public void testEncodedWithClientIdUUid() {
        // Given
        String encoded = "gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk.vpISZLVDAc0.7UlEjBTPRx6oOgY9DcO8jA";
        String clientId = "ed49448c-14cf-471e-a83a-063d0dc3bc8c";
        String clientClientId = "my-client-id";
        ClientModel clientModel = new IdentityBrokerStateTestHelpers.TestClientModel(clientId, clientClientId);
        RealmModel realmModel = new IdentityBrokerStateTestHelpers.TestRealmModel(clientId, clientClientId, clientModel);

        // When
        IdentityBrokerState decodedState = IdentityBrokerState.encoded(encoded, realmModel);

        // Then
        Assert.assertNotNull(decodedState);
        Assert.assertEquals(clientClientId, decodedState.getClientId());
    }

    @Test
    public void testEncodedWithClientIdNotUUid() {
        // Given
        String encoded = "gNrGamIDGKpKSI9yOrcFzYTKoFGH779_WNCacAelkhk.vpISZLVDAc0.http://i.am.an.url";
        String clientId = "http://i.am.an.url";
        ClientModel clientModel = new IdentityBrokerStateTestHelpers.TestClientModel(clientId, clientId);
        RealmModel realmModel = new IdentityBrokerStateTestHelpers.TestRealmModel(clientId, clientId, clientModel);

        // When
        IdentityBrokerState decodedState = IdentityBrokerState.encoded(encoded, realmModel);

        // Then
        Assert.assertNotNull(decodedState);
        Assert.assertEquals("http://i.am.an.url", decodedState.getClientId());
    }

}
