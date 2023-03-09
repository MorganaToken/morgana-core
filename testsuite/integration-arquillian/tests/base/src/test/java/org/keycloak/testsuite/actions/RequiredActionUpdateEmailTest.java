/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.testsuite.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.keycloak.events.Details;
import org.keycloak.events.EventType;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.pages.AppPage;

public class RequiredActionUpdateEmailTest extends AbstractRequiredActionUpdateEmailTest {

    @Override
    protected void changeEmailUsingRequiredAction(String newEmail) {
        loginPage.open();

        loginPage.login("test-user@localhost", "password");

        updateEmailPage.assertCurrent();

        updateEmailPage.changeEmail(newEmail);
    }

    @Test
    public void updateEmail() {
        changeEmailUsingRequiredAction("new@localhost");

        events.expectRequiredAction(EventType.UPDATE_EMAIL).detail(Details.PREVIOUS_EMAIL, "test-user@localhost")
                .detail(Details.UPDATED_EMAIL, "new@localhost").assertEvent();
        assertEquals(AppPage.RequestType.AUTH_RESPONSE, appPage.getRequestType());

        events.expectLogin().assertEvent();

        // assert user is really updated in persistent store
        UserRepresentation user = ActionUtil.findUserWithAdminClient(adminClient, "test-user@localhost");
        assertEquals("new@localhost", user.getEmail());
        assertEquals("Tom", user.getFirstName());
        assertEquals("Brady", user.getLastName());
        assertFalse(user.getRequiredActions().contains(UserModel.RequiredAction.UPDATE_EMAIL.name()));
    }
}
