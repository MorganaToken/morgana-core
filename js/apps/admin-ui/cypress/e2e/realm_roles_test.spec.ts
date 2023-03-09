import LoginPage from "../support/pages/LoginPage";
import Masthead from "../support/pages/admin-ui/Masthead";
import ModalUtils from "../support/util/ModalUtils";
import ListingPage from "../support/pages/admin-ui/ListingPage";
import SidebarPage from "../support/pages/admin-ui/SidebarPage";
import createRealmRolePage from "../support/pages/admin-ui/manage/realm_roles/CreateRealmRolePage";
import AssociatedRolesPage from "../support/pages/admin-ui/manage/realm_roles/AssociatedRolesPage";
import { keycloakBefore } from "../support/util/keycloak_hooks";
import adminClient from "../support/util/AdminClient";
import ClientRolesTab from "../support/pages/admin-ui/manage/clients/ClientRolesTab";
import KeyValueInput from "../support/pages/admin-ui/manage/KeyValueInput";

let itemId = "realm_role_crud";
const loginPage = new LoginPage();
const masthead = new Masthead();
const modalUtils = new ModalUtils();
const sidebarPage = new SidebarPage();
const listingPage = new ListingPage();
const associatedRolesPage = new AssociatedRolesPage();
const rolesTab = new ClientRolesTab();

describe("Realm roles test", () => {
  beforeEach(() => {
    loginPage.logIn();
    keycloakBefore();
    sidebarPage.goToRealmRoles();
  });

  it("should fail creating realm role", () => {
    listingPage.goToCreateItem();
    createRealmRolePage.save().checkRealmRoleNameRequiredMessage();
    createRealmRolePage.fillRealmRoleData("admin").save();

    // The error should inform about duplicated name/id (THIS MESSAGE DOES NOT HAVE QUOTES AS THE OTHERS)
    masthead.checkNotificationMessage(
      "Could not create role: Role with name admin already exists",
      true
    );
  });

  it("shouldn't create a realm role based with only whitespace name", () => {
    listingPage.goToCreateItem();
    createRealmRolePage
      .fillRealmRoleData("  ")
      .checkRealmRoleNameRequiredMessage();
  });

  it("Realm role CRUD test", () => {
    itemId += "_" + crypto.randomUUID();

    // Create
    listingPage.itemExist(itemId, false).goToCreateItem();
    createRealmRolePage.fillRealmRoleData(itemId).save();
    masthead.checkNotificationMessage("Role created", true);
    sidebarPage.goToRealmRoles();

    const fetchUrl = "/admin/realms/master/roles?first=0&max=11";
    cy.intercept(fetchUrl).as("fetch");

    listingPage.deleteItem(itemId);

    cy.wait(["@fetch"]);
    modalUtils.checkModalTitle("Delete role?").confirmModal();
    masthead.checkNotificationMessage("The role has been deleted", true);

    listingPage.itemExist(itemId, false);

    itemId = "realm_role_crud";
  });

  it("should delete role from details action", () => {
    itemId += "_" + crypto.randomUUID();
    listingPage.goToCreateItem();
    createRealmRolePage.fillRealmRoleData(itemId).save();
    masthead.checkNotificationMessage("Role created", true);
    createRealmRolePage.clickActionMenu("Delete this role");
    modalUtils.confirmModal();
    masthead.checkNotificationMessage("The role has been deleted", true);
    itemId = "realm_role_crud";
  });

  it("should not be able to delete default role", () => {
    const defaultRole = "default-roles-master";
    listingPage.itemExist(defaultRole).deleteItem(defaultRole);
    masthead.checkNotificationMessage(
      "You cannot delete a default role.",
      true
    );
  });

  it("Add associated roles test", () => {
    itemId += "_" + crypto.randomUUID();

    // Create
    listingPage.itemExist(itemId, false).goToCreateItem();
    createRealmRolePage.fillRealmRoleData(itemId).save();
    masthead.checkNotificationMessage("Role created", true);

    // Add associated realm role from action dropdown
    associatedRolesPage.addAssociatedRealmRole("create-realm");
    masthead.checkNotificationMessage("Associated roles have been added", true);

    // Add associated realm role from search bar
    associatedRolesPage.addAssociatedRoleFromSearchBar("offline_access");
    masthead.checkNotificationMessage("Associated roles have been added", true);

    rolesTab.goToAssociatedRolesTab();

    // Add associated client role from search bar
    associatedRolesPage.addAssociatedRoleFromSearchBar("manage-account", true);
    masthead.checkNotificationMessage("Associated roles have been added", true);

    rolesTab.goToAssociatedRolesTab();

    // Add associated client role
    associatedRolesPage.addAssociatedRoleFromSearchBar("manage-consent", true);
    masthead.checkNotificationMessage("Associated roles have been added", true);

    rolesTab.goToAssociatedRolesTab();

    // Add associated client role
    associatedRolesPage.addAssociatedRoleFromSearchBar(
      "manage-account-links",
      true
    );
    masthead.checkNotificationMessage("Associated roles have been added", true);
  });

  it("Should search existing associated role by name", () => {
    listingPage.searchItem("create-realm", false).itemExist("create-realm");
  });

  it("Should search non-existent associated role by name", () => {
    const itemName = "non-existent-associated-role";
    listingPage.searchItem(itemName, false);
    cy.findByTestId(listingPage.emptyState).should("exist");
  });

  it("Should hide inherited roles test", () => {
    listingPage.searchItem(itemId, false).goToItemDetails(itemId);
    rolesTab.goToAssociatedRolesTab();
    rolesTab.hideInheritedRoles();
  });

  it("Should fail to remove role when all unchecked from search bar", () => {
    listingPage.searchItem(itemId, false).goToItemDetails(itemId);
    rolesTab.goToAssociatedRolesTab();
    associatedRolesPage.isRemoveAssociatedRolesBtnDisabled();
  });

  it("Should delete single non-inherited role item", () => {
    listingPage.searchItem(itemId, false).goToItemDetails(itemId);
    rolesTab.goToAssociatedRolesTab();
    listingPage.removeItem("create-realm");
    sidebarPage.waitForPageLoad();
    modalUtils.checkModalTitle("Remove role?").confirmModal();
    sidebarPage.waitForPageLoad();

    masthead.checkNotificationMessage(
      "Scope mapping successfully removed",
      true
    );
  });

  it("Should delete all roles from search bar", () => {
    listingPage.searchItem(itemId, false).goToItemDetails(itemId);
    sidebarPage.waitForPageLoad();
    rolesTab.goToAssociatedRolesTab();

    cy.get('input[name="check-all"]').check();

    associatedRolesPage.removeAssociatedRoles();

    sidebarPage.waitForPageLoad();
    modalUtils.checkModalTitle("Remove role?").confirmModal();
    sidebarPage.waitForPageLoad();

    masthead.checkNotificationMessage(
      "Scope mapping successfully removed",
      true
    );
  });

  it("Should delete associated roles from list test", () => {
    itemId = "realm_role_crud";
    itemId += "_" + crypto.randomUUID();

    // Create
    listingPage.itemExist(itemId, false).goToCreateItem();
    createRealmRolePage.fillRealmRoleData(itemId).save();
    masthead.checkNotificationMessage("Role created", true);

    // Add associated realm role from action dropdown
    associatedRolesPage.addAssociatedRealmRole("create-realm");
    masthead.checkNotificationMessage("Associated roles have been added", true);

    // Add associated realm role from search bar
    associatedRolesPage.addAssociatedRoleFromSearchBar("offline_access");
    masthead.checkNotificationMessage("Associated roles have been added", true);

    rolesTab.goToAssociatedRolesTab();

    // delete associated roles from list
    listingPage.removeItem("create-realm");
    sidebarPage.waitForPageLoad();
    modalUtils.checkModalTitle("Remove role?").confirmModal();
    sidebarPage.waitForPageLoad();

    masthead.checkNotificationMessage(
      "Scope mapping successfully removed",
      true
    );
    listingPage.removeItem("offline_access");
    sidebarPage.waitForPageLoad();
    modalUtils.checkModalTitle("Remove role?").confirmModal();
    sidebarPage.waitForPageLoad();

    masthead.checkNotificationMessage(
      "Scope mapping successfully removed",
      true
    );
  });

  describe("edit role details", () => {
    const editRoleName = "going to edit";
    const description = "some description";
    const updateDescription = "updated description";

    before(() =>
      adminClient.createRealmRole({
        name: editRoleName,
        description,
      })
    );

    after(() => adminClient.deleteRealmRole(editRoleName));

    it("should edit realm role details", () => {
      listingPage.itemExist(editRoleName).goToItemDetails(editRoleName);
      createRealmRolePage.checkNameDisabled().checkDescription(description);
      createRealmRolePage.updateDescription(updateDescription).save();
      masthead.checkNotificationMessage("The role has been saved", true);
      createRealmRolePage.checkDescription(updateDescription);
    });

    const keyValue = new KeyValueInput("attributes");
    it("should add attribute", () => {
      listingPage.itemExist(editRoleName).goToItemDetails(editRoleName);

      createRealmRolePage.goToAttributesTab();
      keyValue.fillKeyValue({ key: "one", value: "1" }).validateRows(2);
      keyValue.save();
      masthead.checkNotificationMessage("The role has been saved", true);
      keyValue.validateRows(2);
    });

    it("should add attribute multiple", () => {
      listingPage.itemExist(editRoleName).goToItemDetails(editRoleName);

      createRealmRolePage.goToAttributesTab();
      keyValue
        .fillKeyValue({ key: "two", value: "2" }, 1)
        .fillKeyValue({ key: "three", value: "3" }, 2)
        .save()
        .validateRows(4);
    });

    it("should delete attribute", () => {
      listingPage.itemExist(editRoleName).goToItemDetails(editRoleName);
      createRealmRolePage.goToAttributesTab();

      keyValue.deleteRow(1).save().validateRows(3);
    });
  });
});
