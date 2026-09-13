import { useTranslation } from "react-i18next";
import { setAppLanguage, type AppLang } from "@/i18n";

export function LanguageSwitcher({ className = "" }: { className?: string }) {
  const { t, i18n } = useTranslation();
  const current = (i18n.language?.startsWith("pt") ? "pt" : "fr") as AppLang;

  return (
    <label className={`inline-flex items-center gap-1.5 text-sm text-[var(--muted)] ${className}`}>
      <span className="sr-only">{t("common.language")}</span>
      <select
        aria-label={t("common.language")}
        className="min-h-10 rounded-[var(--radius-lg)] border border-[var(--line)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--brand-ink)] outline-none focus:border-[var(--brand)]"
        value={current}
        onChange={(e) => setAppLanguage(e.target.value as AppLang)}
      >
        <option value="fr">{t("common.fr")}</option>
        <option value="pt">{t("common.pt")}</option>
      </select>
    </label>
  );
}
