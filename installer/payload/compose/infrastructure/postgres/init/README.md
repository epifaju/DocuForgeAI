# PostgreSQL init scripts

Scripts mounted into `/docker-entrypoint-initdb.d` (executed only on first volume init).

Application schema migrations are **not** placed here — they belong to Flyway in `backend/` (Phase 3).

This directory may hold optional extensions or roles for local/dev bootstrap only.