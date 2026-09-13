# DocuForge AI — Vague 1 (design / UX)

**Date :** 2026-09-13  
**Statut :** livré  
**Décisions validées :** topbar conservée (pas de sidebar) · fond papier chaud `--bg: #f3efe6` · Fraunces limité brand + KPI dashboard · CardList mobile Templates/Documents · i18n FR/PT.

---

## Objectif

Quick wins a11y/UX + migration des écrans P0 listes (Login, Templates, Documents) vers les primitives `ui/*` posées en Vague 0, sans sidebar ni dark mode.

---

## Composants / fichiers touchés

| Zone | Fichiers |
|---|---|
| Tokens | `frontend/src/index.css` — restauration papier chaud (`--bg`, `--surface`, brand historique, `--wash-warm`) ; Fraunces uniquement via `.brand` |
| Nav | `AppShell.tsx` — menu déroulant **Admin** (Packs / Users / Settings / Audit / Privacy), logout `Button` ≥ 40px, titres pages en Source Sans 3 |
| Dialog | `ConfirmDialog.tsx` — focus trap Tab, focus initial sur Annuler, restore focus à la fermeture |
| Auth | `App.tsx` — skeleton session `aria-busy` |
| IA | `AiAssistToolbar.tsx` — i18n + `Button` sm |
| Listes mobile | `CardList.tsx` (nouveau) |
| Pages | `LoginPage.tsx`, `TemplatesPage.tsx`, `DocumentsPage.tsx` → `Button` / `Field` / `Input` / `Select` / `Textarea` / `Card` |
| Email défauts | `DocumentDetailPage.tsx` — sujets/corps via i18n |
| Locales | `fr.json`, `pt.json` — `nav.admin`, `nav.privacy`, `ai.*`, `document.emailDefault*`, `common.authLoading` |
| Dépendance | `lucide-react` (chevron menu Admin) |

---

## Comportements clés

1. **Admin menu** : visible pour tout utilisateur authentifié (au minimum *Mes données*). Packs / Users / Settings réservés `ADMIN` ; Audit pour `ADMIN` + `EDITOR`.
2. **CardList** : sous `md:` cartes ; table classique `hidden md:block`. Filtres Templates/Documents passent par `md:` / `lg:`.
3. **Cibles tactiles** : actions table/ghost `min-h-9`+, logout `min-h-10`, LanguageSwitcher `min-h-10`.
4. **Typo** : logo Login + KPI principal Dashboard (`.brand`) restent Fraunces ; `h1` AppShell et titres listes en sans-serif.

---

## Risques / non-régression (migration primitives)

Aucun écran migré ne perd de fonctionnalité métier dans cette vague :

| Écran | Verdict |
|---|---|
| **Login** | OK — mêmes champs / validation / navigation forgot-password |
| **Templates** | OK — create, upload DOCX, activate/archive, filtre statut, pagination ; actions partagées table ↔ cartes |
| **Documents** | OK — filtres q/status/template, ouverture détail, pagination ; cartes exposent les mêmes métadonnées utiles |

**Hors périmètre Vague 1 (non migrés vers `ui/*`) :** FormPage / DynamicForm, DocumentDetail (hors i18n email), Batches, Packs, Users, Settings, Audit — pas de perte, styles legacy inchangés.

**Note Privacy :** le lien « Mes données » est désormais dans Admin (auparavant absent de la nav). Les VIEWER y accèdent aussi.

---

## Captures avant / après

Non jointes dans le dépôt (environnement agent sans screenshots UI). Vérification manuelle suggérée :

- [ ] Topbar : Dashboard / Templates / Documents / Lots + **Admin ▾**
- [ ] Admin ▾ : items selon rôle ; Privacy toujours présent
- [ ] Templates & Documents : cartes &lt; `md`, table ≥ `md`
- [ ] ConfirmDialog (ex. Privacy purge) : Tab cycle + Escape + focus retour
- [ ] Login : Fraunces sur brand ; fond papier chaud
- [ ] Dashboard : KPI « docs today » toujours Fraunces ; titre page sans

---

## Suite suggérée (Vague 2+)

- Migrer DynamicForm / DocumentDetail / Batches vers `ui/*`
- CardList Batches / Packs / Users
- Sticky generation UX (audit § Vague 2)
