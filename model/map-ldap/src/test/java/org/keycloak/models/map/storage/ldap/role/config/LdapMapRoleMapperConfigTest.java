/*
 * Copyright 2022. Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.map.storage.ldap.role.config;

import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.map.storage.ldap.Config;

public class LdapMapRoleMapperConfigTest {

    @Test
    public void shouldEscapeClientNameForPlaceholder() {
        Config config = new Config();
        config.put(LdapMapRoleMapperConfig.CLIENT_ROLES_DN, "ou={0},dc=keycloak,dc=org");
        LdapMapRoleMapperConfig sut = new LdapMapRoleMapperConfig(config);

        Assert.assertEquals("ou=myclient,dc=keycloak,dc=org",
                sut.getRolesDn("myclient"));
        Assert.assertEquals("ou=\\ me\\=co\\\\ol\\, val\\=V\u00E9ronique,dc=keycloak,dc=org",
                sut.getRolesDn(" me=co\\ol, val=V\u00E9ronique"));
    }

}