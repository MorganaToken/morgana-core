import camelize from "camelize-ts";
import { defaultBaseUrl, defaultRealm } from "./constants.js";
import { fetchWithError } from "./fetchWithError.js";
import { stringifyQueryParams } from "./stringifyQueryParams.js";

export type GrantTypes = "client_credentials" | "password" | "refresh_token";

export interface Credentials {
  username?: string;
  password?: string;
  grantType: GrantTypes;
  clientId: string;
  clientSecret?: string;
  totp?: string;
  offlineToken?: boolean;
  refreshToken?: string;
  scopes?: string[];
}

export interface Settings {
  realmName?: string;
  baseUrl?: string;
  credentials: Credentials;
  requestOptions?: RequestInit;
}

export interface TokenResponseRaw {
  access_token: string;
  expires_in: string;
  refresh_expires_in: number;
  refresh_token: string;
  token_type: string;
  not_before_policy: number;
  session_state: string;
  scope: string;
  id_token?: string;
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: string;
  refreshExpiresIn: number;
  refreshToken: string;
  tokenType: string;
  notBeforePolicy: number;
  sessionState: string;
  scope: string;
  idToken?: string;
}

export const getToken = async (settings: Settings): Promise<TokenResponse> => {
  // Construct URL
  const baseUrl = settings.baseUrl || defaultBaseUrl;
  const realmName = settings.realmName || defaultRealm;
  const url = `${baseUrl}/realms/${realmName}/protocol/openid-connect/token`;

  // Prepare credentials for openid-connect token request
  // ref: http://openid.net/specs/openid-connect-core-1_0.html#TokenEndpoint
  const credentials = settings.credentials || ({} as any);
  const payload = stringifyQueryParams({
    username: credentials.username,
    password: credentials.password,
    grant_type: credentials.grantType,
    client_id: credentials.clientId,
    totp: credentials.totp,
    ...(credentials.offlineToken ? { scope: "offline_access" } : {}),
    ...(credentials.scopes ? { scope: credentials.scopes.join(" ") } : {}),
    ...(credentials.refreshToken
      ? {
          refresh_token: credentials.refreshToken,
          client_secret: credentials.clientSecret,
        }
      : {}),
  });

  const options = settings.requestOptions ?? {};
  const headers = new Headers(options.headers);

  if (credentials.clientSecret) {
    headers.set(
      "Authorization",
      atob(credentials.clientId + ":" + credentials.clientSecret)
    );
  }

  headers.set("content-type", "application/x-www-form-urlencoded");

  const response = await fetchWithError(url, {
    ...options,
    method: "POST",
    headers,
    body: payload,
  });

  const data: TokenResponseRaw = await response.json();
  return camelize(data);
};
