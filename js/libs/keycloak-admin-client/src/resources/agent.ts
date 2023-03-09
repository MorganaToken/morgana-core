import { isUndefined, last, omit, pick } from "lodash-es";
import urlJoin from "url-join";
import { parseTemplate } from "url-template";
import type { KeycloakAdminClient } from "../client.js";
import {
  fetchWithError,
  NetworkError,
  parseResponse,
} from "../utils/fetchWithError.js";
import { stringifyQueryParams } from "../utils/stringifyQueryParams.js";

// constants
const SLASH = "/";

type Method = "GET" | "POST" | "PUT" | "DELETE";

// interface
export interface RequestArgs {
  method: Method;
  path?: string;
  // Keys of url params to be applied
  urlParamKeys?: string[];
  // Keys of query parameters to be applied
  queryParamKeys?: string[];
  // Mapping of key transformations to be performed on the payload
  keyTransform?: Record<string, string>;
  // If responding with 404, catch it and return null instead
  catchNotFound?: boolean;
  // The key of the value to use from the payload of request. Only works for POST & PUT.
  payloadKey?: string;
  // Whether the response header have a location field with newly created resource id
  // if this value is set, we return the field with format: {[field]: resourceId}
  // to represent the newly created resource
  // detail: keycloak/keycloak-nodejs-admin-client issue #11
  returnResourceIdInLocationHeader?: { field: string };
  /**
   * Keys to be ignored, meaning that they will not be filtered out of the request payload even if they are a part of `urlParamKeys` or `queryParamKeys`,
   */
  ignoredKeys?: string[];
  headers?: HeadersInit;
}

export class Agent {
  private client: KeycloakAdminClient;
  private basePath: string;
  private getBaseParams?: () => Record<string, any>;
  private getBaseUrl?: () => string;

  constructor({
    client,
    path = "/",
    getUrlParams = () => ({}),
    getBaseUrl = () => client.baseUrl,
  }: {
    client: KeycloakAdminClient;
    path?: string;
    getUrlParams?: () => Record<string, any>;
    getBaseUrl?: () => string;
  }) {
    this.client = client;
    this.getBaseParams = getUrlParams;
    this.getBaseUrl = getBaseUrl;
    this.basePath = path;
  }

  public request({
    method,
    path = "",
    urlParamKeys = [],
    queryParamKeys = [],
    catchNotFound = false,
    keyTransform,
    payloadKey,
    returnResourceIdInLocationHeader,
    ignoredKeys,
    headers,
  }: RequestArgs) {
    return async (
      payload: any = {},
      options?: Pick<RequestArgs, "catchNotFound">
    ) => {
      const baseParams = this.getBaseParams?.() ?? {};

      // Filter query parameters by queryParamKeys
      const queryParams = queryParamKeys
        ? pick(payload, queryParamKeys)
        : undefined;

      // Add filtered payload parameters to base parameters
      const allUrlParamKeys = [...Object.keys(baseParams), ...urlParamKeys];
      const urlParams = { ...baseParams, ...pick(payload, allUrlParamKeys) };

      // Omit url parameters and query parameters from payload
      const omittedKeys = ignoredKeys
        ? [...allUrlParamKeys, ...queryParamKeys].filter(
            (key) => !ignoredKeys.includes(key)
          )
        : [...allUrlParamKeys, ...queryParamKeys];

      payload = omit(payload, omittedKeys);

      // Transform keys of both payload and queryParams
      if (keyTransform) {
        this.transformKey(payload, keyTransform);
        this.transformKey(queryParams, keyTransform);
      }

      return this.requestWithParams({
        method,
        path,
        payload,
        urlParams,
        queryParams,
        // catchNotFound precedence: global > local > default
        catchNotFound,
        ...(this.client.getGlobalRequestArgOptions() ?? options ?? {}),
        payloadKey,
        returnResourceIdInLocationHeader,
        headers,
      });
    };
  }

  public updateRequest({
    method,
    path = "",
    urlParamKeys = [],
    queryParamKeys = [],
    catchNotFound = false,
    keyTransform,
    payloadKey,
    returnResourceIdInLocationHeader,
    headers,
  }: RequestArgs) {
    return async (query: any = {}, payload: any = {}) => {
      const baseParams = this.getBaseParams?.() ?? {};

      // Filter query parameters by queryParamKeys
      const queryParams = queryParamKeys
        ? pick(query, queryParamKeys)
        : undefined;

      // Add filtered query parameters to base parameters
      const allUrlParamKeys = [...Object.keys(baseParams), ...urlParamKeys];
      const urlParams = {
        ...baseParams,
        ...pick(query, allUrlParamKeys),
      };

      // Transform keys of queryParams
      if (keyTransform) {
        this.transformKey(queryParams, keyTransform);
      }

      return this.requestWithParams({
        method,
        path,
        payload,
        urlParams,
        queryParams,
        catchNotFound,
        payloadKey,
        returnResourceIdInLocationHeader,
        headers,
      });
    };
  }

  private async requestWithParams({
    method,
    path,
    payload,
    urlParams,
    queryParams,
    catchNotFound,
    payloadKey,
    returnResourceIdInLocationHeader,
    headers,
  }: {
    method: Method;
    path: string;
    payload: any;
    urlParams: any;
    queryParams?: Record<string, string>;
    catchNotFound: boolean;
    payloadKey?: string;
    returnResourceIdInLocationHeader?: { field: string };
    headers?: HeadersInit;
  }) {
    const newPath = urlJoin(this.basePath, path);

    // Parse template and replace with values from urlParams
    const pathTemplate = parseTemplate(newPath);
    const parsedPath = pathTemplate.expand(urlParams);
    const url = new URL(`${this.getBaseUrl?.() ?? ""}${parsedPath}`);
    const requestOptions = { ...this.client.getRequestOptions() };
    const requestHeaders = new Headers([
      ...new Headers(requestOptions.headers).entries(),
      ["authorization", `Bearer ${await this.client.getAccessToken()}`],
      ["accept", "application/json, text/plain, */*"],
      ...new Headers(headers).entries(),
    ]);

    const searchParams: Record<string, string> = {};

    // Add payload parameters to search params if method is 'GET'.
    if (method === "GET") {
      Object.assign(searchParams, payload);
    } else if (requestHeaders.get("content-type") === "text/plain") {
      // Pass the payload as a plain string if the content type is 'text/plain'.
      requestOptions.body = payload as unknown as string;
    } else if (payload instanceof FormData) {
      requestOptions.body = payload;
    } else {
      // Otherwise assume it's JSON and stringify it.
      requestOptions.body = JSON.stringify(
        payloadKey ? payload[payloadKey] : payload
      );
    }

    if (!requestHeaders.has("content-type") && !(payload instanceof FormData)) {
      requestHeaders.set("content-type", "application/json");
    }

    if (queryParams) {
      Object.assign(searchParams, queryParams);
    }

    url.search = stringifyQueryParams(searchParams);

    try {
      const res = await fetchWithError(url, {
        ...requestOptions,
        headers: requestHeaders,
        method,
      });

      // now we get the response of the http request
      // if `resourceIdInLocationHeader` is true, we'll get the resourceId from the location header field
      // todo: find a better way to find the id in path, maybe some kind of pattern matching
      // for now, we simply split the last sub-path of the path returned in location header field
      if (returnResourceIdInLocationHeader) {
        const locationHeader = res.headers.get("location");

        if (typeof locationHeader !== "string") {
          throw new Error(
            `location header is not found in request: ${res.url}`
          );
        }

        const resourceId = last(locationHeader.split(SLASH));
        if (!resourceId) {
          // throw an error to let users know the response is not expected
          throw new Error(
            `resourceId is not found in Location header from request: ${res.url}`
          );
        }

        // return with format {[field]: string}
        const { field } = returnResourceIdInLocationHeader;
        return { [field]: resourceId };
      }

      if (
        Object.entries(headers || []).find(
          ([key, value]) =>
            key.toLowerCase() === "accept" &&
            value === "application/octet-stream"
        )
      ) {
        return res.arrayBuffer();
      }

      return parseResponse(res);
    } catch (err) {
      if (
        err instanceof NetworkError &&
        err.response.status === 404 &&
        catchNotFound
      ) {
        return null;
      }
      throw err;
    }
  }

  private transformKey(payload: any, keyMapping: Record<string, string>) {
    if (!payload) {
      return;
    }

    Object.keys(keyMapping).some((key) => {
      if (isUndefined(payload[key])) {
        // Skip if undefined
        return false;
      }
      const newKey = keyMapping[key];
      payload[newKey] = payload[key];
      delete payload[key];
    });
  }
}
