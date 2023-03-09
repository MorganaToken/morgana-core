import {
  AlertVariant,
  Button,
  ButtonVariant,
  Label,
  PageSection,
  Tab,
  TabTitleText,
  ToolbarItem,
} from "@patternfly/react-core";
import { sortBy } from "lodash-es";
import { useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import type AuthenticationFlowRepresentation from "@keycloak/keycloak-admin-client/lib/defs/authenticationFlowRepresentation";
import RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import { useAlerts } from "../components/alert/Alerts";
import { useConfirmDialog } from "../components/confirm-dialog/ConfirmDialog";
import { ListEmptyState } from "../components/list-empty-state/ListEmptyState";
import {
  RoutableTabs,
  useRoutableTab,
} from "../components/routable-tabs/RoutableTabs";
import { KeycloakDataTable } from "../components/table-toolbar/KeycloakDataTable";
import { ViewHeader } from "../components/view-header/ViewHeader";
import { useAdminClient, useFetch } from "../context/auth/AdminClient";
import { useRealm } from "../context/realm-context/RealmContext";
import helpUrls from "../help-urls";
import { addTrailingSlash } from "../util";
import { getAuthorizationHeaders } from "../utils/getAuthorizationHeaders";
import useLocaleSort, { mapByKey } from "../utils/useLocaleSort";
import useToggle from "../utils/useToggle";
import { BindFlowDialog } from "./BindFlowDialog";
import { UsedBy } from "./components/UsedBy";
import { DuplicateFlowModal } from "./DuplicateFlowModal";
import { Policies } from "./policies/Policies";
import { RequiredActions } from "./RequiredActions";
import { AuthenticationTab, toAuthentication } from "./routes/Authentication";
import { toCreateFlow } from "./routes/CreateFlow";
import { toFlow } from "./routes/Flow";

import "./authentication-section.css";
import { KeycloakSpinner } from "../components/keycloak-spinner/KeycloakSpinner";

type UsedBy = "SPECIFIC_CLIENTS" | "SPECIFIC_PROVIDERS" | "DEFAULT";

export type AuthenticationType = AuthenticationFlowRepresentation & {
  usedBy?: { type?: UsedBy; values: string[] };
  realm: RealmRepresentation;
};

export const REALM_FLOWS = new Map<string, string>([
  ["browserFlow", "browser"],
  ["registrationFlow", "registration"],
  ["directGrantFlow", "direct grant"],
  ["resetCredentialsFlow", "reset credentials"],
  ["clientAuthenticationFlow", "clients"],
  ["dockerAuthenticationFlow", "docker auth"],
]);

const AliasRenderer = ({ id, alias, usedBy, builtIn }: AuthenticationType) => {
  const { t } = useTranslation("authentication");
  const { realm } = useRealm();

  return (
    <>
      <Link
        to={toFlow({
          realm,
          id: id!,
          usedBy: usedBy?.type || "notInUse",
          builtIn: builtIn ? "builtIn" : undefined,
        })}
        key={`link-${id}`}
      >
        {alias}
      </Link>{" "}
      {builtIn && <Label key={`label-${id}`}>{t("buildIn")}</Label>}
    </>
  );
};

export default function AuthenticationSection() {
  const { t } = useTranslation("authentication");
  const { adminClient } = useAdminClient();
  const { realm: realmName } = useRealm();
  const [key, setKey] = useState(0);
  const refresh = () => {
    setRealm(undefined);
    setKey(key + 1);
  };
  const { addAlert, addError } = useAlerts();
  const localeSort = useLocaleSort();
  const [selectedFlow, setSelectedFlow] = useState<AuthenticationType>();
  const [open, toggleOpen] = useToggle();
  const [bindFlowOpen, toggleBindFlow] = useToggle();

  const [realm, setRealm] = useState<RealmRepresentation>();

  useFetch(() => adminClient.realms.findOne({ realm: realmName }), setRealm, [
    key,
  ]);

  const loader = async () => {
    const flowsRequest = await fetch(
      `${addTrailingSlash(
        adminClient.baseUrl
      )}admin/realms/${realmName}/ui-ext/authentication-management/flows`,
      {
        method: "GET",
        headers: getAuthorizationHeaders(await adminClient.getAccessToken()),
      }
    );
    const flows = await flowsRequest.json();

    if (!flows) {
      return [];
    }

    return sortBy(
      localeSort<AuthenticationType>(flows, mapByKey("alias")),
      (flow) => flow.usedBy?.type
    );
  };

  const useTab = (tab: AuthenticationTab) =>
    useRoutableTab(toAuthentication({ realm: realmName, tab }));

  const flowsTab = useTab("flows");
  const requiredActionsTab = useTab("required-actions");
  const policiesTab = useTab("policies");

  const [toggleDeleteDialog, DeleteConfirm] = useConfirmDialog({
    titleKey: "authentication:deleteConfirmFlow",
    children: (
      <Trans i18nKey="authentication:deleteConfirmFlowMessage">
        {" "}
        <strong>{{ flow: selectedFlow ? selectedFlow.alias : "" }}</strong>.
      </Trans>
    ),
    continueButtonLabel: "common:delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.authenticationManagement.deleteFlow({
          flowId: selectedFlow!.id!,
        });
        refresh();
        addAlert(t("deleteFlowSuccess"), AlertVariant.success);
      } catch (error) {
        addError("authentication:deleteFlowError", error);
      }
    },
  });

  if (!realm) return <KeycloakSpinner />;

  return (
    <>
      <DeleteConfirm />
      {open && (
        <DuplicateFlowModal
          name={selectedFlow ? selectedFlow.alias! : ""}
          description={selectedFlow?.description!}
          toggleDialog={toggleOpen}
          onComplete={() => {
            refresh();
            toggleOpen();
          }}
        />
      )}
      {bindFlowOpen && (
        <BindFlowDialog
          onClose={() => {
            toggleBindFlow();
            refresh();
          }}
          flowAlias={selectedFlow?.alias!}
        />
      )}
      <ViewHeader
        titleKey="authentication:title"
        subKey="authentication:authenticationExplain"
        helpUrl={helpUrls.authenticationUrl}
        divider={false}
      />
      <PageSection variant="light" className="pf-u-p-0">
        <RoutableTabs
          isBox
          defaultLocation={toAuthentication({ realm: realmName, tab: "flows" })}
        >
          <Tab
            data-testid="flows"
            title={<TabTitleText>{t("flows")}</TabTitleText>}
            {...flowsTab}
          >
            <KeycloakDataTable
              key={key}
              loader={loader}
              ariaLabelKey="authentication:title"
              searchPlaceholderKey="authentication:searchForFlow"
              toolbarItem={
                <ToolbarItem>
                  <Button
                    component={(props) => (
                      <Link
                        {...props}
                        to={toCreateFlow({ realm: realmName })}
                      />
                    )}
                  >
                    {t("createFlow")}
                  </Button>
                </ToolbarItem>
              }
              actionResolver={({ data }) => [
                {
                  title: t("duplicate"),
                  onClick: () => {
                    toggleOpen();
                    setSelectedFlow(data);
                  },
                },
                ...(data.usedBy?.type !== "DEFAULT"
                  ? [
                      {
                        title: t("bindFlow"),
                        onClick: () => {
                          toggleBindFlow();
                          setSelectedFlow(data);
                        },
                      },
                    ]
                  : []),
                ...(!data.builtIn && !data.usedBy
                  ? [
                      {
                        title: t("common:delete"),
                        onClick: () => {
                          setSelectedFlow(data);
                          toggleDeleteDialog();
                        },
                      },
                    ]
                  : []),
              ]}
              columns={[
                {
                  name: "alias",
                  displayKey: "authentication:flowName",
                  cellRenderer: (row) => <AliasRenderer {...row} />,
                },
                {
                  name: "usedBy",
                  displayKey: "authentication:usedBy",
                  cellRenderer: (row) => (
                    <UsedBy authType={row} realm={realm} />
                  ),
                },
                {
                  name: "description",
                  displayKey: "common:description",
                },
              ]}
              emptyState={
                <ListEmptyState
                  message={t("emptyEvents")}
                  instructions={t("emptyEventsInstructions")}
                />
              }
            />
          </Tab>
          <Tab
            data-testid="requiredActions"
            title={<TabTitleText>{t("requiredActions")}</TabTitleText>}
            {...requiredActionsTab}
          >
            <RequiredActions />
          </Tab>
          <Tab
            data-testid="policies"
            title={<TabTitleText>{t("policies")}</TabTitleText>}
            {...policiesTab}
          >
            <Policies />
          </Tab>
        </RoutableTabs>
      </PageSection>
    </>
  );
}
