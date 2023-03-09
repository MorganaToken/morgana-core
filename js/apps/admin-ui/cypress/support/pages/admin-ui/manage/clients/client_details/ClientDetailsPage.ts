import CommonPage from "../../../../CommonPage";
import AdvancedTab from "./tabs/AdvancedTab";
import AuthorizationTab from "./tabs/AuthorizationTab";
import ClientScopesTab from "./tabs/ClientScopesTab";
import CredentialsTab from "./tabs/CredentialsTab";
import KeysTab from "./tabs/KeysTab";
import RolesTab from "./tabs/RolesTab";
import SettingsTab from "./tabs/SettingsTab";

export enum ClientsDetailsTab {
  Settings = "Settings",
  Keys = "Keys",
  Credentials = "Credentials",
  Roles = "Roles",
  Sessions = "Sessions",
  Permissions = "Permissions",
  ClientScopes = "Client scopes",
  Authorization = "Authorization",
  ServiceAccountsRoles = "Service accounts roles",
  Advanced = "Advanced",
  Scope = "Scope",
}

export default class ClientDetailsPage extends CommonPage {
  private settingsTab = new SettingsTab();
  private keysTab = new KeysTab();
  private credentialsTab = new CredentialsTab();
  private rolesTab = new RolesTab();
  private clientScopesTab = new ClientScopesTab();
  private authorizationTab = new AuthorizationTab();
  private advancedTab = new AdvancedTab();

  goToSettingsTab() {
    this.tabUtils().clickTab(ClientsDetailsTab.Settings);
    return this.settingsTab;
  }

  goToKeysTab() {
    this.tabUtils().clickTab(ClientsDetailsTab.Keys);
    return this.keysTab;
  }

  goToCredentials() {
    this.tabUtils().clickTab(ClientsDetailsTab.Credentials);
    return this.credentialsTab;
  }

  goToRolesTab() {
    this.tabUtils().clickTab(ClientsDetailsTab.Roles);
    return this.rolesTab;
  }

  goToClientScopesTab() {
    this.tabUtils().clickTab(ClientsDetailsTab.ClientScopes);
    return this.clientScopesTab;
  }

  goToAuthorizationTab() {
    this.tabUtils().clickTab(ClientsDetailsTab.Authorization);
    return this.authorizationTab;
  }

  goToAdvancedTab() {
    this.tabUtils().clickTab(ClientsDetailsTab.Advanced);
    return this.advancedTab;
  }
}
