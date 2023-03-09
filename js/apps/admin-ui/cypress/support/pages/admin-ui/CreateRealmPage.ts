export default class CreateRealmPage {
  private clearBtn = ".pf-c-file-upload__file-select button:last-child";
  private modalClearBtn = "clear-button";
  private realmNameInput = "#kc-realm-name";
  private enabledSwitch =
    '[for="kc-realm-enabled-switch"] span.pf-c-switch__toggle';
  private createBtn = '.pf-c-form__group:last-child button[type="submit"]';
  private cancelBtn = '.pf-c-form__group:last-child button[type="button"]';
  private codeEditor = ".pf-c-code-editor__code";

  fillRealmName(realmName: string) {
    cy.get(this.realmNameInput).clear().type(realmName);

    return this;
  }

  fillCodeEditor() {
    cy.get(this.codeEditor).click().type("clear this field");

    return this;
  }

  createRealm() {
    cy.get(this.createBtn).click();

    return this;
  }

  disableRealm() {
    cy.get(this.enabledSwitch).click();

    return this;
  }

  cancelRealmCreation() {
    cy.get(this.cancelBtn).click();

    return this;
  }

  clearTextField() {
    cy.get(this.clearBtn).click();
    cy.findByTestId(this.modalClearBtn).click();

    return this;
  }

  verifyRealmNameFieldInvalid() {
    cy.get(this.realmNameInput)
      .next("div")
      .contains("Required field")
      .should("have.class", "pf-m-error");

    return this;
  }
}
