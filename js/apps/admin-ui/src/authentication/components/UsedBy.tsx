import RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import {
  Button,
  Modal,
  ModalVariant,
  Popover,
  Text,
  TextContent,
  TextVariants,
} from "@patternfly/react-core";
import { CheckCircleIcon } from "@patternfly/react-icons";
import { useTranslation } from "react-i18next";

import { fetchUsedBy } from "../../components/role-mapping/resource";
import { KeycloakDataTable } from "../../components/table-toolbar/KeycloakDataTable";
import { useAdminClient } from "../../context/auth/AdminClient";
import useToggle from "../../utils/useToggle";
import { AuthenticationType, REALM_FLOWS } from "../AuthenticationSection";

import "./used-by.css";

type UsedByProps = {
  authType: AuthenticationType;
  realm: RealmRepresentation;
};

const Label = ({ label }: { label: string }) => (
  <>
    <CheckCircleIcon className="keycloak_authentication-section__usedby" />{" "}
    {label}
  </>
);

type UsedByModalProps = {
  id: string;
  onClose: () => void;
  isSpecificClient: boolean;
};

const UsedByModal = ({ id, isSpecificClient, onClose }: UsedByModalProps) => {
  const { t } = useTranslation("authentication");
  const { adminClient } = useAdminClient();

  const loader = async (
    first?: number,
    max?: number,
    search?: string
  ): Promise<{ name: string }[]> => {
    const result = await fetchUsedBy({
      adminClient,
      id,
      type: isSpecificClient ? "clients" : "idp",
      first: first || 0,
      max: max || 10,
      search,
    });
    return result.map((p) => ({ name: p }));
  };

  return (
    <Modal
      header={
        <TextContent>
          <Text component={TextVariants.h1}>{t("flowUsedBy")}</Text>
          <Text>
            {t("flowUsedByDescription", {
              value: isSpecificClient ? t("clients") : t("identiyProviders"),
            })}
          </Text>
        </TextContent>
      }
      variant={ModalVariant.medium}
      isOpen
      onClose={onClose}
      actions={[
        <Button
          data-testid="cancel"
          id="modal-cancel"
          key="cancel"
          onClick={onClose}
        >
          {t("common:close")}
        </Button>,
      ]}
    >
      <KeycloakDataTable
        loader={loader}
        isPaginated
        ariaLabelKey="authentication:usedBy"
        searchPlaceholderKey="common:search"
        columns={[
          {
            name: "name",
          },
        ]}
      />
    </Modal>
  );
};

export const UsedBy = ({ authType: { id, usedBy }, realm }: UsedByProps) => {
  const { t } = useTranslation("authentication");
  const [open, toggle] = useToggle();

  const key = Object.entries(realm).find(
    (e) => e[1] === usedBy?.values[0]
  )?.[0];

  return (
    <>
      {open && (
        <UsedByModal
          id={id!}
          onClose={toggle}
          isSpecificClient={usedBy?.type === "SPECIFIC_CLIENTS"}
        />
      )}
      {(usedBy?.type === "SPECIFIC_PROVIDERS" ||
        usedBy?.type === "SPECIFIC_CLIENTS") &&
        (usedBy.values.length <= 8 ? (
          <Popover
            key={id}
            aria-label={t("usedBy")}
            bodyContent={
              <div key={`usedBy-${id}-${usedBy.values}`}>
                {t(
                  "appliedBy" +
                    (usedBy.type === "SPECIFIC_CLIENTS"
                      ? "Clients"
                      : "Providers")
                )}{" "}
                {usedBy.values.map((used, index) => (
                  <>
                    <strong>{used}</strong>
                    {index < usedBy.values.length - 1 ? ", " : ""}
                  </>
                ))}
              </div>
            }
          >
            <Button
              variant="link"
              className="keycloak__used-by__popover-button"
            >
              <Label label={t(`used.${usedBy.type}`)} />
            </Button>
          </Popover>
        ) : (
          <Button
            variant="link"
            className="keycloak__used-by__popover-button"
            onClick={toggle}
          >
            <Label label={t(`used.${usedBy.type}`)} />
          </Button>
        ))}
      {usedBy?.type === "DEFAULT" && (
        <Label label={t(`flow.${REALM_FLOWS.get(key!)}`)} />
      )}
      {!usedBy?.type && t("used.notInUse")}
    </>
  );
};
