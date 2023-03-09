import PageObject from "../../../../components/PageObject";
import Masthead from "../../../../Masthead";

export enum NameIdFormat {
  Username = "username",
  Email = "email",
  Transient = "transient",
  Persistent = "persistent",
}

const masthead = new Masthead();

export default class SettingsTab extends PageObject {
  private samlNameIdFormat = "#samlNameIdFormat";
  private forceNameIdFormat = "forceNameIdFormat";
  private forcePostBinding = "forcePostBinding";
  private forceArtifactBinding = "forceArtifactBinding";
  private includeAuthnStatement = "includeAuthnStatement";
  private includeOneTimeUseCondition = "includeOneTimeUseCondition";
  private optimizeLookup = "optimizeLookup";

  private signDocumentsSwitch = "signDocuments";
  private signAssertionsSwitch = "signAssertions";
  private signatureAlgorithm = "#signatureAlgorithm";
  private signatureKeyName = "#signatureKeyName";
  private canonicalization = "#canonicalization";

  private loginTheme = "#loginTheme";
  private consentSwitch = "#kc-consent-switch";
  private displayClientSwitch = "#kc-display-on-client-switch";
  private consentScreenText = "#kc-consent-screen-text";

  private saveBtn = "settingsSave";
  private revertBtn = "settingsRevert";

  private redirectUris = "redirectUris";
  private postLogoutRedirectUris = "attributes.post.logout.redirect.uris";

  private idpInitiatedSsoUrlName = "idpInitiatedSsoUrlName";
  private idpInitiatedSsoRelayState = "idpInitiatedSsoRelayState";
  private masterSamlProcessingUrl = "masterSamlProcessingUrl";

  public clickSaveBtn() {
    cy.findByTestId(this.saveBtn).click();
    return this;
  }

  public clickRevertBtn() {
    cy.findByTestId(this.revertBtn).click();
    return this;
  }

  public selectNameIdFormatDropdown(nameId: NameIdFormat) {
    cy.get(this.samlNameIdFormat).click();
    cy.findByText(nameId).click();
    return this;
  }

  public selectSignatureAlgorithmDropdown(sign: string) {
    cy.get(this.signatureAlgorithm).click();
    cy.findByText(sign).click();
    return this;
  }

  public selectSignatureKeyNameDropdown(keyName: string) {
    cy.get(this.signatureKeyName).click();
    cy.findByText(keyName).click();
    return this;
  }

  public selectCanonicalizationDropdown(canon: string) {
    cy.get(this.canonicalization).click();
    cy.findByText(canon).click();
  }

  public selectLoginThemeDropdown(theme: string) {
    cy.get(this.loginTheme).click();
    cy.findByText(theme).click();
  }

  public clickForceNameIdFormatSwitch() {
    cy.findByTestId(this.forceNameIdFormat).parent().click();
    return this;
  }

  public clickForcePostBindingSwitch() {
    cy.findByTestId(this.forcePostBinding).parent().click();
    return this;
  }

  public clickForceArtifactBindingSwitch() {
    cy.findByTestId(this.forceArtifactBinding).parent().click();
    return this;
  }

  public clickIncludeAuthnStatementSwitch() {
    cy.findByTestId(this.includeAuthnStatement).parent().click();
    return this;
  }

  public clickIncludeOneTimeUseConditionSwitch() {
    cy.findByTestId(this.includeOneTimeUseCondition).parent().click();
    return this;
  }

  public clickOptimizeLookupSwitch() {
    cy.findByTestId(this.optimizeLookup).parent().click();
    return this;
  }

  public clickSignDocumentsSwitch() {
    cy.findByTestId(this.signDocumentsSwitch).parent().click();
    return this;
  }

  public clickSignAssertionsSwitch() {
    cy.findByTestId(this.signAssertionsSwitch).parent().click();
    return this;
  }

  public clickConsentSwitch() {
    cy.get(this.consentSwitch).parent().click();
    return this;
  }

  public clickDisplayClientSwitch() {
    cy.get(this.displayClientSwitch).parent().click();
    return this;
  }

  public assertNameIdFormatDropdown() {
    this.selectNameIdFormatDropdown(NameIdFormat.Email);
    this.selectNameIdFormatDropdown(NameIdFormat.Username);
    this.selectNameIdFormatDropdown(NameIdFormat.Persistent);
    this.selectNameIdFormatDropdown(NameIdFormat.Transient);
    return this;
  }

  public assertSignatureAlgorithmDropdown() {
    this.selectSignatureAlgorithmDropdown("RSA_SHA1");
    this.selectSignatureAlgorithmDropdown("RSA_SHA256");
    this.selectSignatureAlgorithmDropdown("RSA_SHA256_MGF1");
    this.selectSignatureAlgorithmDropdown("RSA_SHA512");
    this.selectSignatureAlgorithmDropdown("RSA_SHA512_MGF1");
    this.selectSignatureAlgorithmDropdown("DSA_SHA1");
    return this;
  }

  public assertSignatureKeyNameDropdown() {
    this.selectSignatureKeyNameDropdown("KEY_ID");
    this.selectSignatureKeyNameDropdown("CERT_SUBJECT");
    this.selectSignatureKeyNameDropdown("NONE");
    return this;
  }

  public assertCanonicalizationDropdown() {
    this.selectCanonicalizationDropdown("EXCLUSIVE_WITH_COMMENTS");
    this.selectCanonicalizationDropdown("EXCLUSIVE");
    this.selectCanonicalizationDropdown("INCLUSIVE_WITH_COMMENTS");
    this.selectCanonicalizationDropdown("INCLUSIVE");
    return this;
  }

  public assertLoginThemeDropdown() {
    this.selectLoginThemeDropdown("base");
    this.selectLoginThemeDropdown("keycloak");
    return this;
  }

  public assertSAMLCapabilitiesSwitches() {
    this.clickForceNameIdFormatSwitch();
    this.assertSwitchStateOn(cy.findByTestId(this.forceNameIdFormat));

    this.clickForcePostBindingSwitch();
    this.assertSwitchStateOff(cy.findByTestId(this.forcePostBinding));

    this.clickForceArtifactBindingSwitch();
    this.assertSwitchStateOn(cy.findByTestId(this.forceArtifactBinding));

    this.clickIncludeAuthnStatementSwitch();
    this.assertSwitchStateOff(cy.findByTestId(this.includeAuthnStatement));

    this.clickIncludeOneTimeUseConditionSwitch();
    this.assertSwitchStateOn(cy.findByTestId(this.includeOneTimeUseCondition));

    this.clickOptimizeLookupSwitch();
    this.assertSwitchStateOn(cy.findByTestId(this.optimizeLookup));
    return this;
  }

  public assertSignatureEncryptionSwitches() {
    cy.get(this.signatureAlgorithm).should("exist");

    this.clickSignDocumentsSwitch();
    this.assertSwitchStateOff(cy.findByTestId(this.signDocumentsSwitch));
    cy.get(this.signatureAlgorithm).should("not.exist");

    this.clickSignAssertionsSwitch();
    this.assertSwitchStateOn(cy.findByTestId(this.signAssertionsSwitch));
    cy.get(this.signatureAlgorithm).should("exist");
    return this;
  }

  public assertLoginSettings() {
    cy.get(this.displayClientSwitch).should("be.disabled");
    cy.get(this.consentScreenText).should("be.disabled");
    this.clickConsentSwitch();
    cy.get(this.displayClientSwitch).should("not.be.disabled");
    this.clickDisplayClientSwitch();
    cy.get(this.consentScreenText).should("not.be.disabled");
    cy.get(this.consentScreenText).click().type("Consent Screen Text");
    return this;
  }

  public selectRedirectUriTextField(number: number, text: string) {
    cy.findByTestId(this.redirectUris + number)
      .click()
      .clear()
      .type(text);
    return this;
  }

  public assertAccessSettings() {
    const redirectUriError =
      "Client could not be updated: A redirect URI is not a valid URI";

    cy.findByTestId(this.idpInitiatedSsoUrlName).click().type("a");
    cy.findByTestId(this.idpInitiatedSsoRelayState).click().type("b");
    cy.findByTestId(this.masterSamlProcessingUrl).click().type("c");

    this.selectRedirectUriTextField(0, "Redirect Uri");
    cy.findByText("Add valid redirect URIs").click();
    this.selectRedirectUriTextField(1, "Redirect Uri second field");
    this.clickSaveBtn();
    masthead.checkNotificationMessage(redirectUriError);

    return this;
  }
}
