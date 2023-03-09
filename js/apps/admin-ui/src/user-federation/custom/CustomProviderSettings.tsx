import type ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  FormGroup,
  PageSection,
} from "@patternfly/react-core";
import { useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";

import { useAlerts } from "../../components/alert/Alerts";
import { DynamicComponents } from "../../components/dynamic/DynamicComponents";
import { FormAccess } from "../../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { KeycloakTextInput } from "../../components/keycloak-text-input/KeycloakTextInput";
import { useAdminClient, useFetch } from "../../context/auth/AdminClient";
import { useRealm } from "../../context/realm-context/RealmContext";
import { useServerInfo } from "../../context/server-info/ServerInfoProvider";
import { convertFormValuesToObject, convertToFormValues } from "../../util";
import { useParams } from "../../utils/useParams";
import type { CustomUserFederationRouteParams } from "../routes/CustomUserFederation";
import { toUserFederation } from "../routes/UserFederation";
import { ExtendedHeader } from "../shared/ExtendedHeader";
import { SettingsCache } from "../shared/SettingsCache";
import { SyncSettings } from "./SyncSettings";

import "./custom-provider-settings.css";

export default function CustomProviderSettings() {
  const { t } = useTranslation("user-federation");
  const { id, providerId } = useParams<CustomUserFederationRouteParams>();
  const navigate = useNavigate();
  const form = useForm<ComponentRepresentation>({
    mode: "onChange",
  });
  const {
    register,
    reset,
    setValue,
    handleSubmit,
    formState: { errors, isDirty },
  } = form;

  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();
  const { realm: realmName } = useRealm();
  const [parentId, setParentId] = useState("");

  const provider = (
    useServerInfo().componentTypes?.[
      "org.keycloak.storage.UserStorageProvider"
    ] || []
  ).find((p) => p.id === providerId);

  useFetch(
    async () => {
      if (id) {
        return await adminClient.components.findOne({ id });
      }
      return undefined;
    },
    (fetchedComponent) => {
      if (fetchedComponent) {
        convertToFormValues(fetchedComponent, setValue);
      } else if (id) {
        throw new Error(t("common:notFound"));
      }
    },
    []
  );

  useFetch(
    () =>
      adminClient.realms.findOne({
        realm: realmName,
      }),
    (realm) => setParentId(realm?.id!),
    []
  );

  const save = async (component: ComponentRepresentation) => {
    const saveComponent = convertFormValuesToObject({
      ...component,
      config: Object.fromEntries(
        Object.entries(component.config || {}).map(([key, value]) => [
          key,
          Array.isArray(value) ? value : [value],
        ])
      ),
      providerId,
      providerType: "org.keycloak.storage.UserStorageProvider",
      parentId,
    });

    try {
      if (!id) {
        await adminClient.components.create(saveComponent);
        navigate(toUserFederation({ realm: realmName }));
      } else {
        await adminClient.components.update({ id }, saveComponent);
      }
      reset({ ...component });
      addAlert(t(!id ? "createSuccess" : "saveSuccess"), AlertVariant.success);
    } catch (error) {
      addError(`user-federation:${!id ? "createError" : "saveError"}`, error);
    }
  };

  return (
    <FormProvider {...form}>
      <ExtendedHeader provider={providerId} save={() => handleSubmit(save)()} />
      <PageSection variant="light">
        <FormAccess
          role="manage-realm"
          isHorizontal
          className="keycloak__user-federation__custom-form"
          onSubmit={handleSubmit(save)}
        >
          <FormGroup
            label={t("uiDisplayName")}
            labelIcon={
              <HelpItem
                helpText={t("user-federation-help:uiDisplayNameHelp")}
                fieldLabelId="user-federation:uiDisplayName"
              />
            }
            helperTextInvalid={t("validateName")}
            validated={errors.name ? "error" : "default"}
            fieldId="kc-ui-display-name"
            isRequired
          >
            <KeycloakTextInput
              isRequired
              id="kc-ui-display-name"
              data-testid="ui-name"
              validated={errors.name ? "error" : "default"}
              {...register("name", {
                required: true,
              })}
            />
          </FormGroup>
          <FormProvider {...form}>
            <DynamicComponents properties={provider?.properties || []} />
            {provider?.metadata.synchronizable && <SyncSettings />}
          </FormProvider>
          <SettingsCache form={form} unWrap />
          <ActionGroup>
            <Button
              isDisabled={!isDirty}
              variant="primary"
              type="submit"
              data-testid="custom-save"
            >
              {t("common:save")}
            </Button>
            <Button
              variant="link"
              component={(props) => (
                <Link {...props} to={toUserFederation({ realm: realmName })} />
              )}
              data-testid="custom-cancel"
            >
              {t("common:cancel")}
            </Button>
          </ActionGroup>
        </FormAccess>
      </PageSection>
    </FormProvider>
  );
}
