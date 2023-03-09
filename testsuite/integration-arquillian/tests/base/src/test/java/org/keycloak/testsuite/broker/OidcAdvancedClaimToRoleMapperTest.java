package org.keycloak.testsuite.broker;

import org.keycloak.broker.oidc.mappers.AdvancedClaimToRoleMapper;
import org.keycloak.broker.provider.ConfigConstants;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderMapperSyncMode;
import org.keycloak.representations.idm.IdentityProviderMapperRepresentation;

import com.google.common.collect.ImmutableMap;

/**
 * <a href="mailto:external.benjamin.weimer@bosch-si.com">Benjamin Weimer</a>,
 * <a href="mailto:external.martin.idel@bosch.io">Martin Idel</a>,
 * <a href="mailto:daniel.fesenmeyer@bosch.io">Daniel Fesenmeyer</a>
 */
public class OidcAdvancedClaimToRoleMapperTest extends AbstractAdvancedRoleMapperTest {
    @Override
    protected BrokerConfiguration getBrokerConfiguration() {
        return new KcOidcBrokerConfiguration();
    }

    @Override
    protected void createMapperInIdp(String claimsOrAttributeRepresentation,
            boolean areClaimsOrAttributeValuesRegexes, IdentityProviderMapperSyncMode syncMode, String roleValue) {
        IdentityProviderMapperRepresentation advancedClaimToRoleMapper = new IdentityProviderMapperRepresentation();
        advancedClaimToRoleMapper.setName("advanced-claim-to-role-mapper");
        advancedClaimToRoleMapper.setIdentityProviderMapper(AdvancedClaimToRoleMapper.PROVIDER_ID);
        advancedClaimToRoleMapper.setConfig(ImmutableMap.<String, String> builder()
                .put(IdentityProviderMapperModel.SYNC_MODE, syncMode.toString())
                .put(AdvancedClaimToRoleMapper.CLAIM_PROPERTY_NAME, claimsOrAttributeRepresentation)
                .put(AdvancedClaimToRoleMapper.ARE_CLAIM_VALUES_REGEX_PROPERTY_NAME,
                        Boolean.valueOf(areClaimsOrAttributeValuesRegexes).toString())
                .put(ConfigConstants.ROLE, roleValue)
                .build());

        persistMapper(advancedClaimToRoleMapper);
    }
}
