# Changelog

All notable changes to DocuForge AI are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Settings societe (ADMIN) : `GET/PUT /api/v1/admin/settings`, table `application_settings`, UI `/settings/*`, audit `SETTINGS_CHANGED`
- Formulaire dynamique dense : sections auto (préfixe de clé), grille 2 colonnes, barre d'actions sticky, ancre erreurs
- AppShell : nav sticky avec etat actif, titres de page dedies, largeurs unifiees (wide/default/form/narrow)
- Listes Templates / Utilisateurs / Lots : tables denses, creation dans panneau separe, StatusBadge
- Detail document : layout 2 colonnes (actions a gauche, apercu PDF sticky a droite)
- Dashboard : 4 KPI prioritaires + bandeau secondaire ; login marque 2 colonnes ; libelles FR
- i18n frontend FR/PT (i18next) + selecteur de langue (shell + login)
- Erreurs API bilingues FR/PT (MessageSource + Accept-Language)
- Phase 9 — DOCX Engine: DocumentGenerator, generate/preview/download, status GENERATED
- Phase 8 — Dynamic Forms: form-schema API, validate (HTTP 422), React form renderer
- Phase 7 — Variable Parser: DOCX {{variable}} detection, GET/PUT variables API
- Phase 6 — Template Management
- Phase 5 — Storage
- Phase 4 — Authentication
- Phase 3 — Database
- Phase 2 — Backend skeleton
- Phase 1 — Infrastructure
- Phase 0 — Repository skeleton
