import ProviderBaseGeneralSettingsPage from "../ProviderBaseGeneralSettingsPage";

const base_url_input_test_value = "base_url_input_test_value";

export default class ProviderOpenshiftGeneralSettings extends ProviderBaseGeneralSettingsPage {
  private baseUrlInput = "#baseUrl";

  constructor() {
    super();
    this.requiredFields.push(this.baseUrlInput);
  }

  public typeBaseUrlInput(value: string) {
    cy.get(this.baseUrlInput).type(value).blur();
    return this;
  }

  public assertbaseUrlInputEqual(value: string) {
    cy.get(this.baseUrlInput).should("have.value", value);
    return this;
  }

  public assertRequiredFieldsErrorsExist() {
    return this.assertCommonRequiredFields(this.requiredFields);
  }

  public fillData(idpName: string) {
    this.fillCommonFields(idpName);
    cy.get(this.baseUrlInput).type(idpName + base_url_input_test_value);
    return this;
  }

  public assertFilledDataEqual(idpName: string) {
    this.assertCommonFilledDataEqual(idpName);
    this.assertbaseUrlInputEqual(idpName + base_url_input_test_value);
    return this;
  }
}
