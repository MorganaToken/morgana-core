export default class ProviderPage {
  // KerberosSettingsRequired input values
  private kerberosNameInput = "kerberos-name";
  private kerberosRealmInput = "kerberos-realm";
  private kerberosPrincipalInput = "kerberos-principal";
  private kerberosKeytabInput = "kerberos-keytab";

  // LdapSettingsGeneral input values
  private ldapNameInput = "ldap-name";
  private ldapVendorInput = "#kc-vendor";
  private ldapVendorList = "#kc-vendor + ul";

  // LdapSettingsConnection input values
  connectionUrlInput = "ldap-connection-url";
  truststoreSpiInput = "#kc-use-truststore-spi";
  truststoreSpiList = "#kc-use-truststore-spi + ul";
  connectionTimeoutInput = "connection-timeout";
  bindTypeInput = "#kc-bind-type";
  private bindTypeList = "#kc-bind-type + ul";
  bindDnInput = "ldap-bind-dn";
  bindCredsInput = "ldap-bind-credentials";
  private testConnectionBtn = "test-connection-button";
  private testAuthBtn = "test-auth-button";

  // LdapSettingsSearching input values
  ldapEditModeInput = "#kc-edit-mode";
  private ldapEditModeList = "#kc-edit-mode + ul";
  ldapSearchScopeInput = "#kc-search-scope";
  private ldapSearchScopeInputList = "#kc-search-scope + ul";
  ldapPagination = "ui-pagination";
  ldapUsersDnInput = "ldap-users-dn";
  ldapUserLdapAttInput = "ldap-username-attribute";
  ldapRdnLdapAttInput = "ldap-rdn-attribute";
  ldapUuidLdapAttInput = "ldap-uuid-attribute";
  ldapUserObjClassesInput = "ldap-user-object-classes";
  ldapUserLdapFilter = "user-ldap-filter";
  ldapReadTimeout = "ldap-read-timeout";

  // LdapSettingsKerberosIntegration input values
  ldapKerberosRealmInput = "kerberos-realm";
  ldapServerPrincipalInput = "kerberos-principal";
  ldapKeyTabInput = "kerberos-keytab";
  allowKerberosAuth = "allow-kerberos-auth";
  debug = "debug";
  useKerberosForPwAuth = "use-kerberos-pw-auth";

  // LdapSettingsSynchronization input values
  ldapBatchSizeInput = "batch-size";
  ldapFullSyncPeriodInput = "full-sync-period";
  ldapUsersSyncPeriodInput = "changed-users-sync-period";
  importUsers = "import-users";
  periodicFullSync = "periodic-full-sync";
  periodicUsersSync = "periodic-changed-users-sync";

  // SettingsCache input values
  private cacheDayInput = "#kc-eviction-day";
  private cacheDayList = "#kc-eviction-day + ul";
  private cacheHourInput = "#kc-eviction-hour";
  private cacheHourList = "#kc-eviction-hour + ul";
  private cacheMinuteInput = "#kc-eviction-minute";
  private cacheMinuteList = "#kc-eviction-minute + ul";
  private cachePolicyInput = "#kc-cache-policy";
  private cachePolicyList = "#kc-cache-policy + ul";

  // Mapper input values
  private userModelAttInput = "user.model.attribute";
  private ldapAttInput = "ldap.attribute";
  private userModelAttNameInput = "user.model.attribute";
  private attValueInput = "attribute.value";
  private ldapFullNameAttInput = "ldap.full.name.attribute";
  private ldapAttNameInput = "ldap.attribute.name";
  private ldapAttValueInput = "ldap.attribute.value";
  private groupInput = "group";
  private ldapGroupsDnInput = "groups.dn";
  private ldapRolesDnInput = "roles.dn";

  // Mapper types
  private msadUserAcctMapper = "msad-user-account-control-mapper";
  private msadLdsUserAcctMapper = "msad-lds-user-account-control-mapper";
  private userAttLdapMapper = "user-attribute-ldap-mapper";
  private hcAttMapper = "hardcoded-attribute-mapper";
  private certLdapMapper = "certificate-ldap-mapper";
  private fullNameLdapMapper = "full-name-ldap-mapper";
  private hcLdapAttMapper = "hardcoded-ldap-attribute-mapper";
  private hcLdapGroupMapper = "hardcoded-ldap-group-mapper";
  private groupLdapMapper = "group-ldap-mapper";
  private roleLdapMapper = "role-ldap-mapper";
  private hcLdapRoleMapper = "hardcoded-ldap-role-mapper";

  private actionDropdown = "action-dropdown";
  private deleteCmd = "delete-cmd";

  private mappersTab = "ldap-mappers-tab";
  private rolesTab = "rolesTab";
  private createRoleBtn = "no-roles-for-this-client-empty-action";
  private roleSaveBtn = "save";
  private roleNameField = "#kc-name";

  private groupName = "aa-uf-mappers-group";
  private clientName = "aa-uf-mappers-client";

  private maxLifespan = "kerberos-cache-lifespan";

  // Kerberos settings switch input values
  debugSwitch = "debug";
  firstLoginSwitch = "update-first-login";
  passwordAuthSwitch = "allow-password-authentication";

  // LDAP switch input values
  enableStartTls = "enable-start-tls";
  connectionPooling = "connection-pooling";

  // LDAP advanced settings switch input values
  ldapv3PwSwitch = "ldapv3-password";
  validatePwPolicySwitch = "password-policy";
  trustEmailSwitch = "trust-email";

  changeCacheTime(unit: string, time: string) {
    switch (unit) {
      case "day":
        cy.get(this.cacheDayInput).click();
        cy.get(this.cacheDayList).contains(time).click();
        break;
      case "hour":
        cy.get(this.cacheHourInput).click();
        cy.get(this.cacheHourList).contains(time).click();
        break;
      case "minute":
        cy.get(this.cacheMinuteInput).click();
        cy.get(this.cacheMinuteList).contains(time).click();
        break;
      default:
        console.log("Invalid cache time, must be 'day', 'hour', or 'minute'.");
        break;
    }
    return this;
  }

  verifyChangedHourInput(expected: string, unexpected: string) {
    expect(cy.get(this.cacheHourInput).contains(expected).should("exist"));
    expect(
      cy.get(this.cacheHourInput).contains(unexpected).should("not.exist")
    );
    return this;
  }

  deleteCardFromCard(card: string) {
    cy.findByTestId(`${card}-dropdown`).click();
    cy.findByTestId("card-delete").click();
    return this;
  }

  deleteCardFromMenu(card: string) {
    this.clickExistingCard(card);
    cy.findByTestId(this.actionDropdown).click();
    cy.findByTestId(this.deleteCmd).click();
    return this;
  }

  fillKerberosRequiredData(
    name: string,
    realm: string,
    principal: string,
    keytab: string
  ) {
    if (name) {
      cy.findByTestId(this.kerberosNameInput).clear().type(name);
    }
    if (realm) {
      cy.findByTestId(this.kerberosRealmInput).clear().type(realm);
    }
    if (principal) {
      cy.findByTestId(this.kerberosPrincipalInput).clear().type(principal);
    }
    if (keytab) {
      cy.findByTestId(this.kerberosKeytabInput).clear().type(keytab);
    }
    return this;
  }

  fillMaxLifespanData(lifespan: number) {
    for (let i = 0; i < lifespan; i++) {
      cy.findByTestId(this.maxLifespan).click();
    }
    return this;
  }

  fillSelect(selectField: string, value: string) {
    cy.get(selectField).click();
    cy.get(`${selectField} + ul`).contains(value).click();
  }

  fillTextField(textField: string, value: string) {
    cy.findByTestId(textField).type("x");
    cy.findByTestId(textField).clear().type(value).blur();
    return this;
  }

  toggleSwitch(switchName: string) {
    cy.findByTestId(switchName).click({ force: true });
    return this;
  }

  verifyToggle(switchName: string, value: "on" | "off") {
    cy.findByTestId(switchName).should("have.value", value);
    return this;
  }

  verifyTextField(fieldName: string, value: string) {
    cy.findByTestId(fieldName).should("have.value", value);
  }

  verifySelect(selectInput: string, value: string) {
    cy.get(selectInput).should("contain", value);
  }

  fillLdapGeneralData(name: string, vendor?: string) {
    cy.findByTestId(this.ldapNameInput).clear().type(name);
    if (vendor) {
      cy.get(this.ldapVendorInput).click();
      cy.get(this.ldapVendorList).contains(vendor).click();
    }
    return this;
  }

  fillLdapConnectionData(
    connectionUrl: string,
    bindType: string,
    truststoreSpi?: string,
    connectionTimeout?: string,
    bindDn?: string,
    bindCreds?: string
  ) {
    cy.findByTestId(this.connectionUrlInput).clear().type(connectionUrl);

    cy.get(this.bindTypeInput).click();
    cy.get(this.bindTypeList).contains(bindType).click();

    if (truststoreSpi) {
      cy.get(this.truststoreSpiInput).click();
      cy.get(this.truststoreSpiList).contains(truststoreSpi).click();
    }
    if (connectionTimeout) {
      cy.findByTestId(this.connectionTimeoutInput)
        .clear()
        .type(connectionTimeout);
    }
    if (bindDn) {
      cy.findByTestId(this.bindDnInput).clear().type(bindDn);
    }
    if (bindCreds) {
      cy.findByTestId(this.bindCredsInput).clear().type(bindCreds);
    }
    return this;
  }

  fillLdapSearchingData(
    editMode: string,
    usersDn: string,
    userLdapAtt?: string,
    rdnLdapAtt?: string,
    uuidLdapAtt?: string,
    userObjClasses?: string,
    userLdapFilter?: string,
    searchScope?: string,
    readTimeout?: string
  ) {
    cy.get(this.ldapEditModeInput).click();
    cy.get(this.ldapEditModeList).contains(editMode).click();
    cy.findByTestId(this.ldapUsersDnInput).clear().type(usersDn);
    if (userLdapAtt) {
      cy.findByTestId(this.ldapUserLdapAttInput).clear().type(userLdapAtt);
    }
    if (rdnLdapAtt) {
      cy.findByTestId(this.ldapRdnLdapAttInput).clear().type(rdnLdapAtt);
    }
    if (uuidLdapAtt) {
      cy.findByTestId(this.ldapUuidLdapAttInput).clear().type(uuidLdapAtt);
    }
    if (userObjClasses) {
      cy.findByTestId(this.ldapUserObjClassesInput)
        .clear()
        .type(userObjClasses);
    }
    if (userLdapFilter) {
      cy.findByTestId(this.ldapUserLdapFilter).clear().type(userLdapFilter);
    }
    if (searchScope) {
      cy.get(this.ldapSearchScopeInput).click();
      cy.get(this.ldapSearchScopeInputList).contains(searchScope).click();
    }
    if (readTimeout) {
      cy.findByTestId(this.ldapReadTimeout).clear().type(readTimeout);
    }
    return this;
  }

  selectCacheType(cacheType: string) {
    cy.get(this.cachePolicyInput).click();
    cy.get(this.cachePolicyList).contains(cacheType).click();
    return this;
  }

  goToMappers() {
    cy.findByTestId(this.mappersTab).click();
  }

  createRole(roleName: string) {
    cy.findByTestId(this.rolesTab).click();
    cy.wait(1000);
    cy.findByTestId(this.createRoleBtn).click();
    cy.wait(1000);
    cy.get(this.roleNameField).clear().type(roleName);
    cy.wait(1000);
    cy.findByTestId(this.roleSaveBtn).click();
    cy.wait(1000);
  }

  createNewMapper(mapperType: string) {
    const userModelAttValue = "firstName";
    const ldapAttValue = "cn";
    const ldapDnValue = "ou=groups";

    cy.contains("Add").click();
    cy.wait(1000);

    cy.get("#kc-providerId").click();
    cy.get("button").contains(mapperType).click();

    cy.findByTestId("ldap-mapper-name").clear().type(`${mapperType}-test`);

    switch (mapperType) {
      case this.msadUserAcctMapper:
      case this.msadLdsUserAcctMapper:
        break;
      case this.userAttLdapMapper:
      case this.certLdapMapper:
        cy.findByTestId(this.userModelAttInput).clear().type(userModelAttValue);
        cy.findByTestId(this.ldapAttInput).clear().type(ldapAttValue);
        break;
      case this.hcAttMapper:
        cy.findByTestId(this.userModelAttNameInput)
          .clear()
          .type(userModelAttValue);
        cy.findByTestId(this.attValueInput).clear().type(ldapAttValue);
        break;
      case this.fullNameLdapMapper:
        cy.findByTestId(this.ldapFullNameAttInput).clear().type(ldapAttValue);
        break;
      case this.hcLdapAttMapper:
        cy.findByTestId(this.ldapAttNameInput).clear().type(userModelAttValue);
        cy.findByTestId(this.ldapAttValueInput).clear().type(ldapAttValue);
        break;
      case this.hcLdapGroupMapper:
        cy.findByTestId(this.groupInput).clear().type(this.groupName);
        break;
      case this.groupLdapMapper:
        cy.findByTestId(this.ldapGroupsDnInput).clear().type(ldapDnValue);
        break;

      case this.roleLdapMapper:
        cy.findByTestId(this.ldapRolesDnInput).clear().type(ldapDnValue);
        cy.get(".pf-c-form__group")
          .contains("Client ID")
          .parent()
          .parent()
          .find("input")
          .click();
        cy.get("button").contains(this.clientName).click({ force: true });
        break;

      case this.hcLdapRoleMapper:
        cy.findByTestId("add-roles").click();
        cy.get("[aria-label='Select row 1']").click();
        cy.findByTestId("assign").click();
        break;
      default:
        console.log("Invalid mapper type.");
        break;
    }
  }

  updateMapper(mapperType: string) {
    const userModelAttValue = "lastName";
    const ldapAttValue = "sn";

    switch (mapperType) {
      case this.msadUserAcctMapper:
      case this.msadLdsUserAcctMapper:
        break;
      case this.userAttLdapMapper:
      case this.certLdapMapper:
        cy.findByTestId(this.userModelAttInput).clear().type(userModelAttValue);
        cy.findByTestId(this.ldapAttInput).clear().type(ldapAttValue);
        break;
      case this.hcAttMapper:
        cy.findByTestId(this.userModelAttNameInput)
          .clear()
          .type(userModelAttValue);
        cy.findByTestId(this.attValueInput).clear().type(ldapAttValue);
        break;
      case this.fullNameLdapMapper:
        cy.findByTestId(this.ldapFullNameAttInput).clear().type(ldapAttValue);
        break;
      case this.hcLdapAttMapper:
        cy.findByTestId(this.ldapAttNameInput).clear().type(userModelAttValue);
        cy.findByTestId(this.ldapAttValueInput).clear().type(ldapAttValue);
        break;
      default:
        console.log("Invalid mapper name.");
        break;
    }
  }

  clickExistingCard(cardName: string) {
    cy.findByTestId("keycloak-card-title").contains(cardName).click();
    cy.wait(1000);
    return this;
  }

  clickMenuCommand(menu: string, command: string) {
    cy.contains("button", menu).click();
    cy.contains("li", command).click();
    return this;
  }

  clickNewCard(providerType: string) {
    cy.findByTestId(`${providerType}-card`).click();
    cy.wait(1000);
    return this;
  }

  disableEnabledSwitch(providerType: string) {
    cy.get(`#${providerType}-switch`).uncheck({ force: true });
    return this;
  }

  enableEnabledSwitch(providerType: string) {
    cy.get(`#${providerType}-switch`).check({ force: true });
    return this;
  }

  save(providerType: string) {
    cy.findByTestId(`${providerType}-save`).click();
    return this;
  }

  cancel(providerType: string) {
    cy.findByTestId(`${providerType}-cancel`).click();
    return this;
  }

  testConnection() {
    cy.findByTestId(this.testConnectionBtn).click();
    return this;
  }

  testAuthorization() {
    cy.findByTestId(this.testAuthBtn).click();
    return this;
  }
}
