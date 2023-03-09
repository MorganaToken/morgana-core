import CommonElements from "../../CommonElements";

export default class TablePage extends CommonElements {
  private tableRowItem: string;
  private tableRowItemChckBx: string;
  private tableHeaderRowItem: string;
  private tableInModal: boolean;
  static tableSelector = "table[aria-label]";

  constructor(parentElement?: string) {
    if (parentElement) {
      super(parentElement);
    } else {
      super(TablePage.tableSelector + ":visible");
    }
    this.tableRowItem =
      this.parentSelector + "tbody tr[data-ouia-component-type]";
    this.tableHeaderRowItem =
      this.parentSelector + "thead tr[data-ouia-component-type]";
    this.tableRowItemChckBx = ".pf-c-table__check";
    this.tableInModal = false;
  }

  setTableInModal(value: boolean) {
    this.tableInModal = value;
  }

  selectRowItemCheckbox(itemName: string) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .contains(itemName)
      .parentsUntil("tbody")
      .find(this.tableRowItemChckBx)
      .click();
    return this;
  }

  clickRowItemLink(itemName: string) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .contains(itemName)
      .click();
    return this;
  }

  selectRowItemAction(itemName: string, actionItemName: string) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .contains(itemName)
      .parentsUntil("tbody")
      .find(".pf-c-dropdown__toggle")
      .click();
    cy.get(this.dropdownMenuItem).contains(actionItemName).click();
    return this;
  }

  typeValueToRowItem(row: number, column: number, value: string) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") +
        this.tableRowItem +
        ":nth-child(" +
        row +
        ")"
    )
      .find("td:nth-child(" + column + ")")
      .type(value);
    return this;
  }

  clickRowItemByIndex(row: number, column: number, appendChildren?: string) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") +
        this.tableRowItem +
        ":nth-child(" +
        row +
        ")"
    )
      .find("td:nth-child(" + column + ") " + appendChildren)
      .click();
    return this;
  }

  clickRowItemByItemName(
    itemName: string,
    column: number,
    appendChildren?: string
  ) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .find("td:nth-child(" + column + ") " + appendChildren)
      .contains(itemName)
      .click();
    return this;
  }

  clickHeaderItem(column: number, appendChildren?: string) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") +
        this.tableHeaderRowItem
    )
      .find("td:nth-child(" + column + ") " + appendChildren)
      .click();
    return this;
  }

  checkRowItemsEqualTo(amount: number) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .its("length")
      .should("be.eq", amount);
    return this;
  }

  checkRowItemsGreaterThan(amount: number) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .its("length")
      .should("be.gt", amount);
    return this;
  }

  checkRowItemExists(itemName: string, exist = true) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .contains(itemName)
      .should((!exist ? "not." : "") + "exist");
    return this;
  }

  checkRowItemValueByItemName(itemName: string, column: number, value: string) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") + this.tableRowItem
    )
      .contains(itemName)
      .parentsUntil("tbody")
      .find("td:nth-child(" + column + ")")
      .should("have.text", value);
    return this;
  }

  checkRowItemValueByIndex(
    row: number,
    column: number,
    value: string,
    appendChildren?: string
  ) {
    cy.get(
      (this.tableInModal ? ".pf-c-modal-box.pf-m-md " : "") +
        this.tableRowItem +
        ":nth-child(" +
        row +
        ")"
    )
      .find("td:nth-child(" + column + ") " + appendChildren)
      .should("have.text", value)
      .should("have.value", value);
    return this;
  }
}
