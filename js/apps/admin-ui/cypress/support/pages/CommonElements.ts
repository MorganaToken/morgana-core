import { trim } from "lodash-es";

export default class CommonElements {
  protected parentSelector;
  protected primaryBtn;
  protected secondaryBtn;
  protected secondaryBtnLink;
  protected dropdownMenuItem;
  protected selectMenuItem;
  protected dropdownToggleBtn;
  protected dropdownSelectToggleBtn;
  protected dropdownSelectToggleItem;

  constructor(parentSelector = "") {
    this.parentSelector = trim(parentSelector) + " ";
    this.primaryBtn = this.parentSelector + ".pf-c-button.pf-m-primary";
    this.secondaryBtn = this.parentSelector + ".pf-c-button.pf-m-secondary";
    this.secondaryBtnLink = this.parentSelector + ".pf-c-button.pf-m-link";
    this.dropdownMenuItem =
      this.parentSelector + ".pf-c-dropdown__menu .pf-c-dropdown__menu-item";
    this.selectMenuItem =
      this.parentSelector + ".pf-c-select__menu .pf-c-select__menu-item";
    this.dropdownToggleBtn = this.parentSelector + ".pf-c-dropdown__toggle";
    this.dropdownSelectToggleBtn = this.parentSelector + ".pf-c-select__toggle";
    this.dropdownSelectToggleItem =
      this.parentSelector + ".pf-c-select__menu > li";
  }

  clickPrimaryBtn() {
    cy.get(this.primaryBtn).click();
    return this;
  }

  clickSecondaryBtn(buttonName: string, force = false) {
    cy.get(this.secondaryBtn).contains(buttonName).click({ force: force });
    return this;
  }

  checkIfExists(exist: boolean) {
    cy.get(this.parentSelector).should((!exist ? "not." : "") + "exist");
    return this;
  }

  checkElementIsDisabled(
    element: Cypress.Chainable<JQuery<HTMLElement>>,
    disabled: boolean
  ) {
    element.then(($btn) => {
      if ($btn.hasClass("pf-m-disabled")) {
        element.should(
          (!disabled ? "not." : "") + "have.class",
          "pf-m-disabled"
        );
      } else {
        element.should((!disabled ? "not." : "") + "have.attr", "disabled");
      }
    });
  }
}
