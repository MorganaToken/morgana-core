export default class WebAuthnPolicies {
  webAuthnPolicyCreateTimeout(value: number) {
    cy.findByTestId("webAuthnPolicyCreateTimeout").type(String(value));
    return this;
  }
  goToTab() {
    cy.findByTestId("policies").click().get("#pf-tab-3-webauthnPolicy").click();
    return this;
  }

  goToPasswordlessTab() {
    cy.findByTestId("policies")
      .click()
      .get("#pf-tab-4-webauthnPasswordlessPolicy")
      .click();
    return this;
  }

  fillSelects(data: Record<string, string>, isPasswordLess: boolean = false) {
    for (const prop of Object.keys(data)) {
      cy.get(
        `#${
          isPasswordLess ? prop.replace("Policy", "PolicyPasswordless") : prop
        }`
      )
        .click()
        .parent()
        .contains(data[prop])
        .click();
    }
    return this;
  }

  save() {
    cy.findByTestId("save").click();
    return this;
  }
}
