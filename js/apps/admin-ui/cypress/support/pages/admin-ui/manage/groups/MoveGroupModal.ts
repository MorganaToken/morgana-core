import ModalUtils from "../../../../util/ModalUtils";

export default class MoveGroupModal extends ModalUtils {
  private moveButton = "groups:moveHere-button";
  private title = ".pf-c-modal-box__title";

  clickRow(groupName: string) {
    cy.findByTestId(groupName).click();
    return this;
  }

  clickRoot() {
    cy.get(".pf-c-breadcrumb__item > button").click();
    return this;
  }

  checkTitle(title: string) {
    cy.get(this.title).should("have.text", title);
    return this;
  }

  clickMove() {
    cy.findByTestId(this.moveButton).click();
    return this;
  }
}
