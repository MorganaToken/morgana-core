import { CodeEditor, Language } from "@patternfly/react-code-editor";
import { FormGroup } from "@patternfly/react-core";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { HelpItem } from "ui-shared";
import type { ComponentProps } from "./components";
import { convertToName } from "./DynamicComponents";

export const ScriptComponent = ({
  name,
  label,
  helpText,
  defaultValue,
  isDisabled = false,
}: ComponentProps) => {
  const { t } = useTranslation("dynamic");
  const { control } = useFormContext();

  return (
    <FormGroup
      label={t(label!)}
      labelIcon={
        <HelpItem
          helpText={<span style={{ whiteSpace: "pre-wrap" }}>{helpText}</span>}
          fieldLabelId={`dynamic:${label}`}
        />
      }
      fieldId={name!}
    >
      <Controller
        name={convertToName(name!)}
        defaultValue={defaultValue}
        control={control}
        render={({ field }) => (
          <CodeEditor
            id={name!}
            data-testid={name}
            isReadOnly={isDisabled}
            type="text"
            onChange={field.onChange}
            code={field.value}
            height="600px"
            language={Language.javascript}
          />
        )}
      />
    </FormGroup>
  );
};
