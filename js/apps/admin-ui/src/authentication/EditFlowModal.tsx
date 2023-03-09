import type AuthenticationFlowRepresentation from "@keycloak/keycloak-admin-client/lib/defs/authenticationFlowRepresentation";
import {
  AlertVariant,
  Button,
  ButtonVariant,
  Form,
  Modal,
  ModalVariant,
} from "@patternfly/react-core";
import { useEffect } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { useAlerts } from "../components/alert/Alerts";
import { useAdminClient } from "../context/auth/AdminClient";
import { NameDescription } from "./form/NameDescription";

type EditFlowModalProps = {
  flow: AuthenticationFlowRepresentation;
  toggleDialog: () => void;
};

export const EditFlowModal = ({ flow, toggleDialog }: EditFlowModalProps) => {
  const { t } = useTranslation("authentication");
  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();
  const form = useForm<AuthenticationFlowRepresentation>({ mode: "onChange" });
  const { reset, handleSubmit } = form;

  useEffect(() => reset(flow), [flow]);

  const onSubmit = async (formValues: AuthenticationFlowRepresentation) => {
    try {
      await adminClient.authenticationManagement.updateFlow(
        { flowId: flow.id! },
        { ...flow, ...formValues }
      );
      addAlert(t("updateFlowSuccess"), AlertVariant.success);
    } catch (error) {
      addError("authentication:updateFlowError", error);
    }
    toggleDialog();
  };

  return (
    <Modal
      title={t("editFlow")}
      onClose={toggleDialog}
      variant={ModalVariant.small}
      actions={[
        <Button
          key="confirm"
          data-testid="confirm"
          type="submit"
          form="edit-flow-form"
        >
          {t("edit")}
        </Button>,
        <Button
          key="cancel"
          data-testid="cancel"
          variant={ButtonVariant.link}
          onClick={() => toggleDialog()}
        >
          {t("common:cancel")}
        </Button>,
      ]}
      isOpen
    >
      <FormProvider {...form}>
        <Form
          id="edit-flow-form"
          onSubmit={handleSubmit(onSubmit)}
          isHorizontal
        >
          <NameDescription />
        </Form>
      </FormProvider>
    </Modal>
  );
};
