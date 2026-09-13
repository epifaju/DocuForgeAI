# DocuForge AI — Audit design / UI & proposition de refonte

**Périmètre :** `frontend/` (React 19, TypeScript, Vite 6, Tailwind CSS v4, React Router 7, TanStack Query, React Hook Form, Zod, i18next).  
**Date :** 2026-09-13  
**Statut :** analyse & proposition uniquement — **aucune modification de code** à cette étape.

---

## Synthèse exécutive

DocuForge AI dispose déjà d’une **identité visuelle reconnaissable** (vert sauge `#1f6b4f`, papier chaud, Fraunces + Source Sans 3) et de pics UX réussis (login split, dashboard animé, formulaire dynamique + barre sticky). En revanche, le système est surtout **artisanal** : pas de primitives Button/Input/Table, styles Tailwind **copiés-collés**, navigation horizontale saturée pour un back-office, listes peu adaptées au mobile, et quelques dettes d’accessibilité (cibles trop petites, modales sans focus trap).

**Verdict :** base solide pour un SaaS B2B français ; la refonte doit **industrialiser le design system** et **hiérarchiser les parcours métier** (génération, templates, documents), pas remplacer la stack ni tout redessiner d’un coup.

---

## 1. Inventaire de l’existant

### 1.1 Stack UI (à réutiliser en priorité)

| Déjà en place | Rôle |
|---|---|
| Tailwind CSS v4 (`@tailwindcss/vite`) | Utilitaires, responsive |
| Variables CSS dans `src/index.css` | Tokens couleur / surface |
| Fraunces + Source Sans 3 (`index.html`) | Display + corps |
| React Router 7 | Navigation |
| TanStack Query | Données / cache |
| React Hook Form + Zod | Formulaires dynamiques (`DynamicForm`) |
| i18next (FR défaut, PT) | Localisation |
| CSS keyframes (`dash-*`) | Motion dashboard |

**Absents (volontairement) :** Headless UI, Radix, shadcn, Framer Motion, MUI, icônes (lucide), `clsx`/`cva`.  
**Recommandation :** ne pas introduire de grosse UI kit ; ajouter au besoin **`clsx` + `class-variance-authority`** (légers) pour factoriser Button/Badge, et éventuellement **lucide-react** pour une iconographie métier cohérente.

### 1.2 Tokens globaux (`src/index.css`)

```css
--bg: #f3efe6; --bg-accent: #e7efea; --ink: #1c2420; --muted: #5c6b63;
--line: #c9d2cb; --brand: #1f6b4f; --brand-ink: #0f3d2e; --danger: #9b2c2c;
--surface: #fffdf8;
```

- Fond atmosphérique : doubles `radial-gradient` (hex hors variables).
- Mode sombre : **absent**.
- Motion : `dash-rise` / `dash-count` / `dash-line` + `prefers-reduced-motion`.

### 1.3 Chrome applicatif

| Fichier | Rôle |
|---|---|
| `AppShell.tsx` | Header sticky, nav horizontale scrollable, titre page, actions |
| `App.tsx` | Routes ; pas de layout route partagé (chaque page remonte `AppShell`) |
| `CreatePanel.tsx` | Panneau création collapsible |
| `StatusBadge.tsx` | Pastilles de statut (certains tons en hex) |
| `ConfirmDialog.tsx` | Modale confirm (Escape, pas de focus trap) |
| `DynamicForm.tsx` | Formulaire schéma + Zod + sticky submit |
| `AiAssistToolbar.tsx` | Actions IA (libellés FR hardcodés) |
| `LanguageSwitcher.tsx` | FR/PT |
| `PackUpdatePreviewPanel.tsx` | Diff mise à jour pack |

### 1.4 Pages (cartographie UX)

| Criticité | Écran | Fichier | Pattern actuel |
|---|---|---|---|
| **P0** | Login | `LoginPage.tsx` | Split brand / form |
| **P0** | Dashboard | `DashboardPage.tsx` | KPI hero + feeds |
| **P0** | Génération (formulaire) | `FormPage.tsx` + `DynamicForm.tsx` | Sections + sticky CTA |
| **P0** | Détail document / export | `DocumentDetailPage.tsx` | Meta + preview + email |
| **P0** | Templates | `TemplatesPage.tsx` | Table + CreatePanel |
| **P1** | Documents | `DocumentsPage.tsx` | Filtres + table |
| **P1** | Nouvelle version | `DocumentNewVersionPage.tsx` | DynamicForm |
| **P1** | Lots CSV | `BatchesPage.tsx` / `BatchDetailPage.tsx` | Table / détail |
| **P2** | Packs métier | `BusinessPacks*` | Tables + wizard import |
| **P2** | Users / Settings / Audit / Privacy | pages admin | Formulaires / tables |

**Breakpoints observés :** `sm:` et `lg:` (parfois `xl:` sur détail document). **`md:` n’est quasiment jamais utilisé** → saut brutal téléphone → desktop.

---

## 2. Problèmes identifiés

### 2.1 Incohérences visuelles

| Problème | Détail | Fichiers / zones |
|---|---|---|
| **Pas de primitives UI** | Boutons / inputs / tables recopiés ; drift de padding (`px-3.5` vs `px-4`), hover parfois absent | Presque toutes les pages |
| **Rayons mixtes** | Surfaces `rounded-2xl`, contrôles `rounded-xl`, chips `rounded-lg`, badges `rounded-md` | Dashboard, StatusBadge, Toolbars |
| **Couleurs hors tokens** | Badges en `#eef3e8`, `#eeeae2`, `#f5e6e6` ; gradients login en hex inline | `StatusBadge.tsx`, `LoginPage.tsx`, `index.css` |
| **Inputs `bg-white` vs `--surface`** | Double blanc/crème selon les écrans | Listes vs DynamicForm |
| **Focus styles inégaux** | DynamicForm / Login : `focus:border-[var(--brand)]` ; Templates create souvent sans | `TemplatesPage`, filtres Documents |
| **Hiérarchie brand** | Fraunces sur tous les `h1` AppShell → titres de pages listes aussi « éditoriaux » ; brand login fort (bien), listes admin moins « outil » | `AppShell`, pages liste |
| **Elevation incohérente** | `shadow-sm` (DynamicForm), `shadow-lg` (modales), la plupart des cartes sans ombre | Divers |

### 2.2 Responsive (mobile / tablette)

| Problème | Impact | Zones |
|---|---|---|
| **Tables non adaptées** | Scroll horizontal seul (`overflow-x-auto`) ; pas de vue carte mobile | Templates, Documents, Batches, Users, Packs, Audit |
| **Nav horizontale dense** | Jusqu’à ~8 liens (admin) en `overflow-x-auto` ; pas de menu hamburger / drawer | `AppShell.tsx` |
| **Header width fixe `max-w-6xl`** | Même si `main` est `form` / `narrow` → désalignement perçu | `AppShell.tsx` L61 vs L113 |
| **Pas de palier `md:`** | Layouts passent de 1 col (`sm`) à 2–3 cols (`lg`) | Majorité des grilles |
| **DocumentDetail sticky preview** | Hauteur `lg:h-[calc(100vh-12rem)]` ; mobile empile mais preview PDF moins confortable | `DocumentDetailPage.tsx` |
| **Filtres Documents `sm:grid-cols-4`** | Sur tablette étroite, 4 colonnes trop serrées | `DocumentsPage.tsx` |
| **Logout / actions table** | Liens soulignés, pas de zone tactile ≥ 44px | AppShell, Templates row actions |

### 2.3 Accessibilité (base)

| Problème | Sévérité | Où |
|---|---|---|
| Cibles cliquables trop petites | Haute | Logout, actions table (`underline`), `AiAssistToolbar` (`px-2 py-1 text-xs`) |
| Modale sans focus trap / focus initial | Haute | `ConfirmDialog.tsx` |
| Texte `text-[11px]` | Moyenne | Timestamps dashboard |
| Auth gate `null` sans skeleton / `aria-busy` | Moyenne | `App.tsx` Protected |
| Checkbox `size-4` | Moyenne | `DynamicForm` |
| Contrastes | Globalement OK (vert foncé / encre sur papier) ; badges muted sur fond crème à surveiller | StatusBadge DRAFT etc. |
| Labels | Souvent présents sur formulaires ; bon usage `aria-invalid` (Login, Templates) | — |

### 2.4 Duplication / incohérence Tailwind

- Chaîne type **carte** répétée partout :  
  `rounded-2xl border border-[var(--line)] bg-[var(--surface)]`
- Chaîne type **input** :  
  `mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2` (± focus)
- Chaîne type **bouton primary** : variantes `py-2` / `py-2.5`, avec/sans `hover:bg-[var(--brand-ink)]`
- Aucun `Button` / `Input` / `Field` / `DataTable` partagé
- `CreatePanel` vs formulaires pleine page (Settings) : deux patterns de création

### 2.5 i18n / polish produit

- `AiAssistToolbar` : libellés FR hardcodés (hors i18n / PT)
- Email document : sujets/corps par défaut FR hardcodés
- Marque « DocuForge AI » hardcodée (acceptable) ; nav FR garde le mot anglais « Templates »
- `formLayout` fallback section `"autres"` non localisé

### 2.6 Écrans critiques — lecture UX actuelle

| Écran | Forces | Faiblesses |
|---|---|---|
| **Dashboard** | Composition claire, motion, KPI fort, liens KPI filtrés | Densité admin secondaire (chips) ; micro-texte |
| **Templates** | CRUD complet | Table « admin générique » ; actions en liens ; filtre statut récent mais UI minimale |
| **Formulaire génération** | Meilleure UX produit ; sections, erreurs, sticky CTA, IA | Sticky bar calée sur `max-w-4xl` ; toolbar IA peu visible / peu tactile |
| **Détail document** | Preview + downloads + email | Charge cognitive ; confirmations inline ; defaults email FR |
| **Login** | Brand fort, split desktop | Gradients hors tokens ; peu de continuité visuelle avec AppShell |

---

## 3. Proposition de direction artistique

### 3.1 Positionnement

**« Outil métier de confiance »** — SaaS B2B français pour TPE/PME : clair, calme, efficace.  
Conserver le **vert DocuForge** comme signature (confiance, documents officiels), mais **assainir le fond** trop « papier éditorial » qui rapproche l’UI d’un look générique cream + serif.

### 3.2 Palette proposée (clair par défaut)

| Token | Valeur proposée | Usage |
|---|---|---|
| `--brand` | `#1a5c45` (légèrement plus profond que actuel) | CTA, liens actifs |
| `--brand-ink` | `#0c2f24` | Titres, brand |
| `--brand-soft` | `#e6f2ec` | Fonds sélection / badges succès |
| `--bg` | `#f6f7f5` | Fond app (gris-vert très clair, moins crème) |
| `--surface` | `#ffffff` | Cartes, panels |
| `--ink` | `#1a1f1c` | Texte principal |
| `--muted` | `#5a665f` | Secondaire |
| `--line` | `#d5ddd8` | Bordures |
| `--danger` | `#a32828` | Erreurs / destructif |
| `--warning` | `#8a6a1f` + `--warning-soft` | En cours / attention |
| `--info` | `#2a5a7a` + soft | Info / IA |

Éviter : thème violet, glow, pastilles `rounded-full` excessives, multi-ombres.  
**Mode sombre :** non prioritaire (outil métier diurne) ; prévoir tokens `--*` prêts, livrer dark en phase 2 si demande.

### 3.3 Typographie

| Rôle | Police | Échelle |
|---|---|---|
| Brand / login / KPI | **Fraunces** (conservée, usage restreint) | Display 2–3 tailles max |
| UI / formulaires / tables | **Source Sans 3** (ou **IBM Plex Sans** si on veut plus « admin ») | 12 / 14 / 16 / 18 / 24 / 32 |
| Mono (codes, refs) | `ui-monospace` / Source Code | 12–13 |

Règle : **pas de Fraunces** sur les titres de listes admin (`Templates`, `Documents`) — réserver au brand et au dashboard hero.

### 3.4 Espacement (grille 4 / 8)

- Base **4px** ; rythme courant **8 / 16 / 24 / 32 / 48**.
- Page : `px-4 sm:px-6 lg:px-8`, `py-6 sm:py-8`.
- Cartes : padding `p-4 sm:p-5` (listes) / `p-5 sm:p-6` (formulaires).
- Densité tables : row `py-3` (confort) ; mode compact optionnel `py-2` pour power users plus tard.

### 3.5 Motion

- Garder 2–3 motions dashboard (déjà bien cadrées + `prefers-reduced-motion`).
- Ailleurs : transitions courtes (`150–200ms`) sur hover/focus uniquement — pas d’animations décoratives sur les listes.

---

## 4. Design system minimal (à implémenter après validation)

### 4.1 Composants cibles (Tailwind only)

| Composant | Variantes | Notes |
|---|---|---|
| **Button** | `primary`, `secondary`, `ghost`, `danger` ; sizes `sm` / `md` | min-height 40px (`md`), 36px (`sm`) ; focus ring brand |
| **Input / Textarea / Select** | default, error, disabled | même hauteur que Button md ; label + hint + error |
| **Field** | wrapper label + control + message | unifie Login / Templates / DynamicForm |
| **Card** | `default`, `flush` | remplace la chaîne `rounded-2xl border…` |
| **Badge** | success / neutral / progress / danger / warning | remplace hex de `StatusBadge` |
| **Table** | header sticky optionnel + empty state | desktop ; **CardList** mobile |
| **Modal / ConfirmDialog** | focus trap, Esc, retour focus | améliorer l’existant |
| **Toast** | success / error | aujourd’hui messages inline `text-sm` — toast pour feedback non bloquant |
| **Tabs / Stepper** | import pack, sections form | réutiliser pattern BusinessPackImport |
| **EmptyState** | illustration sobre + CTA | Dashboard / listes |

Implémentation suggérée : fichiers `src/components/ui/*` + helpers `cn()` (`clsx` + `tailwind-merge` optionnel).

### 4.2 Navigation proposée

**Conserver le topbar pour le MVP court terme**, mais :

**Phase A (quick win) :**
- Regrouper admin sous un menu « Admin » (Packs, Users, Settings, Audit) → réduire le nombre d’onglets visibles.
- Améliorer la zone tactile logout ; afficher société + rôle en chip.
- Aligner largeur header / main selon `width`.

**Phase B (refonte navigation) :**
- **Sidebar collapsible** (desktop) + **drawer** (mobile) — pattern standard back-office.
- Groupes : *Travail* (Dashboard, Templates, Documents, Lots) · *Admin* (Packs, Users, Settings, Audit) · *Compte* (langue, logout, Mes données).
- Breadcrumb sous le titre pour Packs / Document / Batch détail.

Le topbar actuel reste acceptable pour ≤ 5 items ; au-delà (admin), sidebar gagne clairement.

### 4.3 Patterns à réutiliser (déjà dans le projet)

1. **CreatePanel** → évoluer vers `PageHeader` + `Sheet` / drawer create sur mobile.
2. **DynamicForm** sticky footer → modèle pour tous les formulaires longs.
3. **StatusBadge** → brancher sur tokens soft (pas de hex).
4. **ConfirmDialog** → base Modal accessible.
5. **Wizard import packs** → pattern d’étapes pour un éventuel « Assistant génération » (template → données → revue → export).
6. **Filtres Documents** (`?status=`) → généraliser (Templates, Batches).

---

## 5. Améliorations par écran à fort enjeu

### 5.1 Création / génération de document (P0)

**Aujourd’hui :** `FormPage` + `DynamicForm` (déjà le meilleur parcours).

**Proposition :**
1. **Stepper léger** au-dessus : Template choisi → Saisie → Génération → Document.
2. **Sommaire de sections** sticky à gauche dès `lg:` (déjà partiellement là) ; sur mobile, select « Aller à la section ».
3. **Toolbar IA** en boutons `sm` 40px, i18n, état disabled explicite si IA off.
4. Après succès : **panel résultat** (télécharger DOCX/PDF, ouvrir détail) plus proéminent que le message texte actuel.
5. Option ultérieure : wizard « nouveau document » depuis Dashboard (choix template en cartes, pas seulement lien Templates).

### 5.2 Aperçu / export document (P0)

**Proposition :**
1. Layout **split** : métadonnées + actions à gauche (ou haut mobile), preview PDF plein viewport à droite.
2. Groupe d’actions clair : *Télécharger* (split DOCX/PDF) · *Envoyer* · *Nouvelle version*.
3. Email : drawer / modal dédié (pas un long formulaire dans le flux principal) ; sujets i18n.
4. États PDF (conversion / échec) avec Badge warning/danger + CTA retry.

### 5.3 Liste / gestion templates (P0)

**Proposition :**
1. **Toolbar** : recherche + filtre statut + « Nouveau » (Button primary).
2. **Desktop :** table dense avec actions en `Button ghost` / menu ⋯ (pas de liens soulignés).
3. **Mobile :** cartes (nom, code mono, badge statut, CTA Formulaire).
4. Empty state guidé (importer pack / créer template).
5. Alignement sémantique avec le KPI dashboard (`?status=ACTIVE`) — déjà amorcé.

### 5.4 Dashboard (P1 polish)

- Garder la composition actuelle (bonne).
- Remplacer `text-[11px]` par `text-xs`.
- Ops chips → style `secondary` unifié.
- Réduire Fraunces au KPI principal uniquement.

---

## 6. Recommandations priorisées

### Quick wins (1–3 jours) — fort ROI, peu de risque

1. Extraire **Button** + **Input/Select** + **Field** ; migrer Login + Templates + Documents.
2. Remapper **StatusBadge** sur tokens (`--brand-soft`, `--danger-soft`, etc.) ; supprimer hex.
3. Remonter **taille des cibles** (logout, actions table, AI toolbar ≥ 36–40px).
4. **Focus trap** sur `ConfirmDialog`.
5. Skeleton auth au lieu de `null` dans `App.tsx`.
6. i18n **AiAssistToolbar** + defaults email document.
7. Menu déroulant **Admin** dans AppShell pour désengorger la nav.
8. Documenter tokens dans `index.css` (`--radius-sm/md/lg`, `--space-*`).

### Refonte intermédiaire (1–2 sprints)

1. **CardList mobile** pour Templates / Documents / Batches.
2. Introduire palier **`md:`** sur filtres et grilles (2 colonnes tablette).
3. Harmoniser header/main widths ; `PageHeader` partagé.
4. Toast pour succès upload / génération / import pack.
5. Affiner palette fond (`--bg` moins crème) tout en gardant `--brand`.
6. Restreindre Fraunces (brand + dashboard KPI).

### Refonte plus profonde (après validation produit)

1. **Sidebar + drawer** navigation back-office.
2. Wizard « Nouveau document » de bout en bout.
3. Design tokens Tailwind v4 `@theme` branchés sur les CSS variables.
4. Mode sombre optionnel.
5. Iconographie (lucide) pour nav et empty states.
6. Tests visuels / Storybook léger pour `ui/*` (optionnel).

---

## 7. Hors scope / non-recommandé (pour cette phase)

- Remplacer Tailwind par une autre lib CSS.
- Adopter MUI / Ant Design / Chakra (trop lourd vs stack actuelle).
- Dark mode dès la v1 de refonte.
- Refonte graphique totale type « landing marketing » (le produit est un back-office).
- Changer le nom / logo hors d’une décision marque séparée.

---

## 8. Critères de succès (après implémentation future)

| Critère | Mesure |
|---|---|
| Cohérence | ≥ 90 % des boutons / inputs passent par `ui/Button` & `ui/Input` |
| Mobile | Templates + Documents utilisables sans scroll horizontal forcé (cartes) |
| Accessibilité | Cibles ≥ 40px ; modales focus-trap ; contrastes WCAG AA texte UI |
| Parcours génération | Temps perçu « template → document téléchargé » sans confusion d’actions |
| Identité | Brand DocuForge toujours identifiable sans le nav (test « brand first ») |

---

## 9. Décisions à valider avant code

1. **Navigation :** topbar enrichi (Admin menu) **vs** sidebar dès maintenant ?
2. **Fond :** garder le papier chaud actuel **vs** passer au gris-vert clair proposé ?
3. **Fraunces :** restreindre aux moments brand **vs** conserver sur tous les `h1` ?
4. **Dépendances :** autoriser `clsx` + `cva` (+ lucide) **vs** zéro nouvelle dépendance ?
5. **Périmètre v1 refonte :** quick wins seuls **vs** quick wins + Cards mobile Templates/Documents ?

---

## Annexe A — Fichiers clés audités

```
frontend/src/index.css
frontend/src/App.tsx
frontend/src/components/AppShell.tsx
frontend/src/components/StatusBadge.tsx
frontend/src/components/CreatePanel.tsx
frontend/src/components/ConfirmDialog.tsx
frontend/src/components/DynamicForm.tsx
frontend/src/components/AiAssistToolbar.tsx
frontend/src/components/LanguageSwitcher.tsx
frontend/src/pages/LoginPage.tsx
frontend/src/pages/DashboardPage.tsx
frontend/src/pages/TemplatesPage.tsx
frontend/src/pages/DocumentsPage.tsx
frontend/src/pages/FormPage.tsx
frontend/src/pages/DocumentDetailPage.tsx
frontend/src/pages/DocumentNewVersionPage.tsx
frontend/src/pages/BatchesPage.tsx
frontend/src/pages/BusinessPackImportPage.tsx
… (+ Users, Settings, Audit, Privacy, Packs list/detail)
frontend/package.json
```

## Annexe B — Mapping quick wins → fichiers

| Quick win | Fichiers principaux |
|---|---|
| Button / Input / Field | nouveau `components/ui/*` ; migration Login, Templates, Documents |
| StatusBadge tokens | `StatusBadge.tsx`, `index.css` |
| Cibles tactiles | `AppShell.tsx`, `TemplatesPage.tsx`, `AiAssistToolbar.tsx` |
| Focus trap modal | `ConfirmDialog.tsx` |
| Auth skeleton | `App.tsx` |
| i18n IA / email | `AiAssistToolbar.tsx`, `DocumentDetailPage.tsx`, locales |
| Menu Admin | `AppShell.tsx`, i18n `nav.*` |

---

## Vague 0 — Fondations (livré 2026-09-13)

Sans migration des pages métier (réservée Vague 1) :

- Tokens étendus dans `frontend/src/index.css` (`--brand-soft`, `--danger-soft`, `--warning*`, `--info*`, `--radius-*`, `--space-*`, fond `--bg` assaini)
- `frontend/src/lib/cn.ts` + deps `clsx` / `class-variance-authority` / `tailwind-merge`
- Primitives `frontend/src/components/ui/` : Button, Input, Select, Textarea, Field, Card, Badge
- `StatusBadge` branché sur `Badge` + tokens (hex retirés)

## Vague 1 — Quick wins + listes (livré 2026-09-13)

Voir [`docs/DESIGN_WAVE1.md`](./DESIGN_WAVE1.md). Décisions : topbar + menu Admin, papier chaud conservé, Fraunces restreint, CardList Templates/Documents, migration `ui/*` Login/Templates/Documents.

*Fin du rapport. Vague 0–1 livrées.*
