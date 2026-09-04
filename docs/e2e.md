# E2E (Playwright) — Phase 20

Tests des critères d'acceptation MVP (§101) via l'UI, avec focus sur :

- téléchargement DOCX/PDF (§31)
- upload de template DOCX (§17) — **via API** (pas d'UI d'upload ; le seed E2E appelle `POST /templates` + `/versions`)
- génération formulaire, batch CSV, email Mailpit, audit

## Prérequis

1. Stack Docker démarrée (`postgres`, **backend à jour**, LibreOffice, Mailpit, …)
2. Backend joignable : `http://localhost:18081` (reconstruire si besoin : `docker compose build backend && docker compose up -d backend`)
3. Frontend : `http://localhost:5174` — Playwright réutilise un serveur existant, sinon lance `npm run dev` (Vite) pour les derniers correctifs UI
4. Compte bootstrap : `demo` / `admin@demo.local` / `changeme_admin_dev_only`
5. LibreOffice UNO joignable depuis le backend (`JODConverter connected…` dans les logs)

## Lancer

```bash
cd tests/e2e
npm install
npx playwright install chromium
npm test
```

Variables optionnelles :

| Variable | Défaut |
|----------|--------|
| `E2E_BASE_URL` | `http://localhost:5174` |
| `E2E_API_URL` | `http://localhost:18081` |
| `E2E_MAILPIT_URL` | `http://localhost:8028` |
| `E2E_COMPANY` / `E2E_EMAIL` / `E2E_PASSWORD` | bootstrap admin |

## Couverture §101

| # | Critère | Couverture |
|---|---------|------------|
| 2 | Connexion | `01-auth` |
| 4–10, 13 | Import DOCX, variables, formulaire, génération DOCX/PDF | `02-generate` (+ seed API) |
| 8 | Données invalides rejetées | email invalide dans `02-generate` |
| 11–12 | Prévisualisation + téléchargements | `03-downloads-preview` |
| 18–19 | Batch CSV | `04-batch` |
| 20–21 | Email Mailpit + audit | `05-email-audit` |
| 22 | Contrôle d'accès (redirect) | `01-auth` |
| 1, 3, 14–17, 23–27 | Docker, rôles fins, checksum, IA, isolation, backup, docs, secrets | hors UI E2E (unit/IT/docs/scripts) |

## Structure

```text
tests/e2e/
  playwright.config.ts
  fixtures/          # DOCX généré + CSV batch
  helpers/           # login API/UI, seed template
  specs/             # scénarios Playwright
```
