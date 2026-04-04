import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";

import zhHans from "../locales/zh-Hans.json";
import zhHant from "../locales/zh-Hant.json";
import en from "../locales/en.json";
import ja from "../locales/ja.json";

const resources = {
  "zh-Hans": zhHans,
  "zh-Hant": zhHant,
  en,
  ja,
};

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    ns: ["language", "sidebar", "auth", "app", "tab", "scores", "settings", "adminStatic", "adminUsers", "aliases", "imports"],
    defaultNS: "app",
    fallbackLng: "zh-Hans",
    supportedLngs: ["zh-Hans", "zh-Hant", "en", "ja"],
    interpolation: {
      escapeValue: false, // react already safes from xss
    },
    detection: {
      order: ["localStorage", "navigator"],
      caches: ["localStorage"],
    },
  });

export default i18n;
