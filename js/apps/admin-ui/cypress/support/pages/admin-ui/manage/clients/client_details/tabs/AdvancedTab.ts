import PageObject from "../../../../components/PageObject";

export default class AdvancedTab extends PageObject {
  private setToNowBtn = "#setToNow";
  private clearBtn = "#clear";
  private pushBtn = "#push";
  private notBeforeInput = "#kc-not-before";

  private clusterNodesExpandBtn =
    ".pf-c-expandable-section .pf-c-expandable-section__toggle";
  private testClusterAvailability = "#testClusterAvailability";
  private emptyClusterElement = "empty-state";
  private registerNodeManuallyBtn = "no-nodes-registered-empty-action";
  private deleteClusterNodeDrpDwn =
    '[aria-label="registeredClusterNodes"] [aria-label="Actions"]';
  private deleteClusterNodeBtn =
    '[aria-label="registeredClusterNodes"] [role="menu"] button';
  private nodeHostInput = "#nodeHost";
  private addNodeConfirmBtn = "#add-node-confirm";

  private accessTokenSignatureAlgorithmInput = "#accessTokenSignatureAlgorithm";
  private fineGrainSaveBtn = "#fineGrainSave";
  private fineGrainRevertBtn = "#fineGrainRevert";
  private OIDCCompatabilitySaveBtn = "OIDCCompatabilitySave";
  private OIDCCompatabilityRevertBtn = "OIDCCompatabilityRevert";
  private OIDCAdvancedSaveBtn = "OIDCAdvancedSave";
  private OIDCAdvancedRevertBtn = "OIDCAdvancedRevert";
  private OIDCAuthFlowOverrideSaveBtn = "OIDCAuthFlowOverrideSave";
  private OIDCAuthFlowOverrideRevertBtn = "OIDCAuthFlowOverrideRevert";

  private excludeSessionStateSwitch =
    "#excludeSessionStateFromAuthenticationResponse-switch";
  private useRefreshTokenSwitch = "#useRefreshTokens";
  private useRefreshTokenForClientCredentialsGrantSwitch =
    "#useRefreshTokenForClientCredentialsGrant";
  private useLowerCaseBearerTypeSwitch = "#useLowerCaseBearerType";

  private oAuthMutualSwitch = "#oAuthMutual-switch";
  private keyForCodeExchangeInput = "#keyForCodeExchange";
  private pushedAuthorizationRequestRequiredSwitch =
    "#pushedAuthorizationRequestRequired";

  private browserFlowInput = "#browserFlow";
  private directGrantInput = "#directGrant";

  private jumpToOIDCCompatabilitySettings =
    "jump-link-open-id-connect-compatibility-modes";
  private jumpToAdvancedSettings = "jump-link-advanced-settings";
  private jumpToAuthFlowOverride = "jump-link-authentication-flow-overrides";

  setRevocationToNow() {
    cy.get(this.setToNowBtn).click();
    return this;
  }

  clearRevocation() {
    cy.get(this.clearBtn).click();
    return this;
  }

  pushRevocation() {
    cy.get(this.pushBtn).click();
    return this;
  }

  checkRevacationIsNone() {
    cy.get(this.notBeforeInput).should("have.value", "None");

    return this;
  }

  checkRevocationIsSetToNow() {
    cy.get(this.notBeforeInput).should(
      "have.value",
      new Date().toLocaleString("en-US", {
        dateStyle: "long",
        timeStyle: "short",
      })
    );

    return this;
  }

  expandClusterNode() {
    cy.get(this.clusterNodesExpandBtn).click();
    return this;
  }

  checkTestClusterAvailability(active: boolean) {
    cy.get(this.testClusterAvailability).should(
      (active ? "not." : "") + "have.class",
      "pf-m-disabled"
    );
    return this;
  }

  checkEmptyClusterNode() {
    cy.findByTestId(this.emptyClusterElement).should("exist");
    return this;
  }

  registerNodeManually() {
    cy.findByTestId(this.registerNodeManuallyBtn).click();
    return this;
  }

  deleteClusterNode() {
    cy.get(this.deleteClusterNodeDrpDwn).click();
    cy.get(this.deleteClusterNodeBtn).click();
    return this;
  }

  fillHost(host: string) {
    cy.get(this.nodeHostInput).type(host);
    return this;
  }

  saveHost() {
    cy.get(this.addNodeConfirmBtn).click();
    return this;
  }

  selectAccessTokenSignatureAlgorithm(algorithm: string) {
    cy.get(this.accessTokenSignatureAlgorithmInput).click();
    cy.get(this.accessTokenSignatureAlgorithmInput + " + ul")
      .contains(algorithm)
      .click();

    return this;
  }

  checkAccessTokenSignatureAlgorithm(algorithm: string) {
    cy.get(this.accessTokenSignatureAlgorithmInput).should(
      "have.text",
      algorithm
    );
    return this;
  }

  saveFineGrain() {
    cy.get(this.fineGrainSaveBtn).click();
    return this;
  }

  revertFineGrain() {
    cy.get(this.fineGrainRevertBtn).click();
    return this;
  }

  saveCompatibility() {
    cy.findByTestId(this.OIDCCompatabilitySaveBtn).click();
    return this;
  }

  revertCompatibility() {
    cy.findByTestId(this.OIDCCompatabilityRevertBtn).click();
    cy.findByTestId(this.jumpToOIDCCompatabilitySettings).click();
    //uncomment when revert function reverts all switches, rather than just the first one
    //this.assertSwitchStateOn(cy.get(this.useRefreshTokenForClientCredentialsGrantSwitch));
    this.assertSwitchStateOn(cy.get(this.excludeSessionStateSwitch));
    return this;
  }

  jumpToCompatability() {
    cy.findByTestId(this.jumpToOIDCCompatabilitySettings).click();
    return this;
  }

  clickAllCompatibilitySwitch() {
    cy.get(this.excludeSessionStateSwitch).parent().click();
    this.assertSwitchStateOn(cy.get(this.excludeSessionStateSwitch));
    cy.get(this.useRefreshTokenSwitch).parent().click();
    this.assertSwitchStateOff(cy.get(this.useRefreshTokenSwitch));
    cy.get(this.useRefreshTokenForClientCredentialsGrantSwitch)
      .parent()
      .click();
    this.assertSwitchStateOn(
      cy.get(this.useRefreshTokenForClientCredentialsGrantSwitch)
    );
    cy.get(this.useLowerCaseBearerTypeSwitch).parent().click();
    this.assertSwitchStateOn(cy.get(this.useLowerCaseBearerTypeSwitch));
    return this;
  }

  clickExcludeSessionStateSwitch() {
    cy.get(this.excludeSessionStateSwitch).parent().click();
    this.assertSwitchStateOff(cy.get(this.excludeSessionStateSwitch));
  }
  clickUseRefreshTokenForClientCredentialsGrantSwitch() {
    cy.get(this.useRefreshTokenForClientCredentialsGrantSwitch)
      .parent()
      .click();
    this.assertSwitchStateOff(
      cy.get(this.useRefreshTokenForClientCredentialsGrantSwitch)
    );
  }

  saveAdvanced() {
    cy.findByTestId(this.OIDCAdvancedSaveBtn).click();
    return this;
  }

  revertAdvanced() {
    cy.findByTestId(this.OIDCAdvancedRevertBtn).click();
    return this;
  }

  jumpToAdvanced() {
    cy.findByTestId(this.jumpToAdvancedSettings).click();
    return this;
  }

  clickAdvancedSwitches() {
    cy.get(this.oAuthMutualSwitch).parent().click();
    cy.get(this.pushedAuthorizationRequestRequiredSwitch).parent().click();
    return this;
  }

  checkAdvancedSwitchesOn() {
    cy.get(this.oAuthMutualSwitch).scrollIntoView();
    this.assertSwitchStateOn(cy.get(this.oAuthMutualSwitch));
    this.assertSwitchStateOn(
      cy.get(this.pushedAuthorizationRequestRequiredSwitch)
    );
    return this;
  }

  checkAdvancedSwitchesOff() {
    this.assertSwitchStateOff(cy.get(this.oAuthMutualSwitch));
    this.assertSwitchStateOff(
      cy.get(this.pushedAuthorizationRequestRequiredSwitch)
    );
    return this;
  }

  selectKeyForCodeExchangeInput(input: string) {
    cy.get(this.keyForCodeExchangeInput).click();
    cy.get(this.keyForCodeExchangeInput + " + ul")
      .contains(input)
      .click();
    return this;
  }

  checkKeyForCodeExchangeInput(input: string) {
    cy.get(this.keyForCodeExchangeInput).should("have.text", input);
    return this;
  }

  saveAuthFlowOverride() {
    cy.findByTestId(this.OIDCAuthFlowOverrideSaveBtn).click();
    return this;
  }

  revertAuthFlowOverride() {
    cy.findByTestId(this.OIDCAuthFlowOverrideRevertBtn).click();
    return this;
  }

  jumpToAuthFlow() {
    cy.findByTestId(this.jumpToAuthFlowOverride).click();
    return this;
  }

  selectBrowserFlowInput(input: string) {
    cy.get(this.browserFlowInput).click();
    cy.get(this.browserFlowInput + " + ul")
      .contains(input)
      .click();
    return this;
  }

  selectDirectGrantInput(input: string) {
    cy.get(this.directGrantInput).click();
    cy.get(this.directGrantInput + " + ul")
      .contains(input)
      .click();
    return this;
  }

  checkBrowserFlowInput(input: string) {
    cy.get(this.browserFlowInput).should("have.text", input);
    return this;
  }

  checkDirectGrantInput(input: string) {
    cy.get(this.directGrantInput).should("have.text", input);
    return this;
  }
}
