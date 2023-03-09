package org.keycloak.testsuite.broker;

import static org.keycloak.testsuite.broker.KcSamlBrokerConfiguration.ATTRIBUTE_TO_MAP_FRIENDLY_NAME;

import org.junit.Test;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.broker.saml.mappers.AdvancedAttributeToRoleMapper;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;

/**
 * @author <a href="mailto:external.martin.idel@bosch.io">Martin Idel</a>,
 * <a href="mailto:daniel.fesenmeyer@bosch.io">Daniel Fesenmeyer</a>
 */
public class KcSamlAdvancedAttributeToRoleMapperTest extends AbstractAdvancedRoleMapperTest {

    private static final String ATTRIBUTES = "[\n" +
            "  {\n" +
            "    \"key\": \"" + ATTRIBUTE_TO_MAP_FRIENDLY_NAME + "\",\n" +
            "    \"value\": \"value 1\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"key\": \"" + KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME_2 + "\",\n" +
            "    \"value\": \"value 2\"\n" +
            "  }\n" +
            "]";


    @Override
    protected BrokerConfiguration getBrokerConfiguration() {
        return new KcSamlBrokerConfiguration();
    }

    @Override
    protected void createMapperInIdp(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation advancedAttributeToRoleMapper = new IdentityProviderMapperRepresentation();
        advancedAttributeToRoleMapper.setName("advanced-attribute-to-role-mapper");
        advancedAttributeToRoleMapper.setIdentityProviderMapper(AdvancedAttributeToRoleMapper.PROVIDER_ID);
        advancedAttributeToRoleMapper.setConfig(ImmutableMap.<String, String> builder()
                .put(IdentityProviderMapperModel.SYNC_MODE, syncMode.toString())
                .put(AdvancedAttributeToRoleMapper.ATTRIBUTE_PROPERTY_NAME, claimsOrAttributeRepresentation)
                .put(AdvancedAttributeToRoleMapper.ARE_ATTRIBUTE_VALUES_REGEX_PROPERTY_NAME,
                        Boolean.valueOf(areClaimsOrAttributeValuesRegexes).toString())
                .put(ConfigConstants.ROLE, roleValue)
                .build());

        persistMapper(advancedAttributeToRoleMapper);
    }

    @Test
    public void attributeFriendlyNameGetsConsideredAndMatchedToRole() {
        createAdvancedRoleMapper(ATTRIBUTES, false);
        createUserInProviderRealm(ImmutableMap.<String, List<String>> builder()
                .put(ATTRIBUTE_TO_MAP_FRIENDLY_NAME, ImmutableList.<String> builder().add("value 1").build())
                .put(KcOidcBrokerConfiguration.ATTRIBUTE_TO_MAP_NAME_2,
                        ImmutableList.<String> builder().add("value 2").build())
                .build());

        logInAsUserInIDPForFirstTime();

        assertThatRoleHasBeenAssignedInConsumerRealm();
    }

}
