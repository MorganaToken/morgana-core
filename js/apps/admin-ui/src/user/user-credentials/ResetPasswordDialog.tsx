import type UserRepresentation from "@keycloak/keycloak-admin-client/lib/defs/userRepresentation";
import {
  AlertVariant,
  ButtonVariant,
  Form,
  FormGroup,
  Switch,
  ValidatedOptions,
} from "@patternfly/react-core";
import { Controller, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { useAlerts } from "../../components/alert/Alerts";
import {
  ConfirmDialogModal,
  useConfirmDialog,
} from "../../components/confirm-dialog/ConfirmDialog";
import { HelpItem } from "ui-shared";
import { PasswordInput } from "../../components/password-input/PasswordInput";
import { useAdminClient } from "../../context/auth/AdminClient";
import useToggle from "../../utils/useToggle";

type ResetPasswordDialogProps = {
  user: UserRepresentation;
  isResetPassword: boolean;
  refresh: () => void;
  onClose: () => void;
};

export type CredentialsForm = {
  password: string;
  passwordConfirmation: string;
  temporaryPassword: boolean;
};

const credFormDefaultValues: CredentialsForm = {
  password: "",
  passwordConfirmation: "",
  temporaryPassword: true,
};

export const ResetPasswordDialog = ({
  user,
  isResetPassword,
  refresh,
  onClose,
}: ResetPasswordDialogProps) => {
  const { t } = useTranslation("users");
  const {
    register,
    control,
    formState: { isValid, errors },
    watch,
    handleSubmit,
  } = useForm<CredentialsForm>({
    defaultValues: credFormDefaultValues,
    mode: "onChange",
  });

  const [confirm, toggle] = useToggle(true);
  const password = watch("password", "");

  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();

  const [toggleConfirmSaveModal, ConfirmSaveModal] = useConfirmDialog({
    titleKey: isResetPassword
      ? "users:resetPasswordConfirm"
      : "users:setPasswordConfirm",
    messageKey: isResetPassword
      ? t("resetPasswordConfirmText", { username: user.username })
      : t("setPasswordConfirmText", { username: user.username }),
    continueButtonLabel: isResetPassword
      ? "users:resetPassword"
      : "users:savePassword",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: () => handleSubmit(saveUserPassword)(),
  });

  const saveUserPassword = async ({
    password,
    temporaryPassword,
  }: CredentialsForm) => {
    try {
      await adminClient.users.resetPassword({
        id: user.id!,
        credential: {
          temporary: temporaryPassword,
          type: "password",
          value: password,
        },
      });
      const credentials = await adminClient.users.getCredentials({
        id: user.id!,
      });
      const credentialLabel = credentials.find((c) => c.type === "password");
      if (credentialLabel) {
        await adminClient.users.updateCredentialLabel(
          {
            id: user.id!,
            credentialId: credentialLabel.id!,
          },
          t("defaultPasswordLabel")
        );
      }
      addAlert(
        isResetPassword
          ? t("resetCredentialsSuccess")
          : t("savePasswordSuccess"),
        AlertVariant.success
      );
      refresh();
    } catch (error) {
      addError(
        isResetPassword
          ? "users:resetPasswordError"
          : "users:savePasswordError",
        error
      );
    }

    onClose();
  };

  return (
    <>
      <ConfirmSaveModal />
      <ConfirmDialogModal
        titleKey={
          isResetPassword
            ? t("resetPasswordFor", { username: user.username })
            : t("setPasswordFor", { username: user.username })
        }
        open={confirm}
        onCancel={onClose}
        toggleDialog={toggle}
        onConfirm={toggleConfirmSaveModal}
        confirmButtonDisabled={!isValid}
        continueButtonLabel="common:save"
      >
        <Form
          id="userCredentials-form"
          isHorizontal
          className="keycloak__user-credentials__reset-form"
        >
          <FormGroup
            name="password"
            label={t("password")}
            fieldId="password"
            helperTextInvalid={t("common:required")}
            validated={
              errors.password
                ? ValidatedOptions.error
                : ValidatedOptions.default
            }
            isRequired
          >
            <PasswordInput
              data-testid="passwordField"
              id="password"
              {...register("password", { required: true })}
            />
          </FormGroup>
          <FormGroup
            name="passwordConfirmation"
            label={
              isResetPassword
                ? t("resetPasswordConfirmation")
                : t("passwordConfirmation")
            }
            fieldId="passwordConfirmation"
            helperTextInvalid={errors.passwordConfirmation?.message}
            validated={
              errors.passwordConfirmation
                ? ValidatedOptions.error
                : ValidatedOptions.default
            }
            isRequired
          >
            <PasswordInput
              data-testid="passwordConfirmationField"
              id="passwordConfirmation"
              {...register("passwordConfirmation", {
                required: true,
                validate: (value) =>
                  value === password ||
                  t("confirmPasswordDoesNotMatch").toString(),
              })}
            />
          </FormGroup>
          <FormGroup
            label={t("common:temporaryPassword")}
            labelIcon={
              <HelpItem
                helpText={t("temporaryPasswordHelpText")}
                fieldLabelId="temporaryPassword"
              />
            }
            fieldId="kc-temporaryPassword"
          >
            <Controller
              name="temporaryPassword"
              defaultValue={true}
              control={control}
              render={({ field }) => (
                <Switch
                  className="kc-temporaryPassword"
                  onChange={field.onChange}
                  isChecked={field.value}
                  label={t("common:on")}
                  labelOff={t("common:off")}
                  aria-label={t("common:temporaryPassword")}
                />
              )}
            />
          </FormGroup>
        </Form>
      </ConfirmDialogModal>
    </>
  );
};
