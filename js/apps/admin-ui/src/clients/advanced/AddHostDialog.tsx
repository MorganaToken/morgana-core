import {
  AlertVariant,
  Button,
  ButtonVariant,
  Form,
  FormGroup,
  Modal,
} from "@patternfly/react-core";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { useAlerts } from "../../components/alert/Alerts";
import { KeycloakTextInput } from "../../components/keycloak-text-input/KeycloakTextInput";
import { useAdminClient } from "../../context/auth/AdminClient";

type FormFields = {
  node: string;
};

type AddHostDialogProps = {
  clientId: string;
  isOpen: boolean;
  onAdded: (host: string) => void;
  onClose: () => void;
};

export const AddHostDialog = ({
  clientId: id,
  isOpen,
  onAdded,
  onClose,
}: AddHostDialogProps) => {
  const { t } = useTranslation("clients");
  const {
    register,
    handleSubmit,
    formState: { isDirty, isValid },
  } = useForm<FormFields>();
  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();

  async function onSubmit({ node }: FormFields) {
    try {
      await adminClient.clients.addClusterNode({
        id,
        node,
      });
      onAdded(node);
      addAlert(t("addedNodeSuccess"), AlertVariant.success);
    } catch (error) {
      addError("clients:addedNodeFail", error);
    }

    onClose();
  }

  return (
    <Modal
      title={t("addNode")}
      isOpen={isOpen}
      onClose={onClose}
      variant="small"
      actions={[
        <Button
          key="confirm"
          id="add-node-confirm"
          type="submit"
          form="add-host-form"
          isDisabled={!isDirty || !isValid}
        >
          {t("common:save")}
        </Button>,
        <Button
          key="cancel"
          id="add-node-cancel"
          variant={ButtonVariant.link}
          onClick={onClose}
        >
          {t("common:cancel")}
        </Button>,
      ]}
    >
      <Form id="add-host-form" onSubmit={handleSubmit(onSubmit)} isHorizontal>
        <FormGroup label={t("nodeHost")} fieldId="nodeHost" isRequired>
          <KeycloakTextInput
            id="nodeHost"
            {...register("node", { required: true })}
            isRequired
          />
        </FormGroup>
      </Form>
    </Modal>
  );
};
