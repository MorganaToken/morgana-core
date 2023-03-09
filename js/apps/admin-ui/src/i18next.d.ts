// https://www.i18next.com/overview/typescript
import "i18next";

declare module "i18next" {
  interface CustomTypeOptions {
    // TODO: These flags should be removed and code that errors out should be made functional.
    // This will have to be done incrementally as the amount of errors the defaults produce is just too much.
    returnNull: false;
    allowObjectInHTMLChildren: true;
  }
}
