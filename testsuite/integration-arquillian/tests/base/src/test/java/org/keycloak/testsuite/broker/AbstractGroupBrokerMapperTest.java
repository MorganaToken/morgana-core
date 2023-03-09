/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.broker;

import static org.keycloak.models.IdentityProviderMapperSyncMode.IMPORT;

import java.util.List;
import java.util.Map;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class AbstractGroupBrokerMapperTest extends AbstractGroupMapperTest {

    protected static final String CLAIMS_OR_ATTRIBUTES = "[\n" +
            "  {\n" +
            "    \"key\": \"" + KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME + "\",\n" +
            "    \"value\": \"value 1\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME_2 + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  }\n" +
            "]";

    protected static final String CLAIMS_OR_ATTRIBUTES_REGEX = "[\n" +
            "  {\n" +
            "    \"key\": \"" + KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME + "\",\n" +
            "    \"value\": \"va.*\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME_2 + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  }\n" +
            "]";

    protected String newValueForAttribute2 = "";

    public UserRepresentation createMapperAndLoginAsUserTwiceWithMapper(IdentityProviderMapperSyncMode syncMode,
            boolean createAfterFirstLogin, String groupPath) {
        return loginAsUserTwiceWithMapper(syncMode, createAfterFirstLogin, createMatchingAttributes(), groupPath);
    }

    @Override
    protected void updateUser() {
        UserRepresentation user = findUser(bc.providerRealmName(), bc.getUserLogin(), bc.getUserEmail());
        ImmutableMap<String, List<String>> matchingAttributes = ImmutableMap.<String, List<String>>builder()
                .put(KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME, ImmutableList.<String>builder().add("value 1").build())
                .put(KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME_2, ImmutableList.<String>builder().add(newValueForAttribute2).build())
                .put("some.other.attribute", ImmutableList.<String>builder().add("some value").build())
                .build();
        user.setAttributes(matchingAttributes);
        adminClient.realm(bc.providerRealmName()).users().get(user.getId()).update(user);
    }



    @Override
    protected String createMapperInIdp(IdentityProviderRepresentation idp, IdentityProviderMapperSyncMode syncMode,
            String groupPath) {
        return createMapperInIdp(idp, CLAIMS_OR_ATTRIBUTES, false, syncMode, groupPath);
    }

    @Override
    protected String setupScenarioWithGroupPath(String groupPath) {
        String mapperId = createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES, false, groupPath);
        createUserInProviderRealm(createMatchingAttributes());
        return mapperId;
    }

    @Override
    protected void setupScenarioWithNonExistingGroup() {
        createAdvancedGroupMapper(CLAIMS_OR_ATTRIBUTES, false, MAPPER_TEST_NOT_EXISTING_GROUP_PATH);
        createUserInProviderRealm(createMatchingAttributes());
    }

    protected String createAdvancedGroupMapper(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, String groupPath) {
        IdentityProviderRepresentation idp = setupIdentityProvider();
        return createMapperInIdp(idp, claimsOrAttributeRepresentation, areClaimsOrAttributeValuesRegexes, IMPORT,
                groupPath);
    }

    abstract protected String createMapperInIdp(
            IdentityProviderRepresentation idp, String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String groupPath);

    protected static Map<String, List<String>> createMatchingAttributes() {
        return ImmutableMap.<String, List<String>> builder()
                .put(KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME,
                        ImmutableList.<String> builder().add("value 1").build())
                .put(KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME_2,
                        ImmutableList.<String> builder().add("value 2").build())
                .build();
    }

}
