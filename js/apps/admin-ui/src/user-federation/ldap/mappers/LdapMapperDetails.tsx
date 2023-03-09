import type ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import type ComponentTypeRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentTypeRepresentation";
import { DirectionType } from "@keycloak/keycloak-admin-client/lib/resources/userStorageProvider";
import {
  ActionGroup,
  AlertVariant,
  Button,
  ButtonVariant,
  DropdownItem,
  Form,
  FormGroup,
  PageSection,
  Select,
  SelectOption,
  SelectVariant,
  ValidatedOptions,
} from "@patternfly/react-core";
import { useState } from "react";
import { Controller, FormProvider, useForm, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { useAlerts } from "../../../components/alert/Alerts";
import { useConfirmDialog } from "../../../components/confirm-dialog/ConfirmDialog";
import { DynamicComponents } from "../../../components/dynamic/DynamicComponents";
import { FormAccess } from "../../../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { KeycloakSpinner } from "../../../components/keycloak-spinner/KeycloakSpinner";
import { KeycloakTextInput } from "../../../components/keycloak-text-input/KeycloakTextInput";
import { ViewHeader } from "../../../components/view-header/ViewHeader";
import { useAdminClient, useFetch } from "../../../context/auth/AdminClient";
import { useRealm } from "../../../context/realm-context/RealmContext";
import { convertFormValuesToObject, convertToFormValues } from "../../../util";
import { useParams } from "../../../utils/useParams";
import { toUserFederationLdap } from "../../routes/UserFederationLdap";
import { UserFederationLdapMapperParams } from "../../routes/UserFederationLdapMapper";

export default function LdapMapperDetails() {
  const form = useForm<ComponentRepresentation>();
  const [mapping, setMapping] = useState<ComponentRepresentation>();
  const [components, setComponents] = useState<ComponentTypeRepresentation[]>();

  const { adminClient } = useAdminClient();
  const { id, mapperId } = useParams<UserFederationLdapMapperParams>();
  const navigate = useNavigate();
  const { realm } = useRealm();
  const { t } = useTranslation("user-federation");
  const { addAlert, addError } = useAlerts();

  const [isMapperDropdownOpen, setIsMapperDropdownOpen] = useState(false);
  const [key, setKey] = useState(0);
  const refresh = () => setKey(key + 1);

  useFetch(
    async () => {
      const components = await adminClient.components.listSubComponents({
        id,
        type: "org.keycloak.storage.ldap.mappers.LDAPStorageMapper",
      });
      if (mapperId && mapperId !== "new") {
        const fetchedMapper = await adminClient.components.findOne({
          id: mapperId,
        });
        return { components, fetchedMapper };
      }
      return { components };
    },
    ({ components, fetchedMapper }) => {
      setMapping(fetchedMapper);
      setComponents(components);
      if (mapperId !== "new" && !fetchedMapper)
        throw new Error(t("common:notFound"));

      if (fetchedMapper) setupForm(fetchedMapper);
    },
    []
  );

  const setupForm = (mapper: ComponentRepresentation) => {
    convertToFormValues(mapper, form.setValue);
  };

  const save = async (mapper: ComponentRepresentation) => {
    const component: ComponentRepresentation =
      convertFormValuesToObject(mapper);
    const map = {
      ...component,
      config: Object.entries(component.config || {}).reduce(
        (result, [key, value]) => {
          result[key] = Array.isArray(value) ? value : [value];
          return result;
        },
        {} as Record<string, string | string[]>
      ),
    };

    try {
      if (mapperId === "new") {
        await adminClient.components.create(map);
        navigate(
          toUserFederationLdap({ realm, id: mapper.parentId!, tab: "mappers" })
        );
      } else {
        await adminClient.components.update({ id: mapperId }, map);
      }
      setupForm(map as ComponentRepresentation);
      addAlert(
        t(
          mapperId === "new"
            ? "common:mappingCreatedSuccess"
            : "common:mappingUpdatedSuccess"
        ),
        AlertVariant.success
      );
    } catch (error) {
      addError(
        mapperId === "new"
          ? "common:mappingCreatedError"
          : "common:mappingUpdatedError",
        error
      );
    }
  };

  const sync = async (direction: DirectionType) => {
    try {
      const result = await adminClient.userStorageProvider.mappersSync({
        parentId: mapping?.parentId || "",
        id: mapperId,
        direction,
      });
      addAlert(
        t("syncLDAPGroupsSuccessful", {
          result: result.status,
        })
      );
    } catch (error) {
      addError("user-federation:syncLDAPGroupsError", error);
    }
    refresh();
  };

  const [toggleDeleteDialog, DeleteConfirm] = useConfirmDialog({
    titleKey: "common:deleteMappingTitle",
    messageKey: "common:deleteMappingConfirm",
    continueButtonLabel: "common:delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.components.del({
          id: mapping!.id!,
        });
        addAlert(t("common:mappingDeletedSuccess"), AlertVariant.success);
        navigate(toUserFederationLdap({ id, realm, tab: "mappers" }));
      } catch (error) {
        addError("common:mappingDeletedError", error);
      }
    },
  });

  const mapperType = useWatch({
    control: form.control,
    name: "providerId",
  });

  if (!components) {
    return <KeycloakSpinner />;
  }

  const isNew = mapperId === "new";
  const mapper = components.find((c) => c.id === mapperType);
  return (
    <>
      <DeleteConfirm />
      <ViewHeader
        key={key}
        titleKey={mapping ? mapping.name! : t("common:createNewMapper")}
        dropdownItems={
          isNew
            ? undefined
            : [
                <DropdownItem key="delete" onClick={toggleDeleteDialog}>
                  {t("common:delete")}
                </DropdownItem>,
                mapper?.metadata.fedToKeycloakSyncSupported && (
                  <DropdownItem
                    key="fedSync"
                    onClick={() => sync("fedToKeycloak")}
                  >
                    {t("syncLDAPGroupsToKeycloak")}
                  </DropdownItem>
                ),
                mapper?.metadata.keycloakToFedSyncSupported && (
                  <DropdownItem
                    key="ldapSync"
                    onClick={() => {
                      sync("keycloakToFed");
                    }}
                  >
                    {t("syncKeycloakGroupsToLDAP")}
                  </DropdownItem>
                ),
              ]
        }
      />
      <PageSection variant="light" isFilled>
        <FormAccess role="manage-realm" isHorizontal>
          {!isNew && (
            <FormGroup label={t("common:id")} fieldId="kc-ldap-mapper-id">
              <KeycloakTextInput
                isDisabled
                id="kc-ldap-mapper-id"
                data-testid="ldap-mapper-id"
                {...form.register("id")}
              />
            </FormGroup>
          )}
          <FormGroup
            label={t("common:name")}
            labelIcon={
              <HelpItem
                helpText={t("user-federation-help:nameHelp")}
                fieldLabelId="name"
              />
            }
            fieldId="kc-ldap-mapper-name"
            isRequired
          >
            <KeycloakTextInput
              isDisabled={!isNew}
              isRequired
              id="kc-ldap-mapper-name"
              data-testid="ldap-mapper-name"
              validated={
                form.formState.errors.name
                  ? ValidatedOptions.error
                  : ValidatedOptions.default
              }
              {...form.register("name", { required: true })}
            />
            <KeycloakTextInput
              hidden
              defaultValue={isNew ? id : mapping ? mapping.parentId : ""}
              id="kc-ldap-parentId"
              data-testid="ldap-mapper-parentId"
              {...form.register("parentId")}
            />
            <KeycloakTextInput
              hidden
              defaultValue="org.keycloak.storage.ldap.mappers.LDAPStorageMapper"
              id="kc-ldap-provider-type"
              data-testid="ldap-mapper-provider-type"
              {...form.register("providerType")}
            />
          </FormGroup>
          {!isNew ? (
            <FormGroup
              label={t("common:mapperType")}
              labelIcon={
                <HelpItem
                  helpText={
                    mapper?.helpText
                      ? mapper.helpText
                      : t("user-federation-help:mapperTypeHelp")
                  }
                  fieldLabelId="mapperType"
                />
              }
              fieldId="kc-ldap-mapper-type"
              isRequired
            >
              <KeycloakTextInput
                isDisabled={!isNew}
                isRequired
                id="kc-ldap-mapper-type"
                data-testid="ldap-mapper-type-fld"
                {...form.register("providerId")}
              />
            </FormGroup>
          ) : (
            <FormGroup
              label={t("common:mapperType")}
              labelIcon={
                <HelpItem
                  helpText={
                    mapper?.helpText
                      ? mapper.helpText
                      : t("user-federation-help:mapperTypeHelp")
                  }
                  fieldLabelId="mapperType"
                />
              }
              fieldId="kc-providerId"
              isRequired
            >
              <Controller
                name="providerId"
                defaultValue=""
                control={form.control}
                data-testid="ldap-mapper-type-select"
                render={({ field }) => (
                  <Select
                    toggleId="kc-providerId"
                    required
                    onToggle={() =>
                      setIsMapperDropdownOpen(!isMapperDropdownOpen)
                    }
                    isOpen={isMapperDropdownOpen}
                    onSelect={(_, value) => {
                      field.onChange(value as string);
                      setIsMapperDropdownOpen(false);
                    }}
                    selections={field.value}
                    variant={SelectVariant.typeahead}
                  >
                    {components.map((c) => (
                      <SelectOption key={c.id} value={c.id} />
                    ))}
                  </Select>
                )}
              ></Controller>
            </FormGroup>
          )}
          <FormProvider {...form}>
            {!!mapperType && (
              <DynamicComponents properties={mapper?.properties!} />
            )}
          </FormProvider>
        </FormAccess>

        <Form onSubmit={form.handleSubmit(() => save(form.getValues()))}>
          <ActionGroup>
            <Button
              isDisabled={!form.formState.isDirty}
              variant="primary"
              type="submit"
              data-testid="ldap-mapper-save"
            >
              {t("common:save")}
            </Button>
            <Button
              variant="link"
              onClick={() =>
                isNew
                  ? navigate(-1)
                  : navigate(
                      `/${realm}/user-federation/ldap/${
                        mapping!.parentId
                      }/mappers`
                    )
              }
              data-testid="ldap-mapper-cancel"
            >
              {t("common:cancel")}
            </Button>
          </ActionGroup>
        </Form>
      </PageSection>
    </>
  );
}
