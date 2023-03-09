/**
 * https://www.keycloak.org/docs-api/11.0/rest-api/index.html#_grouprepresentation
 */

export default interface GroupRepresentation {
  id?: string;
  name?: string;
  path?: string;
  subGroups?: GroupRepresentation[];

  // optional in response
  access?: Record<string, boolean>;
  attributes?: Record<string, any>;
  clientRoles?: Record<string, any>;
  realmRoles?: string[];
}
