-- DocuForge AI — Phase 3 core schema (PRD §§46-55, §16, §40, §45)
-- Business seed data (demo company/users/templates) is deferred to later phases.

CREATE TABLE companies (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    identifier  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_companies_identifier UNIQUE (identifier)
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    company_id    UUID NOT NULL REFERENCES companies (id),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_company_email UNIQUE (company_id, email)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE templates (
    id                  UUID PRIMARY KEY,
    company_id          UUID NOT NULL REFERENCES companies (id),
    code                VARCHAR(100) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    category            VARCHAR(100),
    status              VARCHAR(30) NOT NULL,
    current_version_id  UUID,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_templates_company_code UNIQUE (company_id, code)
);

CREATE TABLE template_versions (
    id                 UUID PRIMARY KEY,
    template_id        UUID NOT NULL REFERENCES templates (id),
    version_number     INTEGER NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    checksum           VARCHAR(64) NOT NULL,
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_template_versions_template_version UNIQUE (template_id, version_number)
);

ALTER TABLE templates
    ADD CONSTRAINT fk_templates_current_version
        FOREIGN KEY (current_version_id) REFERENCES template_versions (id);

CREATE TABLE template_variables (
    id                   UUID PRIMARY KEY,
    template_version_id  UUID NOT NULL REFERENCES template_versions (id) ON DELETE CASCADE,
    variable_key         VARCHAR(200) NOT NULL,
    label                VARCHAR(200) NOT NULL,
    type                 VARCHAR(50) NOT NULL,
    required             BOOLEAN NOT NULL DEFAULT FALSE,
    default_value        TEXT,
    placeholder          TEXT,
    display_order        INTEGER NOT NULL DEFAULT 0,
    configuration        JSONB,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_template_variables_version_key UNIQUE (template_version_id, variable_key)
);

CREATE TABLE generated_documents (
    id                    UUID PRIMARY KEY,
    company_id            UUID NOT NULL REFERENCES companies (id),
    template_id           UUID NOT NULL REFERENCES templates (id),
    template_version_id   UUID NOT NULL REFERENCES template_versions (id),
    reference             VARCHAR(100) NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    status                VARCHAR(30) NOT NULL,
    data_snapshot         JSONB NOT NULL,
    docx_storage_key      VARCHAR(500),
    pdf_storage_key       VARCHAR(500),
    checksum              VARCHAR(64),
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_generated_documents_company_reference UNIQUE (company_id, reference)
);

CREATE TABLE batch_jobs (
    id                    UUID PRIMARY KEY,
    company_id            UUID NOT NULL REFERENCES companies (id),
    template_version_id   UUID NOT NULL REFERENCES template_versions (id),
    status                VARCHAR(30) NOT NULL,
    total_items           INTEGER NOT NULL DEFAULT 0,
    processed_items       INTEGER NOT NULL DEFAULT 0,
    successful_items      INTEGER NOT NULL DEFAULT 0,
    failed_items          INTEGER NOT NULL DEFAULT 0,
    locked_at             TIMESTAMPTZ,
    locked_by             VARCHAR(100),
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ
);

CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY,
    company_id   UUID REFERENCES companies (id),
    user_id      UUID REFERENCES users (id),
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100),
    entity_id    VARCHAR(100),
    status       VARCHAR(30) NOT NULL,
    ip_address   VARCHAR(45),
    metadata     JSONB,
    created_at   TIMESTAMPTZ NOT NULL
);

-- PRD §55 indexes (unique constraints already cover some pairs)
CREATE INDEX idx_users_company_email ON users (company_id, email);
CREATE INDEX idx_templates_company_status ON templates (company_id, status);
CREATE INDEX idx_templates_company_code ON templates (company_id, code);
CREATE INDEX idx_generated_documents_company_reference ON generated_documents (company_id, reference);
CREATE INDEX idx_generated_documents_company_created_at ON generated_documents (company_id, created_at);
CREATE INDEX idx_generated_documents_template_id ON generated_documents (template_id);
CREATE INDEX idx_audit_logs_company_created_at ON audit_logs (company_id, created_at);
CREATE INDEX idx_batch_jobs_company_status ON batch_jobs (company_id, status);
CREATE INDEX idx_batch_jobs_status_locked_at ON batch_jobs (status, locked_at);

-- MVP roles (PRD §16)
INSERT INTO roles (id, code, name, created_at) VALUES
    ('11111111-1111-1111-1111-111111111001', 'ADMIN', 'Administrateur', NOW()),
    ('11111111-1111-1111-1111-111111111002', 'EDITOR', 'Éditeur', NOW()),
    ('11111111-1111-1111-1111-111111111003', 'USER', 'Utilisateur', NOW()),
    ('11111111-1111-1111-1111-111111111004', 'VIEWER', 'Lecteur', NOW());