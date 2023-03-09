import type { ConfigPropertyRepresentation } from "@keycloak/keycloak-admin-client/lib/defs/authenticatorConfigInfoRepresentation";
import type ClientPolicyConditionRepresentation from "@keycloak/keycloak-admin-client/lib/defs/clientPolicyConditionRepresentation";
import type ClientPolicyRepresentation from "@keycloak/keycloak-admin-client/lib/defs/clientPolicyRepresentation";
import type ComponentTypeRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentTypeRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  FormGroup,
  PageSection,
  Select,
  SelectOption,
  SelectVariant,
} from "@patternfly/react-core";
import { camelCase } from "lodash-es";
import { useState } from "react";
import { Controller, FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

import { useAlerts } from "../components/alert/Alerts";
import { DynamicComponents } from "../components/dynamic/DynamicComponents";
import { FormAccess } from "../components/form-access/FormAccess";
import { HelpItem } from "ui-shared";
import { FormPanel } from "../components/scroll-form/FormPanel";
import { useAdminClient, useFetch } from "../context/auth/AdminClient";
import { useRealm } from "../context/realm-context/RealmContext";
import { useServerInfo } from "../context/server-info/ServerInfoProvider";
import { toEditClientPolicy } from "./routes/EditClientPolicy";
import type { EditClientPolicyConditionParams } from "./routes/EditCondition";

export type ItemType = { value: string };

type ConfigProperty = ConfigPropertyRepresentation & {
  conditions: any;
  config: any;
};

export default function NewClientPolicyCondition() {
  const { t } = useTranslation("realm-settings");
  const { addAlert, addError } = useAlerts();
  const navigate = useNavigate();
  const { realm } = useRealm();

  const [openConditionType, setOpenConditionType] = useState(false);
  const [policies, setPolicies] = useState<ClientPolicyRepresentation[]>([]);

  const [condition, setCondition] = useState<
    ClientPolicyConditionRepresentation[]
  >([]);
  const [conditionData, setConditionData] =
    useState<ClientPolicyConditionRepresentation>();
  const [conditionType, setConditionType] = useState("");
  const [conditionProperties, setConditionProperties] = useState<
    ConfigPropertyRepresentation[]
  >([]);

  const { policyName, conditionName } =
    useParams<EditClientPolicyConditionParams>();

  const serverInfo = useServerInfo();
  const form = useForm<ConfigProperty>();

  const conditionTypes =
    serverInfo.componentTypes?.[
      "org.keycloak.services.clientpolicy.condition.ClientPolicyConditionProvider"
    ];

  const { adminClient } = useAdminClient();

  const setupForm = (condition: ClientPolicyConditionRepresentation) => {
    form.reset({ config: condition.configuration || {} });
  };

  useFetch(
    () => adminClient.clientPolicies.listPolicies(),

    (policies) => {
      setPolicies(policies.policies ?? []);

      if (conditionName) {
        const currentPolicy = policies.policies?.find(
          (item) => item.name === policyName
        );

        const typeAndConfigData = currentPolicy?.conditions?.find(
          (item) => item.condition === conditionName
        );

        const currentCondition = conditionTypes?.find(
          (condition) => condition.id === conditionName
        );

        setConditionData(typeAndConfigData!);
        setConditionProperties(currentCondition?.properties!);
        setupForm(typeAndConfigData!);
      }
    },
    []
  );

  const save = async (configPolicy: ConfigProperty) => {
    const configValues = configPolicy.config;

    const writeConfig = () => {
      return conditionProperties.reduce((r: any, p) => {
        r[p.name!] = configValues[p.name!];
        return r;
      }, {});
    };

    const updatedPolicies = policies.map((policy) => {
      if (policy.name !== policyName) {
        return policy;
      }

      let conditions = policy.conditions ?? [];

      if (conditionName) {
        const createdCondition = {
          condition: conditionData?.condition,
          configuration: writeConfig(),
        };

        const index = conditions.findIndex(
          (condition) => conditionName === condition.condition
        );

        if (index === -1) {
          return;
        }

        const newConditions = [
          ...conditions.slice(0, index),
          createdCondition,
          ...conditions.slice(index + 1),
        ];

        return {
          ...policy,
          conditions: newConditions,
        };
      }

      conditions = conditions.concat({
        condition: condition[0].condition,
        configuration: writeConfig(),
      });

      return {
        ...policy,
        conditions,
      };
    }) as ClientPolicyRepresentation[];

    try {
      await adminClient.clientPolicies.updatePolicy({
        policies: updatedPolicies,
      });
      setPolicies(updatedPolicies);
      navigate(toEditClientPolicy({ realm, policyName: policyName! }));
      addAlert(
        conditionName
          ? t("realm-settings:updateClientConditionSuccess")
          : t("realm-settings:createClientConditionSuccess"),
        AlertVariant.success
      );
    } catch (error) {
      addError("realm-settings:createClientConditionError", error);
    }
  };

  return (
    <PageSection variant="light">
      <FormPanel
        className="kc-login-screen"
        title={conditionName ? t("editCondition") : t("addCondition")}
      >
        <FormAccess
          isHorizontal
          role="manage-realm"
          className="pf-u-mt-lg"
          onSubmit={form.handleSubmit(save)}
        >
          <FormGroup
            label={t("conditionType")}
            fieldId="conditionType"
            labelIcon={
              <HelpItem
                helpText={
                  conditionType
                    ? `realm-settings-help:${camelCase(
                        conditionType.replace(/-/g, " ")
                      )}`
                    : "realm-settings-help:conditions"
                }
                fieldLabelId="realm-settings:conditionType"
              />
            }
          >
            <Controller
              name="conditions"
              defaultValue={"any-client"}
              control={form.control}
              render={({ field }) => (
                <Select
                  placeholderText={t("selectACondition")}
                  className="kc-conditionType-select"
                  data-testid="conditionType-select"
                  toggleId="provider"
                  isDisabled={!!conditionName}
                  onToggle={(toggle) => setOpenConditionType(toggle)}
                  onSelect={(_, value) => {
                    field.onChange(value);
                    setConditionProperties(
                      (value as ComponentTypeRepresentation).properties
                    );
                    setConditionType((value as ComponentTypeRepresentation).id);
                    setCondition([
                      {
                        condition: (value as ComponentTypeRepresentation).id,
                      },
                    ]);
                    setOpenConditionType(false);
                  }}
                  selections={conditionName ? conditionName : conditionType}
                  variant={SelectVariant.single}
                  aria-label={t("conditionType")}
                  isOpen={openConditionType}
                >
                  {conditionTypes?.map((condition) => (
                    <SelectOption
                      selected={condition.id === field.value}
                      description={t(
                        `realm-settings-help:${camelCase(
                          condition.id.replace(/-/g, " ")
                        )}`
                      )}
                      key={condition.id}
                      value={condition}
                    >
                      {condition.id}
                    </SelectOption>
                  ))}
                </Select>
              )}
            />
          </FormGroup>

          <FormProvider {...form}>
            <DynamicComponents properties={conditionProperties} />
          </FormProvider>
          <ActionGroup>
            <Button
              variant="primary"
              type="submit"
              data-testid="addCondition-saveBtn"
              isDisabled={conditionType === "" && !conditionName}
            >
              {conditionName ? t("common:save") : t("common:add")}
            </Button>
            <Button
              variant="link"
              data-testid="addCondition-cancelBtn"
              onClick={() =>
                navigate(toEditClientPolicy({ realm, policyName: policyName! }))
              }
            >
              {t("common:cancel")}
            </Button>
          </ActionGroup>
        </FormAccess>
      </FormPanel>
    </PageSection>
  );
}
