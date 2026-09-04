import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import fr from "./locales/fr.json";
import pt from "./locales/pt.json";

export const LANG_STORAGE_KEY = "docuforge.lang";
export const SUPPORTED_LANGS = ["fr", "pt"] as const;
export type AppLang = (typeof SUPPORTED_LANGS)[number];

function resolveInitialLang(): AppLang {
  const stored = localStorage.getItem(LANG_STORAGE_KEY);
  if (stored === "fr" || stored === "pt") return stored;
  const nav = navigator.language.toLowerCase();
  if (nav.startsWith("pt")) return "pt";
  return "fr";
}

void i18n.use(initReactI18next).init({
  resources: {
    fr: { translation: fr },
    pt: { translation: pt },
  },
  lng: resolveInitialLang(),
  fallbackLng: "fr",
  interpolation: { escapeValue: false },
});

export function setAppLanguage(lang: AppLang) {
  localStorage.setItem(LANG_STORAGE_KEY, lang);
  document.documentElement.lang = lang === "pt" ? "pt" : "fr";
  void i18n.changeLanguage(lang);
}

document.documentElement.lang = i18n.language === "pt" ? "pt" : "fr";

export default i18n;

export function dateLocale(lang?: string): string {
  return (lang ?? i18n.language) === "pt" ? "pt-PT" : "fr-FR";
}
