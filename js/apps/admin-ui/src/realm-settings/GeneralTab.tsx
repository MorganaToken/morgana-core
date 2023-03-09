import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import {
  ActionGroup,
  Button,
  ClipboardCopy,
  FormGroup,
  PageSection,
  Select,
  SelectOption,
  SelectVariant,
  Stack,
  StackItem,
  Switch,
} from "@patternfly/react-core";
import { useEffect, useState } from "react";
import { Controller, FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { FormattedLink } from "../components/external-link/FormattedLink";
import { FormAccess } from "../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { KeyValueInput } from "../components/key-value-form/KeyValueInput";
import { KeycloakTextInput } from "../components/keycloak-text-input/KeycloakTextInput";
import { useAdminClient } from "../context/auth/AdminClient";
import { useRealm } from "../context/realm-context/RealmContext";
import {
  addTrailingSlash,
  convertAttributeNameToForm,
  convertToFormValues,
} from "../util";
import useIsFeatureEnabled, { Feature } from "../utils/useIsFeatureEnabled";

type RealmSettingsGeneralTabProps = {
  realm: RealmRepresentation;
  save: (realm: RealmRepresentation) => void;
};

export const RealmSettingsGeneralTab = ({
  realm,
  save,
}: RealmSettingsGeneralTabProps) => {
  const { t } = useTranslation("realm-settings");
  const { adminClient } = useAdminClient();
  const { realm: realmName } = useRealm();
  const form = useForm<RealmRepresentation>();
  const {
    register,
    control,
    handleSubmit,
    setValue,
    formState: { isDirty, errors },
  } = form;
  const isFeatureEnabled = useIsFeatureEnabled();
  const [open, setOpen] = useState(false);

  const requireSslTypes = ["all", "external", "none"];

  const setupForm = () => {
    convertToFormValues(realm, setValue);
    if (realm.attributes?.["acr.loa.map"]) {
      const result = Object.entries(
        JSON.parse(realm.attributes["acr.loa.map"])
      ).flatMap(([key, value]) => ({ key, value }));
      result.concat({ key: "", value: "" });
      setValue(
        convertAttributeNameToForm("attributes.acr.loa.map") as any,
        result
      );
    }
  };

  useEffect(setupForm, []);

  return (
    <PageSection variant="light">
      <FormAccess
        isHorizontal
        role="manage-realm"
        className="pf-u-mt-lg"
        onSubmit={handleSubmit(save)}
      >
        <FormGroup
          label={t("realmId")}
          fieldId="kc-realm-id"
          isRequired
          validated={errors.realm ? "error" : "default"}
          helperTextInvalid={errors.realm?.message}
        >
          <Controller
            name="realm"
            control={control}
            rules={{
              required: { value: true, message: t("common:required") },
              pattern: {
                value: /^[a-zA-Z0-9-_]+$/,
                message: t("realm:invalidRealmName"),
              },
            }}
            defaultValue=""
            render={({ field }) => (
              <ClipboardCopy data-testid="realmName" onChange={field.onChange}>
                {field.value}
              </ClipboardCopy>
            )}
          />
        </FormGroup>
        <FormGroup label={t("displayName")} fieldId="kc-display-name">
          <KeycloakTextInput
            id="kc-display-name"
            {...register("displayName")}
          />
        </FormGroup>
        <FormGroup label={t("htmlDisplayName")} fieldId="kc-html-display-name">
          <KeycloakTextInput
            id="kc-html-display-name"
            {...register("displayNameHtml")}
          />
        </FormGroup>
        <FormGroup
          label={t("frontendUrl")}
          fieldId="kc-frontend-url"
          labelIcon={
            <HelpItem
              helpText={t("realm-settings-help:frontendUrl")}
              fieldLabelId="realm-settings:frontendUrl"
            />
          }
        >
          <KeycloakTextInput
            type="url"
            id="kc-frontend-url"
            {...register(convertAttributeNameToForm("attributes.frontendUrl"))}
          />
        </FormGroup>
        <FormGroup
          label={t("requireSsl")}
          fieldId="kc-require-ssl"
          labelIcon={
            <HelpItem
              helpText={t("realm-settings-help:requireSsl")}
              fieldLabelId="realm-settings:requireSsl"
            />
          }
        >
          <Controller
            name="sslRequired"
            defaultValue="none"
            control={control}
            render={({ field }) => (
              <Select
                toggleId="kc-require-ssl"
                onToggle={() => setOpen(!open)}
                onSelect={(_, value) => {
                  field.onChange(value as string);
                  setOpen(false);
                }}
                selections={field.value}
                variant={SelectVariant.single}
                aria-label={t("requireSsl")}
                isOpen={open}
              >
                {requireSslTypes.map((sslType) => (
                  <SelectOption
                    selected={sslType === field.value}
                    key={sslType}
                    value={sslType}
                  >
                    {t(`sslType.${sslType}`)}
                  </SelectOption>
                ))}
              </Select>
            )}
          />
        </FormGroup>
        <FormGroup
          label={t("clients:acrToLoAMapping")}
          fieldId="acrToLoAMapping"
          labelIcon={
            <HelpItem
              helpText={t("clients-help:acrToLoAMapping")}
              fieldLabelId="clients:acrToLoAMapping"
            />
          }
        >
          <FormProvider {...form}>
            <KeyValueInput
              name={convertAttributeNameToForm("attributes.acr.loa.map")}
            />
          </FormProvider>
        </FormGroup>
        <FormGroup
          hasNoPaddingTop
          label={t("userManagedAccess")}
          labelIcon={
            <HelpItem
              helpText={t("realm-settings-help:userManagedAccess")}
              fieldLabelId="realm-settings:userManagedAccess"
            />
          }
          fieldId="kc-user-managed-access"
        >
          <Controller
            name="userManagedAccessAllowed"
            control={control}
            defaultValue={false}
            render={({ field }) => (
              <Switch
                id="kc-user-managed-access"
                data-testid="user-managed-access-switch"
                label={t("common:on")}
                labelOff={t("common:off")}
                isChecked={field.value}
                onChange={field.onChange}
                aria-label={t("userManagedAccess")}
              />
            )}
          />
        </FormGroup>
        {isFeatureEnabled(Feature.DeclarativeUserProfile) && (
          <FormGroup
            hasNoPaddingTop
            label={t("userProfileEnabled")}
            labelIcon={
              <HelpItem
                helpText={t("realm-settings-help:userProfileEnabled")}
                fieldLabelId="realm-settings:userProfileEnabled"
              />
            }
            fieldId="kc-user-profile-enabled"
          >
            <Controller
              name={
                convertAttributeNameToForm(
                  "attributes.userProfileEnabled"
                ) as any
              }
              control={control}
              defaultValue="false"
              render={({ field }) => (
                <Switch
                  id="kc-user-profile-enabled"
                  data-testid="user-profile-enabled-switch"
                  label={t("common:on")}
                  labelOff={t("common:off")}
                  isChecked={field.value === "true"}
                  onChange={(value) => field.onChange(value.toString())}
                  aria-label={t("userProfileEnabled")}
                />
              )}
            />
          </FormGroup>
        )}
        <FormGroup
          label={t("endpoints")}
          labelIcon={
            <HelpItem
              helpText={t("realm-settings-help:endpoints")}
              fieldLabelId="realm-settings:endpoints"
            />
          }
          fieldId="kc-endpoints"
        >
          <Stack>
            <StackItem>
              <FormattedLink
                href={`${addTrailingSlash(
                  adminClient.baseUrl
                )}realms/${realmName}/.well-known/openid-configuration`}
                title={t("openIDEndpointConfiguration")}
              />
            </StackItem>
            <StackItem>
              <FormattedLink
                href={`${addTrailingSlash(
                  adminClient.baseUrl
                )}realms/${realmName}/protocol/saml/descriptor`}
                title={t("samlIdentityProviderMetadata")}
              />
            </StackItem>
          </Stack>
        </FormGroup>

        <ActionGroup>
          <Button
            variant="primary"
            type="submit"
            data-testid="general-tab-save"
            isDisabled={!isDirty}
          >
            {t("common:save")}
          </Button>
          <Button
            data-testid="general-tab-revert"
            variant="link"
            onClick={setupForm}
          >
            {t("common:revert")}
          </Button>
        </ActionGroup>
      </FormAccess>
    </PageSection>
  );
};
