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
package org.keycloak.userprofile.validator;

import java.util.List;

import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.AttributeContext;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.validate.SimpleValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

/**
 * Validator to check User Profile username change and prevent it if not allowed in realm. Expects List of Strings as
 * input.
 * 
 * @author Vlastimil Elias <velias@redhat.com>
 *
 */
public class UsernameMutationValidator implements SimpleValidator {

    public static final String ID = "up-username-mutation";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ValidationContext validate(Object input, String inputHint, ValidationContext context, ValidatorConfig config) {
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) input;

        if (values.isEmpty()) {
            return context;
        }

        String value = values.get(0);

        if (Validation.isBlank(value)) {
            return context;
        }

        AttributeContext attributeContext = UserProfileAttributeValidationContext.from(context).getAttributeContext();
        UserModel user = attributeContext.getUser();
        RealmModel realm = context.getSession().getContext().getRealm();

        if (! KeycloakModelUtils.isUsernameCaseSensitive(realm)) value = value.toLowerCase();

        if (!realm.isEditUsernameAllowed() && user != null && !value.equals(user.getFirstAttribute(UserModel.USERNAME))) {
            if (realm.isRegistrationEmailAsUsername() && UserProfileContext.UPDATE_PROFILE.equals(attributeContext.getContext())) {
                // if username changed is because email as username is allowed so no validation should happen for update profile
                // it is expected that username changes when attributes are normalized by the provider
                return context;
            }
            context.addError(new ValidationError(ID, inputHint, Messages.READ_ONLY_USERNAME));
        }
        return context;
    }

}
