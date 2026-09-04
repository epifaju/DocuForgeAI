# PostgreSQL (infrastructure)

Service Compose `postgres` — image `postgres:16.6-alpine` (Phase 1).

- Healthcheck : `pg_isready`
- Volume nommé : `postgres_data`
- Init optionnel : `infrastructure/postgres/init/`
- Migrations applicatives : Flyway (Phase 3), `ddl-auto=validate` (PRD §8)