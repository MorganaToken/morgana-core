export default class PageObject {
  private selectItemSelectedIcon = ".pf-c-select__menu-item-icon";
  private drpDwnMenuList = ".pf-c-dropdown__menu";
  private drpDwnMenuItem = ".pf-c-dropdown__menu-item";
  private drpDwnMenuToggleBtn = ".pf-c-dropdown__toggle";
  private selectMenuList = ".pf-c-select__menu";
  private selectMenuItem = ".pf-c-select__menu-item";
  private selectMenuToggleBtn = ".pf-c-select__toggle";
  private switchInput = ".pf-c-switch__input";
  private formLabel = ".pf-c-form__label";
  private chipGroup = ".pf-c-chip-group";
  private chipGroupCloseBtn = ".pf-c-chip-group__close";
  private chipItem = ".pf-c-chip-group__list-item";
  private emptyStateDiv = ".pf-c-empty-state:visible";
  private toolbarActionsButton = ".pf-c-toolbar button[aria-label='Actions']";
  private breadcrumbItem = ".pf-c-breadcrumb .pf-c-breadcrumb__item";

  protected assertExist(element: Cypress.Chainable<JQuery>, exist: boolean) {
    element.should((!exist ? "not." : "") + "exist");
    return this;
  }

  protected assertIsVisible(
    element: Cypress.Chainable<JQuery>,
    isVisible: boolean
  ) {
    element.should((!isVisible ? "not." : "") + "be.visible");
    return this;
  }

  protected assertIsEnabled(
    element: Cypress.Chainable<JQuery>,
    isEnabled = true
  ) {
    element.then(($btn) => {
      if ($btn.hasClass("pf-m-disabled")) {
        element.should(
          (isEnabled ? "not." : "") + "have.class",
          "pf-m-disabled"
        );
      } else {
        element.should((isEnabled ? "not." : "") + "have.attr", "disabled");
      }
    });
    return this;
  }

  protected assertIsDisabled(element: Cypress.Chainable<JQuery>) {
    return this.assertIsEnabled(element, false);
  }

  protected assertHaveText(element: Cypress.Chainable<JQuery>, text: string) {
    element.should("have.text", text);
    return this;
  }

  protected assertHaveValue(element: Cypress.Chainable<JQuery>, value: string) {
    element.should("have.value", value);
    return this;
  }

  protected assertSwitchStateOn(
    element?: Cypress.Chainable<JQuery>,
    isOn = true
  ) {
    (element ?? cy.get(this.switchInput))
      .parent()
      .contains(isOn ? "On" : "Off")
      .should("be.visible");
    return this;
  }

  protected assertSwitchStateOff(element?: Cypress.Chainable<JQuery>) {
    return this.assertSwitchStateOn(element, false);
  }

  protected assertDropdownMenuIsOpen(
    isOpen = true,
    element?: Cypress.Chainable<JQuery>
  ) {
    this.assertExist(element ?? cy.get(this.drpDwnMenuList), isOpen);
    return this;
  }

  protected assertDropdownMenuIsClosed(element?: Cypress.Chainable<JQuery>) {
    return this.assertDropdownMenuIsOpen(
      false,
      element ?? cy.get(this.drpDwnMenuList)
    );
  }

  protected clickDropdownMenuItem(
    itemName: string,
    element?: Cypress.Chainable<JQuery>
  ) {
    (element ?? cy.get(this.drpDwnMenuItem).contains(itemName)).click();
    return this;
  }

  protected clickDropdownMenuToggleButton(
    itemName: string,
    element?: Cypress.Chainable<JQuery>
  ) {
    element =
      element ??
      cy.get(this.drpDwnMenuToggleBtn).contains(itemName).parent().parent();
    element.click();
    return this;
  }

  protected openDropdownMenu(
    itemName: string,
    element?: Cypress.Chainable<JQuery>
  ) {
    element =
      element ??
      cy.get(this.drpDwnMenuToggleBtn).contains(itemName).parent().parent();
    this.clickDropdownMenuToggleButton(itemName, element);
    this.assertDropdownMenuIsOpen(true);
    return this;
  }

  protected closeDropdownMenu(
    itemName: string,
    element?: Cypress.Chainable<JQuery>
  ) {
    element =
      element ??
      cy.get(this.drpDwnMenuToggleBtn).contains(itemName).parent().parent();
    this.clickDropdownMenuToggleButton(itemName, element);
    this.assertDropdownMenuIsOpen(false);
    return this;
  }

  protected assertDropdownMenuItemIsSelected(
    itemName: string,
    isSelected: boolean,
    element?: Cypress.Chainable<JQuery>
  ) {
    element = element ?? cy.get(this.drpDwnMenuItem);
    this.assertExist(
      element.contains(itemName).find(this.selectItemSelectedIcon),
      isSelected
    );
    return this;
  }

  protected assertDropdownMenuHasItems(
    items: string[],
    element?: Cypress.Chainable<JQuery>
  ) {
    const initialElement = element;
    for (const item of items) {
      element = initialElement ?? cy.get(this.drpDwnMenuList);
      this.assertExist(element.find(this.drpDwnMenuItem).contains(item), true);
    }
    return this;
  }

  protected assertDropdownMenuHasLabels(
    items: string[],
    element?: Cypress.Chainable<JQuery>
  ) {
    const initialElement = element;
    for (const item of items) {
      element = initialElement ?? cy.get(this.drpDwnMenuList);
      this.assertExist(element.find(this.formLabel).contains(item), true);
    }
    return this;
  }

  protected assertDropdownMenuItemsEqualTo(
    number: number,
    element?: Cypress.Chainable<JQuery>
  ) {
    element = element ?? cy.get(this.drpDwnMenuList);
    element.find(this.drpDwnMenuItem).should(($item) => {
      expect($item).to.have.length(number);
    });
    return this;
  }

  protected assertSelectMenuIsOpen(
    isOpen = true,
    element?: Cypress.Chainable<JQuery>
  ) {
    element = element ?? cy.get(this.selectMenuList);
    return this.assertDropdownMenuIsOpen(isOpen, element);
  }

  protected assertSelectMenuIsClosed(element?: Cypress.Chainable<JQuery>) {
    element = element ?? cy.get(this.selectMenuList);
    return this.assertDropdownMenuIsClosed(element);
  }

  protected clickSelectMenuItem(
    itemName: string,
    element?: Cypress.Chainable<JQuery>
  ) {
    element =
      element ??
      cy.get(this.selectMenuItem).contains(new RegExp(`^${itemName}$`));
    return this.clickDropdownMenuItem(itemName, element);
  }

  protected clickSelectMenuToggleButton(
    itemName: string,
    element?: Cypress.Chainable<JQuery>
  ) {
    element =
      element ??
      cy.get(this.selectMenuToggleBtn).contains(itemName).parent().parent();
    return this.clickDropdownMenuToggleButton(itemName, element);
  }

  protected openSelectMenu(itemName: string, element?: Cypress.Chainable<any>) {
    element =
      element ??
      cy.get(this.selectMenuToggleBtn).contains(itemName).parent().parent();
    this.clickDropdownMenuToggleButton(itemName, element);
    this.assertSelectMenuIsOpen(true);
    return this;
  }

  protected closeSelectMenu(
    itemName: string,
    element?: Cypress.Chainable<JQuery>
  ) {
    element =
      element ??
      cy.get(this.selectMenuToggleBtn).contains(itemName).parent().parent();
    this.clickDropdownMenuToggleButton(itemName, element);
    this.assertSelectMenuIsOpen(false);
    return this;
  }

  protected assertSelectMenuItemIsSelected(
    itemName: string,
    isSelected: boolean,
    element?: Cypress.Chainable<JQuery>
  ) {
    element = element ?? cy.get(this.selectMenuItem);
    return this.assertDropdownMenuItemIsSelected(itemName, isSelected, element);
  }

  protected assertSelectMenuHasItems(
    items: string[],
    element?: Cypress.Chainable<JQuery>
  ) {
    const initialElement = element;
    for (const item of items) {
      element = initialElement ?? cy.get(this.selectMenuList);
      this.assertExist(element.find(this.selectMenuItem).contains(item), true);
    }
    return this;
  }

  protected assertSelectMenuItemsEqualTo(
    number: number,
    element?: Cypress.Chainable<JQuery>
  ) {
    element = element ?? cy.get(this.selectMenuList);
    element.find(this.selectMenuItem).should(($item) => {
      expect($item).to.have.length(number);
    });
    return this;
  }

  private getChipGroup(groupName: string) {
    return cy.get(this.chipGroup).contains(groupName).parent().parent();
  }

  private getChipItem(itemName: string) {
    return cy.get(this.chipItem).contains(itemName).parent();
  }

  private getChipGroupItem(groupName: string, itemName: string) {
    return this.getChipGroup(groupName)
      .find(this.chipItem)
      .contains(itemName)
      .parent();
  }

  protected removeChipGroup(groupName: string) {
    this.getChipGroup(groupName)
      .find(this.chipGroupCloseBtn)
      .find("button")
      .click();
    return this;
  }

  protected removeChipItem(itemName: string) {
    this.getChipItem(itemName).find("button").click();
    return this;
  }

  protected removeChipGroupItem(groupName: string, itemName: string) {
    this.getChipGroupItem(groupName, itemName).find("button").click();
    return this;
  }

  protected assertChipGroupExist(groupName: string, exist: boolean) {
    this.assertExist(cy.contains(this.chipGroup, groupName), exist);
    return this;
  }

  protected clickToolbarAction(itemName: string) {
    cy.get(this.toolbarActionsButton).click();
    this.clickDropdownMenuItem(itemName);
    return this;
  }

  protected assertChipItemExist(itemName: string, exist: boolean) {
    cy.get(this.chipItem).within(() => {
      cy.contains(itemName).should((exist ? "" : "not.") + "exist");
    });
    return this;
  }

  protected assertChipGroupItemExist(
    groupName: string,
    itemName: string,
    exist: boolean
  ) {
    this.assertExist(
      this.getChipGroup(groupName).contains(this.chipItem, itemName),
      exist
    );
    return this;
  }

  protected assertEmptyStateExist(exist: boolean) {
    if (exist) {
      cy.get(this.emptyStateDiv).should("exist").should("be.visible");
    } else {
      cy.get(this.emptyStateDiv).should("not.exist");
    }
    return this;
  }

  protected clickBreadcrumbItem(itemName: string) {
    cy.get(this.breadcrumbItem).contains(itemName).click();
    return this;
  }

  protected waitForPageLoad() {
    cy.get('[role="progressbar"]').should("not.exist");
    return this;
  }
}
