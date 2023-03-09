import type ClientScopeRepresentation from "@keycloak/keycloak-admin-client/lib/defs/clientScopeRepresentation";
import type UserProfileConfig from "@keycloak/keycloak-admin-client/lib/defs/userProfileConfig";
import {
  Divider,
  FormGroup,
  Radio,
  Select,
  SelectOption,
  SelectVariant,
  Switch,
} from "@patternfly/react-core";
import { isEqual } from "lodash-es";
import { useState } from "react";
import { Controller, useFormContext, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { FormAccess } from "../../../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { KeycloakSpinner } from "../../../components/keycloak-spinner/KeycloakSpinner";
import { KeycloakTextInput } from "../../../components/keycloak-text-input/KeycloakTextInput";
import { useAdminClient, useFetch } from "../../../context/auth/AdminClient";
import { useParams } from "../../../utils/useParams";
import { USERNAME_EMAIL } from "../../NewAttributeSettings";
import type { AttributeParams } from "../../routes/Attribute";

import "../../realm-settings-section.css";

const REQUIRED_FOR = [
  { label: "requiredForLabel.both", value: ["admin", "user"] },
  { label: "requiredForLabel.users", value: ["user"] },
  { label: "requiredForLabel.admins", value: ["admin"] },
] as const;

export const AttributeGeneralSettings = () => {
  const { t } = useTranslation("realm-settings");
  const { adminClient } = useAdminClient();
  const form = useFormContext();
  const [clientScopes, setClientScopes] =
    useState<ClientScopeRepresentation[]>();
  const [config, setConfig] = useState<UserProfileConfig>();
  const [selectEnabledWhenOpen, setSelectEnabledWhenOpen] = useState(false);
  const [selectRequiredForOpen, setSelectRequiredForOpen] = useState(false);
  const [isAttributeGroupDropdownOpen, setIsAttributeGroupDropdownOpen] =
    useState(false);
  const { attributeName } = useParams<AttributeParams>();
  const editMode = attributeName ? true : false;

  const selectedScopes = useWatch({
    control: form.control,
    name: "selector.scopes",
    defaultValue: [],
  });

  const requiredScopes = useWatch({
    control: form.control,
    name: "required.scopes",
    defaultValue: [],
  });

  const required = useWatch({
    control: form.control,
    name: "isRequired",
    defaultValue: false,
  });

  useFetch(() => adminClient.clientScopes.find(), setClientScopes, []);
  useFetch(() => adminClient.users.getProfile(), setConfig, []);

  if (!clientScopes) {
    return <KeycloakSpinner />;
  }
  return (
    <FormAccess role="manage-realm" isHorizontal>
      <FormGroup
        label={t("attributeName")}
        labelIcon={
          <HelpItem
            helpText={t("realm-settings-help:attributeNameHelp")}
            fieldLabelId="realm-settings:attributeName"
          />
        }
        fieldId="kc-attribute-name"
        isRequired
        validated={form.formState.errors.name ? "error" : "default"}
        helperTextInvalid={t("validateName")}
      >
        <KeycloakTextInput
          isRequired
          id="kc-attribute-name"
          defaultValue=""
          data-testid="attribute-name"
          isDisabled={editMode}
          validated={form.formState.errors.name ? "error" : "default"}
          {...form.register("name", { required: true })}
        />
      </FormGroup>
      <FormGroup
        label={t("attributeDisplayName")}
        labelIcon={
          <HelpItem
            helpText={t("realm-settings-help:attributeDisplayNameHelp")}
            fieldLabelId="realm-settings:attributeDisplayName"
          />
        }
        fieldId="kc-attribute-display-name"
      >
        <KeycloakTextInput
          id="kc-attribute-display-name"
          defaultValue=""
          data-testid="attribute-display-name"
          {...form.register("displayName")}
        />
      </FormGroup>
      <FormGroup
        label={t("attributeGroup")}
        labelIcon={
          <HelpItem
            helpText={t("realm-setting-help:attributeGroupHelp")}
            fieldLabelId="realm-setting:attributeGroup"
          />
        }
        fieldId="kc-attribute-group"
      >
        <Controller
          name="group"
          defaultValue=""
          control={form.control}
          render={({ field }) => (
            <Select
              toggleId="kc-attributeGroup"
              onToggle={() =>
                setIsAttributeGroupDropdownOpen(!isAttributeGroupDropdownOpen)
              }
              isOpen={isAttributeGroupDropdownOpen}
              onSelect={(_, value) => {
                field.onChange(value.toString());
                setIsAttributeGroupDropdownOpen(false);
              }}
              selections={[field.value || t("common:none")]}
              variant={SelectVariant.single}
            >
              {[
                <SelectOption key="empty" value="">
                  {t("common:none")}
                </SelectOption>,
                ...(config?.groups?.map((group) => (
                  <SelectOption key={group.name} value={group.name}>
                    {group.name}
                  </SelectOption>
                )) || []),
              ]}
            </Select>
          )}
        ></Controller>
      </FormGroup>
      {!USERNAME_EMAIL.includes(attributeName) && (
        <>
          <Divider />
          <FormGroup
            label={t("enabledWhen")}
            fieldId="enabledWhen"
            hasNoPaddingTop
          >
            <Radio
              id="always"
              data-testid="always"
              isChecked={selectedScopes.length === clientScopes.length}
              name="enabledWhen"
              label={t("always")}
              onChange={(value) => {
                if (value) {
                  form.setValue(
                    "selector.scopes",
                    clientScopes.map((s) => s.name)
                  );
                } else {
                  form.setValue("selector.scopes", []);
                }
              }}
              className="pf-u-mb-md"
            />
            <Radio
              id="scopesAsRequested"
              data-testid="scopesAsRequested"
              isChecked={selectedScopes.length !== clientScopes.length}
              name="enabledWhen"
              label={t("scopesAsRequested")}
              onChange={(value) => {
                if (value) {
                  form.setValue("selector.scopes", []);
                } else {
                  form.setValue(
                    "selector.scopes",
                    clientScopes.map((s) => s.name)
                  );
                }
              }}
              className="pf-u-mb-md"
            />
          </FormGroup>
          <FormGroup fieldId="kc-scope-enabled-when">
            <Controller
              name="selector.scopes"
              control={form.control}
              defaultValue={clientScopes.map((s) => s.name)}
              render={({ field }) => (
                <Select
                  name="scopes"
                  data-testid="enabled-when-scope-field"
                  variant={SelectVariant.typeaheadMulti}
                  typeAheadAriaLabel="Select"
                  chipGroupProps={{
                    numChips: 3,
                    expandedText: t("common:hide"),
                    collapsedText: t("common:showRemaining"),
                  }}
                  onToggle={(isOpen) => setSelectEnabledWhenOpen(isOpen)}
                  selections={field.value}
                  onSelect={(_, selectedValue) => {
                    const option = selectedValue.toString();
                    let changedValue = [""];
                    if (field.value) {
                      changedValue = field.value.includes(option)
                        ? field.value.filter((item: string) => item !== option)
                        : [...field.value, option];
                    } else {
                      changedValue = [option];
                    }

                    field.onChange(changedValue);
                  }}
                  onClear={(selectedValues) => {
                    selectedValues.stopPropagation();
                    field.onChange([]);
                  }}
                  isOpen={selectEnabledWhenOpen}
                  isDisabled={selectedScopes.length === clientScopes.length}
                  aria-labelledby={"scope"}
                >
                  {clientScopes.map((option) => (
                    <SelectOption key={option.name} value={option.name} />
                  ))}
                </Select>
              )}
            />
          </FormGroup>

          <Divider />
          <FormGroup
            label={t("required")}
            labelIcon={
              <HelpItem
                helpText={t("realm-settings-help:requiredHelp")}
                fieldLabelId="realm-settings:required"
              />
            }
            fieldId="kc-required"
            hasNoPaddingTop
          >
            <Controller
              name="isRequired"
              data-testid="required"
              defaultValue={false}
              control={form.control}
              render={({ field }) => (
                <Switch
                  id={"kc-required"}
                  onChange={field.onChange}
                  isChecked={field.value}
                  label={t("common:on")}
                  labelOff={t("common:off")}
                  aria-label={t("required")}
                />
              )}
            />
          </FormGroup>
          {required && (
            <>
              <FormGroup
                label={t("requiredFor")}
                fieldId="requiredFor"
                hasNoPaddingTop
              >
                <Controller
                  name="required.roles"
                  data-testid="requiredFor"
                  defaultValue={REQUIRED_FOR[0].value}
                  control={form.control}
                  render={({ field }) => (
                    <div className="kc-requiredFor">
                      {REQUIRED_FOR.map((option) => (
                        <Radio
                          id={option.label}
                          key={option.label}
                          data-testid={option.label}
                          isChecked={isEqual(field.value, option.value)}
                          name="roles"
                          onChange={() => {
                            field.onChange(option.value);
                          }}
                          label={t(option.label)}
                          className="kc-requiredFor-option"
                        />
                      ))}
                    </div>
                  )}
                />
              </FormGroup>
              <FormGroup
                label={t("requiredWhen")}
                fieldId="requiredWhen"
                hasNoPaddingTop
              >
                <Radio
                  id="requiredAlways"
                  data-testid="requiredAlways"
                  isChecked={requiredScopes.length === clientScopes.length}
                  name="requiredWhen"
                  label={t("always")}
                  onChange={(value) => {
                    if (value) {
                      form.setValue(
                        "required.scopes",
                        clientScopes.map((s) => s.name)
                      );
                    } else {
                      form.setValue("required.scopes", []);
                    }
                  }}
                  className="pf-u-mb-md"
                />
                <Radio
                  id="requiredScopesAsRequested"
                  data-testid="requiredScopesAsRequested"
                  isChecked={requiredScopes.length !== clientScopes.length}
                  name="requiredWhen"
                  label={t("scopesAsRequested")}
                  onChange={(value) => {
                    if (value) {
                      form.setValue("required.scopes", []);
                    } else {
                      form.setValue(
                        "required.scopes",
                        clientScopes.map((s) => s.name)
                      );
                    }
                  }}
                  className="pf-u-mb-md"
                />
              </FormGroup>
              <FormGroup fieldId="kc-scope-required-when">
                <Controller
                  name="required.scopes"
                  control={form.control}
                  defaultValue={[]}
                  render={({ field }) => (
                    <Select
                      name="scopeRequired"
                      data-testid="required-when-scope-field"
                      variant={SelectVariant.typeaheadMulti}
                      typeAheadAriaLabel="Select"
                      chipGroupProps={{
                        numChips: 3,
                        expandedText: t("common:hide"),
                        collapsedText: t("common:showRemaining"),
                      }}
                      onToggle={(isOpen) => setSelectRequiredForOpen(isOpen)}
                      selections={field.value}
                      onSelect={(_, selectedValue) => {
                        const option = selectedValue.toString();
                        let changedValue = [""];
                        if (field.value) {
                          changedValue = field.value.includes(option)
                            ? field.value.filter(
                                (item: string) => item !== option
                              )
                            : [...field.value, option];
                        } else {
                          changedValue = [option];
                        }
                        field.onChange(changedValue);
                      }}
                      onClear={(selectedValues) => {
                        selectedValues.stopPropagation();
                        field.onChange([]);
                      }}
                      isOpen={selectRequiredForOpen}
                      isDisabled={requiredScopes.length === clientScopes.length}
                      aria-labelledby={"scope"}
                    >
                      {clientScopes.map((option) => (
                        <SelectOption key={option.name} value={option.name} />
                      ))}
                    </Select>
                  )}
                />
              </FormGroup>
            </>
          )}
        </>
      )}
    </FormAccess>
  );
};
