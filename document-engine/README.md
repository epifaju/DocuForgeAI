# Document Engine

Module conceptuel du moteur documentaire DocuForge AI (PRD §9, §14).

Implementation MVP dans le backend Spring Boot :

- Interface `DocumentGenerator`
- `PoiDocxDocumentGenerator` — fusion `{{variable}}` avec coalescence des runs Word
  (meme objectif split-run que docx-stamper §9, compatible avec le parser Phase 7)
- API : `POST /api/v1/documents/generate|preview`, `GET /api/v1/documents/{id}`,
  `GET /api/v1/documents/{id}/download/docx`
- Statut Phase 9 : `GENERATED` (PDF / `COMPLETED` = Phase 10)

Extraction eventuelle en artefact separe post-MVP uniquement.
