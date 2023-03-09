import type ScopeRepresentation from "@keycloak/keycloak-admin-client/lib/defs/scopeRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  ButtonVariant,
  DropdownItem,
  FormGroup,
  PageSection,
  ValidatedOptions,
} from "@patternfly/react-core";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";

import { useAlerts } from "../../components/alert/Alerts";
import { FormAccess } from "../../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { KeycloakTextInput } from "../../components/keycloak-text-input/KeycloakTextInput";
import { ViewHeader } from "../../components/view-header/ViewHeader";
import { useAdminClient, useFetch } from "../../context/auth/AdminClient";
import { useParams } from "../../utils/useParams";
import useToggle from "../../utils/useToggle";
import { toAuthorizationTab } from "../routes/AuthenticationTab";
import type { ScopeDetailsParams } from "../routes/Scope";
import { DeleteScopeDialog } from "./DeleteScopeDialog";

type FormFields = Omit<ScopeRepresentation, "resources">;

export default function ScopeDetails() {
  const { t } = useTranslation("clients");
  const { id, scopeId, realm } = useParams<ScopeDetailsParams>();
  const navigate = useNavigate();

  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();

  const [deleteDialog, toggleDeleteDialog] = useToggle();
  const [scope, setScope] = useState<ScopeRepresentation>();
  const {
    register,
    reset,
    handleSubmit,
    formState: { errors },
  } = useForm<FormFields>({
    mode: "onChange",
  });

  useFetch(
    async () => {
      if (scopeId) {
        const scope = await adminClient.clients.getAuthorizationScope({
          id,
          scopeId,
        });
        if (!scope) {
          throw new Error(t("common:notFound"));
        }
        return scope;
      }
    },
    (scope) => {
      setScope(scope);
      reset({ ...scope });
    },
    []
  );

  const onSubmit = async (scope: ScopeRepresentation) => {
    try {
      if (scopeId) {
        await adminClient.clients.updateAuthorizationScope(
          { id, scopeId },
          scope
        );
        setScope(scope);
      } else {
        await adminClient.clients.createAuthorizationScope(
          { id },
          {
            name: scope.name!,
            displayName: scope.displayName,
            iconUri: scope.iconUri,
          }
        );
        navigate(toAuthorizationTab({ realm, clientId: id, tab: "scopes" }));
      }
      addAlert(
        t((scopeId ? "update" : "create") + "ScopeSuccess"),
        AlertVariant.success
      );
    } catch (error) {
      addError("clients:scopeSaveError", error);
    }
  };

  return (
    <>
      <DeleteScopeDialog
        clientId={id}
        open={deleteDialog}
        toggleDialog={toggleDeleteDialog}
        selectedScope={scope}
        refresh={() =>
          navigate(toAuthorizationTab({ realm, clientId: id, tab: "scopes" }))
        }
      />
      <ViewHeader
        titleKey={
          scopeId ? scope?.name! : t("clients:createAuthorizationScope")
        }
        dropdownItems={
          scopeId
            ? [
                <DropdownItem
                  key="delete"
                  data-testid="delete-resource"
                  onClick={() => toggleDeleteDialog()}
                >
                  {t("common:delete")}
                </DropdownItem>,
              ]
            : undefined
        }
      />
      <PageSection variant="light">
        <FormAccess
          isHorizontal
          role="view-clients"
          onSubmit={handleSubmit(onSubmit)}
        >
          <FormGroup
            label={t("common:name")}
            fieldId="name"
            labelIcon={
              <HelpItem
                helpText={t("clients-help:scopeName")}
                fieldLabelId="name"
              />
            }
            helperTextInvalid={t("common:required")}
            validated={
              errors.name ? ValidatedOptions.error : ValidatedOptions.default
            }
            isRequired
          >
            <KeycloakTextInput
              id="name"
              validated={
                errors.name ? ValidatedOptions.error : ValidatedOptions.default
              }
              isRequired
              {...register("name", { required: true })}
            />
          </FormGroup>
          <FormGroup
            label={t("displayName")}
            fieldId="displayName"
            labelIcon={
              <HelpItem
                helpText={t("clients-help:scopeDisplayName")}
                fieldLabelId="displayName"
              />
            }
          >
            <KeycloakTextInput id="displayName" {...register("displayName")} />
          </FormGroup>
          <FormGroup
            label={t("iconUri")}
            fieldId="iconUri"
            labelIcon={
              <HelpItem
                helpText={t("clients-help:iconUri")}
                fieldLabelId="clients:iconUri"
              />
            }
          >
            <KeycloakTextInput id="iconUri" {...register("iconUri")} />
          </FormGroup>
          <ActionGroup>
            <div className="pf-u-mt-md">
              <Button
                variant={ButtonVariant.primary}
                type="submit"
                data-testid="save"
              >
                {t("common:save")}
              </Button>

              {!scope ? (
                <Button
                  variant="link"
                  data-testid="cancel"
                  component={(props) => (
                    <Link
                      {...props}
                      to={toAuthorizationTab({
                        realm,
                        clientId: id,
                        tab: "scopes",
                      })}
                    ></Link>
                  )}
                >
                  {t("common:cancel")}
                </Button>
              ) : (
                <Button
                  variant="link"
                  data-testid="revert"
                  onClick={() => reset({ ...scope })}
                >
                  {t("common:revert")}
                </Button>
              )}
            </div>
          </ActionGroup>
        </FormAccess>
      </PageSection>
    </>
  );
}
