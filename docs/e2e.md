# E2E (Playwright) — Core + Business Packs

Tests des critères d'acceptation MVP Core (§101) et Business Pack (§§188–189) via l'UI.

## Prérequis

1. Stack Docker démarrée (`postgres`, **backend à jour** avec migrations packs V9–V11, LibreOffice, Mailpit, …)
2. Backend joignable : `http://localhost:18081` (reconstruire si besoin : `docker compose build backend && docker compose up -d backend`)
3. Frontend : Playwright lance Vite sur `http://127.0.0.1:5175` par défaut (évite un frontend Docker obsolète sur `:5174`). Pour réutiliser un serveur déjà lancé : `E2E_BASE_URL=…` + `E2E_REUSE_SERVER=1`
4. Compte bootstrap : `demo` / `admin@demo.local` / `changeme_admin_dev_only`
5. LibreOffice UNO joignable depuis le backend (`JODConverter connected…` dans les logs)
6. Artefact pack : `docs/business-packs/packs/artisan-demo/dist/docuforge-pack-artisan-demo-1.0.0.zip`

## Lancer

```bash
cd tests/e2e
npm install
npx playwright install chromium
npm test                 # suite complète
npm run test:packs       # §§188–189 uniquement
```

Variables optionnelles :

| Variable | Défaut |
|----------|--------|
| `E2E_BASE_URL` | `http://127.0.0.1:5175` |
| `E2E_VITE_PORT` | `5175` |
| `E2E_REUSE_SERVER` | unset (Vite démarré par Playwright) |
| `E2E_API_URL` | `http://localhost:18081` |
| `E2E_MAILPIT_URL` | `http://localhost:8028` |
| `E2E_COMPANY` / `E2E_EMAIL` / `E2E_PASSWORD` | bootstrap admin |

## Couverture

### Core §101

| # | Critère | Couverture |
|---|---------|------------|
| 2 | Connexion | `01-auth` |
| 4–10, 13 | Import DOCX, variables, formulaire, génération DOCX/PDF | `02-generate` (+ seed API) |
| 8 | Données invalides rejetées | email invalide dans `02-generate` |
| 11–12 | Prévisualisation + téléchargements | `03-downloads-preview` |
| 18–19 | Batch CSV | `04-batch` |
| 20–21 | Email Mailpit + audit | `05-email-audit` |
| 22 | Contrôle d'accès (redirect) | `01-auth` |

### Business Pack §§188–189

| Spec | Scénario |
|------|----------|
| `06-business-pack` | Import artisan-demo → install → generate `ARTISAN_DEVIS` |
| `07-business-pack-update` | 1.0.0 → doc → update 1.1.0 → nouveau doc + historique |

## Structure

```text
tests/e2e/
  playwright.config.ts
  fixtures/          # DOCX généré + CSV batch (+ ZIP pack copié au runtime)
  helpers/           # login API/UI, seed template, packs
  specs/             # scénarios Playwright
```
