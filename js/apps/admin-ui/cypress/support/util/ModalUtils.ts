import PageObject from "../pages/admin-ui/components/PageObject";
import TablePage from "../pages/admin-ui/components/TablePage";

export default class ModalUtils extends PageObject {
  private modalDiv = ".pf-c-modal-box";
  private modalTitle = ".pf-c-modal-box .pf-c-modal-box__title-text";
  private modalMessage = ".pf-c-modal-box .pf-c-modal-box__body";
  private confirmModalBtn = "confirm";
  private cancelModalBtn = "cancel";
  private closeModalBtn = ".pf-c-modal-box .pf-m-plain";
  private copyToClipboardBtn = '[id*="copy-button"]';
  private addModalDropdownBtn = "#add-dropdown > button";
  private addModalDropdownItem = "#add-dropdown [role='menuitem']";
  private primaryBtn = ".pf-c-button.pf-m-primary";
  private addBtn = "add";
  private tablePage = new TablePage(TablePage.tableSelector);

  table() {
    return this.tablePage;
  }

  add() {
    cy.findByTestId(this.addBtn).click();
    return this;
  }

  confirmModal() {
    cy.findByTestId(this.confirmModalBtn).click({ force: true });

    return this;
  }

  checkConfirmButtonText(text: string) {
    cy.findByTestId(this.confirmModalBtn).contains(text);

    return this;
  }

  confirmModalWithItem(itemName: string) {
    cy.get(this.addModalDropdownBtn).click();
    cy.get(this.addModalDropdownItem).contains(itemName).click();

    return this;
  }

  cancelModal() {
    cy.findByTestId(this.cancelModalBtn).click({ force: true });

    return this;
  }

  cancelButtonContains(text: string) {
    cy.findByTestId(this.cancelModalBtn).contains(text);

    return this;
  }

  copyToClipboard() {
    cy.get(this.copyToClipboardBtn).click();

    return this;
  }

  closeModal() {
    cy.get(this.closeModalBtn).click({ force: true });

    return this;
  }

  checkModalTitle(title: string) {
    //deprecated
    this.assertModalTitleEqual(title);
    return this;
  }

  checkModalMessage(message: string) {
    cy.get(this.modalMessage).invoke("text").should("eq", message);

    return this;
  }

  assertModalMessageContainText(text: string) {
    cy.get(this.modalMessage).should("contain.text", text);
    return this;
  }

  assertModalHasElement(elementSelector: string, exist: boolean) {
    cy.get(this.modalDiv)
      .find(elementSelector)
      .should((exist ? "" : ".not") + "exist");
    return this;
  }

  assertModalVisible(isVisible: boolean) {
    super.assertIsVisible(cy.get(this.modalDiv), isVisible);
    return this;
  }

  assertModalExist(exist: boolean) {
    super.assertExist(cy.get(this.modalDiv), exist);
    return this;
  }

  assertModalTitleEqual(text: string) {
    cy.get(this.modalTitle).invoke("text").should("eq", text);
    return this;
  }
}
