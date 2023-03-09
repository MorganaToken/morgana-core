import ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import {
  FormGroup,
  Select,
  SelectOption,
  SelectVariant,
} from "@patternfly/react-core";
import { useState } from "react";
import { Controller, UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { FormAccess } from "../../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { KeycloakTextInput } from "../../components/keycloak-text-input/KeycloakTextInput";
import { WizardSectionHeader } from "../../components/wizard-section-header/WizardSectionHeader";
import { useAdminClient, useFetch } from "../../context/auth/AdminClient";
import { useRealm } from "../../context/realm-context/RealmContext";

export type LdapSettingsGeneralProps = {
  form: UseFormReturn<ComponentRepresentation>;
  showSectionHeading?: boolean;
  showSectionDescription?: boolean;
  vendorEdit?: boolean;
};

export const LdapSettingsGeneral = ({
  form,
  showSectionHeading = false,
  showSectionDescription = false,
  vendorEdit = false,
}: LdapSettingsGeneralProps) => {
  const { t } = useTranslation("user-federation");
  const { t: helpText } = useTranslation("user-federation-help");

  const { adminClient } = useAdminClient();
  const { realm } = useRealm();

  useFetch(
    () => adminClient.realms.findOne({ realm }),
    (result) => form.setValue("parentId", result!.id),
    []
  );
  const [isVendorDropdownOpen, setIsVendorDropdownOpen] = useState(false);

  const setVendorDefaultValues = () => {
    switch (form.getValues("config.vendor[0]")) {
      case "ad":
        form.setValue("config.usernameLDAPAttribute[0]", "cn");
        form.setValue("config.rdnLDAPAttribute[0]", "cn");
        form.setValue("config.uuidLDAPAttribute[0]", "objectGUID");
        form.setValue(
          "config.userObjectClasses[0]",
          "person, organizationalPerson, user"
        );
        break;
      case "rhds":
        form.setValue("config.usernameLDAPAttribute[0]", "uid");
        form.setValue("config.rdnLDAPAttribute[0]", "uid");
        form.setValue("config.uuidLDAPAttribute[0]", "nsuniqueid");
        form.setValue(
          "config.userObjectClasses[0]",
          "inetOrgPerson, organizationalPerson"
        );
        break;
      case "tivoli":
        form.setValue("config.usernameLDAPAttribute[0]", "uid");
        form.setValue("config.rdnLDAPAttribute[0]", "uid");
        form.setValue("config.uuidLDAPAttribute[0]", "uniqueidentifier");
        form.setValue(
          "config.userObjectClasses[0]",
          "inetOrgPerson, organizationalPerson"
        );
        break;
      case "edirectory":
        form.setValue("config.usernameLDAPAttribute[0]", "uid");
        form.setValue("config.rdnLDAPAttribute[0]", "uid");
        form.setValue("config.uuidLDAPAttribute[0]", "guid");
        form.setValue(
          "config.userObjectClasses[0]",
          "inetOrgPerson, organizationalPerson"
        );
        break;
      case "other":
        form.setValue("config.usernameLDAPAttribute[0]", "uid");
        form.setValue("config.rdnLDAPAttribute[0]", "uid");
        form.setValue("config.uuidLDAPAttribute[0]", "entryUUID");
        form.setValue(
          "config.userObjectClasses[0]",
          "inetOrgPerson, organizationalPerson"
        );
        break;
      default:
        return "";
    }
  };

  return (
    <>
      {showSectionHeading && (
        <WizardSectionHeader
          title={t("generalOptions")}
          description={helpText("ldapGeneralOptionsSettingsDescription")}
          showDescription={showSectionDescription}
        />
      )}
      <FormAccess role="manage-realm" isHorizontal>
        <FormGroup
          label={t("uiDisplayName")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:uiDisplayNameHelp")}
              fieldLabelId="user-federation:uiDisplayName"
            />
          }
          fieldId="kc-ui-display-name"
          isRequired
          validated={form.formState.errors.name ? "error" : "default"}
          helperTextInvalid={form.formState.errors.name?.message}
        >
          {/* These hidden fields are required so data object written back matches data retrieved */}
          <KeycloakTextInput
            hidden
            id="kc-ui-provider-id"
            defaultValue="ldap"
            {...form.register("providerId")}
          />
          <KeycloakTextInput
            hidden
            id="kc-ui-provider-type"
            defaultValue="org.keycloak.storage.UserStorageProvider"
            {...form.register("providerType")}
          />
          <KeycloakTextInput
            hidden
            id="kc-ui-parentId"
            defaultValue={realm}
            {...form.register("parentId")}
          />
          <KeycloakTextInput
            isRequired
            id="kc-ui-display-name"
            defaultValue="ldap"
            data-testid="ldap-name"
            validated={form.formState.errors.name ? "error" : "default"}
            {...form.register("name", {
              required: {
                value: true,
                message: `${t("validateName")}`,
              },
            })}
          />
        </FormGroup>
        <FormGroup
          label={t("vendor")}
          labelIcon={
            <HelpItem
              helpText={t("user-federation-help:vendorHelp")}
              fieldLabelId="user-federation:vendor"
            />
          }
          fieldId="kc-vendor"
          isRequired
        >
          <Controller
            name="config.vendor[0]"
            defaultValue="ad"
            control={form.control}
            render={({ field }) => (
              <Select
                isDisabled={!!vendorEdit}
                toggleId="kc-vendor"
                required
                onToggle={() => setIsVendorDropdownOpen(!isVendorDropdownOpen)}
                isOpen={isVendorDropdownOpen}
                onSelect={(_, value) => {
                  field.onChange(value as string);
                  setIsVendorDropdownOpen(false);
                  setVendorDefaultValues();
                }}
                selections={field.value}
                variant={SelectVariant.single}
              >
                <SelectOption key={0} value="ad" isPlaceholder>
                  Active Directory
                </SelectOption>
                <SelectOption key={1} value="rhds">
                  Red Hat Directory Server
                </SelectOption>
                <SelectOption key={2} value="tivoli">
                  Tivoli
                </SelectOption>
                <SelectOption key={3} value="edirectory">
                  Novell eDirectory
                </SelectOption>
                <SelectOption key={4} value="other">
                  Other
                </SelectOption>
              </Select>
            )}
          ></Controller>
        </FormGroup>
      </FormAccess>
    </>
  );
};
