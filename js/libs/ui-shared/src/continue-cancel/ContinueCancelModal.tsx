import { ReactNode, useState } from "react";
import { Button, ButtonProps, Modal, ModalProps } from "@patternfly/react-core";

export type ContinueCancelModalProps = Omit<ModalProps, "ref" | "children"> & {
  modalTitle: string;
  modalMessage?: string;
  buttonTitle: string | ReactNode;
  buttonVariant?: ButtonProps["variant"];
  isDisabled?: boolean;
  onContinue: () => void;
  continueLabel?: string;
  cancelLabel?: string;
  component?: React.ElementType<any> | React.ComponentType<any>;
  children?: ReactNode;
};

export const ContinueCancelModal = ({
  modalTitle,
  modalMessage,
  buttonTitle,
  isDisabled,
  buttonVariant,
  onContinue,
  continueLabel = "continue",
  cancelLabel = "doCancel",
  component = Button,
  children,
  ...rest
}: ContinueCancelModalProps) => {
  const [open, setOpen] = useState(false);
  const Component = component;

  return (
    <>
      <Component
        variant={buttonVariant}
        onClick={() => setOpen(true)}
        isDisabled={isDisabled}
      >
        {buttonTitle}
      </Component>
      <Modal
        variant="small"
        {...rest}
        title={modalTitle}
        isOpen={open}
        onClose={() => setOpen(false)}
        actions={[
          <Button
            id="modal-confirm"
            key="confirm"
            variant="primary"
            onClick={() => {
              setOpen(false);
              onContinue();
            }}
          >
            {continueLabel}
          </Button>,
          <Button
            id="modal-cancel"
            key="cancel"
            variant="secondary"
            onClick={() => setOpen(false)}
          >
            {cancelLabel}
          </Button>,
        ]}
      >
        {modalMessage ? modalMessage : children}
      </Modal>
    </>
  );
};
