import PageObject from "../../components/PageObject";
import Masthead from "../../Masthead";

const masthead = new Masthead();

export default class ProviderBaseGeneralSettingsPage extends PageObject {
  private redirectUriGroup = ".pf-c-clipboard-copy__group";
  protected clientIdInput = "#kc-client-id";
  protected clientSecretInput = "#kc-client-secret";
  private displayOrderInput = "#kc-display-order";
  private addBtn = "createProvider";
  private cancelBtn = "cancel";
  private requiredFieldErrorMsg = ".pf-c-form__helper-text.pf-m-error";
  protected requiredFields: string[] = [
    this.clientIdInput,
    this.clientSecretInput,
  ];
  protected testData = {
    ClientId: "client",
    ClientSecret: "client_secret",
    DisplayOrder: "0",
  };

  public typeClientId(clientId: string) {
    cy.get(this.clientIdInput).type(clientId).blur();
    return this;
  }

  public typeClientSecret(clientSecret: string) {
    cy.get(this.clientSecretInput).type(clientSecret).blur();
    return this;
  }

  public typeDisplayOrder(displayOrder: string) {
    cy.get(this.displayOrderInput).type(displayOrder).blur();

    return this;
  }

  public clickShowPassword() {
    cy.get(this.clientSecretInput).parent().find("button").click();
    return this;
  }

  public clickCopyToClipboard() {
    cy.get(this.redirectUriGroup).find("button").click();
    return this;
  }

  public clickAdd() {
    cy.findByTestId(this.addBtn).click();
    return this;
  }

  public clickCancel() {
    cy.findByTestId(this.cancelBtn).click();
    return this;
  }

  public assertRedirectUriInputEqual(value: string) {
    cy.get(this.redirectUriGroup).find("input").should("have.value", value);
    return this;
  }

  public assertClientIdInputEqual(text: string) {
    cy.get(this.clientIdInput).should("have.text", text);
    return this;
  }

  public assertClientSecretInputEqual(text: string) {
    cy.get(this.clientSecretInput).should("have.text", text);
    return this;
  }

  public assertDisplayOrderInputEqual(text: string) {
    cy.get(this.clientSecretInput).should("have.text", text);
    return this;
  }

  public assertNotificationIdpCreated() {
    masthead.checkNotificationMessage("Identity provider successfully created");
    return this;
  }

  protected assertCommonRequiredFields(requiredFiels: string[]) {
    requiredFiels.forEach((elementLocator) => {
      if (elementLocator.includes("#")) {
        cy.get(elementLocator)
          .parent()
          .parent()
          .find(this.requiredFieldErrorMsg)
          .should("exist");
      } else {
        cy.findByTestId(elementLocator)
          .parent()
          .parent()
          .find(this.requiredFieldErrorMsg)
          .should("exist");
      }
    });
    return this;
  }

  public assertRequiredFieldsErrorsExist() {
    return this.assertCommonRequiredFields(this.requiredFields);
  }

  protected fillCommonFields(idpName: string) {
    this.typeClientId(this.testData["ClientId"] + idpName);
    this.typeClientSecret(this.testData["ClientSecret"] + idpName);
    this.typeDisplayOrder(this.testData["DisplayOrder"]);

    return this;
  }

  public fillData(idpName: string) {
    this.fillCommonFields(idpName);

    return this;
  }

  protected assertCommonFilledDataEqual(idpName: string) {
    cy.get(this.clientIdInput).should(
      "have.value",
      this.testData["ClientId"] + idpName
    );
    cy.get(this.clientSecretInput).should("contain.value", "****");
    cy.get(this.displayOrderInput).should(
      "have.value",
      this.testData["DisplayOrder"]
    );
    return this;
  }

  public assertFilledDataEqual(idpName: string) {
    this.assertCommonFilledDataEqual(idpName);
    return this;
  }
}
