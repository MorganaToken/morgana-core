import {
  Button,
  Chip,
  FormGroup,
  Split,
  SplitItem,
} from "@patternfly/react-core";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";

import useToggle from "../../utils/useToggle";
import { HelpItem } from "ui-shared";
import { AddRoleMappingModal } from "../role-mapping/AddRoleMappingModal";
import { Row, ServiceRole } from "../role-mapping/RoleMapping";
import type { ComponentProps } from "./components";
import { convertToName } from "./DynamicComponents";

const parseValue = (value: any) =>
  value?.includes(".") ? value.split(".") : ["", value || ""];

const parseRow = (value: Row) =>
  value.client?.clientId
    ? `${value.client.clientId}.${value.role.name}`
    : value.role.name;

export const RoleComponent = ({
  name,
  label,
  helpText,
  defaultValue,
  isDisabled = false,
}: ComponentProps) => {
  const { t } = useTranslation("dynamic");

  const [openModal, toggleModal] = useToggle();
  const {
    control,
    formState: { errors },
  } = useFormContext();

  const fieldName = convertToName(name!);

  return (
    <FormGroup
      label={t(label!)}
      labelIcon={
        <HelpItem helpText={t(helpText!)} fieldLabelId={`dynamic:${label}`} />
      }
      validated={errors[fieldName] ? "error" : "default"}
      helperTextInvalid={t("common:required")}
      fieldId={name!}
    >
      <Controller
        name={fieldName}
        defaultValue={defaultValue || ""}
        control={control}
        render={({ field }) => (
          <Split>
            {openModal && (
              <AddRoleMappingModal
                id="id"
                type="roles"
                name={name}
                onAssign={(rows) => field.onChange(parseRow(rows[0]))}
                onClose={toggleModal}
                isRadio
              />
            )}

            {field.value !== "" && (
              <SplitItem>
                <Chip textMaxWidth="500px" onClick={() => field.onChange("")}>
                  <ServiceRole
                    role={{ name: parseValue(field.value)[1] }}
                    client={{ clientId: parseValue(field.value)[0] }}
                  />
                </Chip>
              </SplitItem>
            )}
            <SplitItem>
              <Button
                onClick={toggleModal}
                variant="secondary"
                data-testid="add-roles"
                disabled={isDisabled}
              >
                {t("selectRole.label")}
              </Button>
            </SplitItem>
          </Split>
        )}
      />
    </FormGroup>
  );
};
