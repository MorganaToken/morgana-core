// @ts-nocheck
import {
  FormGroup,
  Select,
  SelectOption,
  SelectVariant,
  Switch,
} from "@patternfly/react-core";
import { useState } from "react";
import { Controller, UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { FormAccess } from "../../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { KeycloakTextInput } from "../../components/keycloak-text-input/KeycloakTextInput";
import { WizardSectionHeader } from "../../components/wizard-section-header/WizardSectionHeader";

export type LdapSettingsSearchingProps = {
  form: UseFormReturn;
  showSectionHeading?: boolean;
  showSectionDescription?: boolean;
};

export const LdapSettingsSearching = ({
  form,
  showSectionHeading = false,
  showSectionDescription = false,
}: LdapSettingsSearchingProps) => {
  const { t } = useTranslation("user-federation");
  const { t: helpText } = useTranslation("user-federation-help");

  const [isSearchScopeDropdownOpen, setIsSearchScopeDropdownOpen] =
    useState(false);
  const [isEditModeDropdownOpen, setIsEditModeDropdownOpen] = useState(false);

  return (
    <>
      {showSectionHeading && (
        <WizardSectionHeader
          title={t("ldapSearchingAndUpdatingSettings")}
          description={helpText("ldapSearchingAndUpdatingSettingsDescription")}
          showDescription={showSectionDescription}
        />
      )}

      <FormAccess role="manage-realm" isHorizontal>
        <FormGroup
          label={t("editMode")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:editModeLdapHelp")}
              fieldLabelId="user-federation:editMode"
            />
          }
          fieldId="kc-edit-mode"
          isRequired
          validated={
            form.formState.errors.config?.editMode?.[0] ? "error" : "default"
          }
          helperTextInvalid={
            form.formState.errors.config?.editMode?.[0].message
          }
        >
          <Controller
            name="config.editMode[0]"
            defaultValue=""
            control={form.control}
            rules={{
              required: { value: true, message: t("validateEditMode") },
            }}
            render={({ field }) => (
              <Select
                toggleId="kc-edit-mode"
                required
                onToggle={() =>
                  setIsEditModeDropdownOpen(!isEditModeDropdownOpen)
                }
                isOpen={isEditModeDropdownOpen}
                onSelect={(_, value) => {
                  field.onChange(value.toString());
                  setIsEditModeDropdownOpen(false);
                }}
                selections={field.value}
                variant={SelectVariant.single}
                validated={
                  form.formState.errors.config?.editMode?.[0]
                    ? "error"
                    : "default"
                }
              >
                <SelectOption value="" isPlaceholder />
                <SelectOption value="READ_ONLY" />
                <SelectOption value="WRITABLE" />
                <SelectOption value="UNSYNCED" />
              </Select>
            )}
          />
        </FormGroup>
        <FormGroup
          label={t("usersDN")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:usersDNHelp")}
              fieldLabelId="user-federation:usersDn"
            />
          }
          fieldId="kc-ui-users-dn"
          isRequired
          validated={
            form.formState.errors.config?.usersDn?.[0] ? "error" : "default"
          }
          helperTextInvalid={form.formState.errors.config?.usersDn?.[0].message}
        >
          <KeycloakTextInput
            isRequired
            defaultValue=""
            id="kc-ui-users-dn"
            data-testid="ldap-users-dn"
            validated={
              form.formState.errors.config?.usersDn?.[0] ? "error" : "default"
            }
            {...form.register("config.usersDn.0", {
              required: {
                value: true,
                message: t("validateUsersDn").toString(),
              },
            })}
          />
        </FormGroup>
        <FormGroup
          label={t("usernameLdapAttribute")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:usernameLdapAttributeHelp")}
              fieldLabelId="user-federation:usernameLdapAttribute"
            />
          }
          fieldId="kc-username-ldap-attribute"
          isRequired
          validated={
            form.formState.errors.config?.usernameLDAPAttribute?.[0]
              ? "error"
              : "default"
          }
          helperTextInvalid={
            form.formState.errors.config?.usernameLDAPAttribute?.[0].message
          }
        >
          <KeycloakTextInput
            isRequired
            defaultValue="cn"
            id="kc-username-ldap-attribute"
            data-testid="ldap-username-attribute"
            validated={
              form.formState.errors.config?.usernameLDAPAttribute?.[0]
                ? "error"
                : "default"
            }
            {...form.register("config.usernameLDAPAttribute.0", {
              required: {
                value: true,
                message: `${t("validateUsernameLDAPAttribute")}`,
              },
            })}
          />
        </FormGroup>
        <FormGroup
          label={t("rdnLdapAttribute")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:rdnLdapAttributeHelp")}
              fieldLabelId="user-federation:rdnLdapAttribute"
            />
          }
          fieldId="kc-rdn-ldap-attribute"
          isRequired
          validated={
            form.formState.errors.config?.rdnLDAPAttribute?.[0]
              ? "error"
              : "default"
          }
          helperTextInvalid={
            form.formState.errors.config?.rdnLDAPAttribute?.[0].message
          }
        >
          <KeycloakTextInput
            isRequired
            defaultValue="cn"
            id="kc-rdn-ldap-attribute"
            data-testid="ldap-rdn-attribute"
            validated={
              form.formState.errors.config?.rdnLDAPAttribute?.[0]
                ? "error"
                : "default"
            }
            {...form.register("config.rdnLDAPAttribute.0", {
              required: {
                value: true,
                message: `${t("validateRdnLdapAttribute")}`,
              },
            })}
          />
        </FormGroup>
        <FormGroup
          label={t("uuidLdapAttribute")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:uuidLdapAttributeHelp")}
              fieldLabelId="user-federation:uuidLdapAttribute"
            />
          }
          fieldId="kc-uuid-ldap-attribute"
          isRequired
          validated={
            form.formState.errors.config?.uuidLDAPAttribute?.[0]
              ? "error"
              : "default"
          }
          helperTextInvalid={
            form.formState.errors.config?.uuidLDAPAttribute?.[0].message
          }
        >
          <KeycloakTextInput
            isRequired
            defaultValue="objectGUID"
            id="kc-uuid-ldap-attribute"
            data-testid="ldap-uuid-attribute"
            validated={
              form.formState.errors.config?.uuidLDAPAttribute?.[0]
                ? "error"
                : "default"
            }
            {...form.register("config.uuidLDAPAttribute.0", {
              required: {
                value: true,
                message: `${t("validateUuidLDAPAttribute")}`,
              },
            })}
          />
        </FormGroup>
        <FormGroup
          label={t("userObjectClasses")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:userObjectClassesHelp")}
              fieldLabelId="user-federation:userObjectClasses"
            />
          }
          fieldId="kc-user-object-classes"
          isRequired
          validated={
            form.formState.errors.config?.userObjectClasses?.[0]
              ? "error"
              : "default"
          }
          helperTextInvalid={
            form.formState.errors.config?.userObjectClasses?.[0].message
          }
        >
          <KeycloakTextInput
            isRequired
            defaultValue="person, organizationalPerson, user"
            id="kc-user-object-classes"
            data-testid="ldap-user-object-classes"
            validated={
              form.formState.errors.config?.userObjectClasses?.[0]
                ? "error"
                : "default"
            }
            {...form.register("config.userObjectClasses.0", {
              required: {
                value: true,
                message: t("validateUserObjectClasses").toString(),
              },
            })}
          />
        </FormGroup>
        <FormGroup
          label={t("userLdapFilter")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:userLdapFilterHelp")}
              fieldLabelId="user-federation:userLdapFilter"
            />
          }
          fieldId="kc-user-ldap-filter"
          validated={
            form.formState.errors.config?.customUserSearchFilter?.[0]
              ? "error"
              : "default"
          }
          helperTextInvalid={
            form.formState.errors.config?.customUserSearchFilter?.[0].message
          }
        >
          <KeycloakTextInput
            id="kc-user-ldap-filter"
            data-testid="user-ldap-filter"
            validated={
              form.formState.errors.config?.customUserSearchFilter?.[0]
                ? "error"
                : "default"
            }
            {...form.register("config.customUserSearchFilter.0", {
              pattern: {
                value: /(\(.*\))$/,
                message: t("validateCustomUserSearchFilter").toString(),
              },
            })}
          />
        </FormGroup>

        <FormGroup
          label={t("searchScope")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:searchScopeHelp")}
              fieldLabelId="user-federation:searchScope"
            />
          }
          fieldId="kc-search-scope"
        >
          <Controller
            name="config.searchScope[0]"
            defaultValue=""
            control={form.control}
            render={({ field }) => (
              <Select
                toggleId="kc-search-scope"
                required
                onToggle={() =>
                  setIsSearchScopeDropdownOpen(!isSearchScopeDropdownOpen)
                }
                isOpen={isSearchScopeDropdownOpen}
                onSelect={(_, value) => {
                  field.onChange(value as string);
                  setIsSearchScopeDropdownOpen(false);
                }}
                selections={field.value}
                variant={SelectVariant.single}
              >
                <SelectOption key={0} value="1" isPlaceholder>
                  {t("oneLevel")}
                </SelectOption>
                <SelectOption key={1} value="2">
                  {t("subtree")}
                </SelectOption>
              </Select>
            )}
          ></Controller>
        </FormGroup>
        <FormGroup
          label={t("readTimeout")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:readTimeoutHelp")}
              fieldLabelId="user-federation:readTimeout"
            />
          }
          fieldId="kc-read-timeout"
        >
          <KeycloakTextInput
            type="number"
            min={0}
            id="kc-read-timeout"
            data-testid="ldap-read-timeout"
            {...form.register("config.readTimeout.0")}
          />
        </FormGroup>
        <FormGroup
          label={t("pagination")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:paginationHelp")}
              fieldLabelId="user-federation:pagination"
            />
          }
          fieldId="kc-ui-pagination"
          hasNoPaddingTop
        >
          <Controller
            name="config.pagination"
            defaultValue={["false"]}
            control={form.control}
            render={({ field }) => (
              <Switch
                id="kc-ui-pagination"
                data-testid="ui-pagination"
                isDisabled={false}
                onChange={(value) => field.onChange([`${value}`])}
                isChecked={field.value[0] === "true"}
                label={t("common:on")}
                labelOff={t("common:off")}
                aria-label={t("pagination")}
              />
            )}
          ></Controller>
        </FormGroup>
      </FormAccess>
    </>
  );
};
