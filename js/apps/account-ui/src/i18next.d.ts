// https://www.i18next.com/overview/typescript
import "i18next";

import translation from "../public/locales/en/translation.json";

export type TranslationKeys = keyof translation;

declare module "i18next" {
  interface CustomTypeOptions {
    defaultNS: "translation";
    resources: {
      translation: typeof translation;
    };
    // TODO: This flag should be removed and code that errors out should be made functional.
    // This will have to be done incrementally as the amount of errors the default produces is just too much.
    allowObjectInHTMLChildren: true;
  }
}
