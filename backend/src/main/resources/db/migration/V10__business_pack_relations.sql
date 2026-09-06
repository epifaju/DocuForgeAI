-- Phase 3 — Pack relations + template provenance (PRD §§49–56, §58)
-- Adaptation: organization_id → company_id

-- ---------------------------------------------------------------------------
-- Template provenance
-- ---------------------------------------------------------------------------
ALTER TABLE templates
    ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'USER',
    ADD COLUMN source_pack_id UUID;

ALTER TABLE templates
    ADD CONSTRAINT fk_templates_source_pack
        FOREIGN KEY (source_pack_id)
        REFERENCES business_packs (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_templates_origin ON templates (origin);
CREATE INDEX idx_templates_source_pack ON templates (source_pack_id);

ALTER TABLE template_versions
    ADD COLUMN source_pack_version_id UUID,
    ADD COLUMN source_template_code VARCHAR(150);

ALTER TABLE template_versions
    ADD CONSTRAINT fk_template_versions_source_pack_version
        FOREIGN KEY (source_pack_version_id)
        REFERENCES business_pack_versions (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_template_versions_source_pack_version
    ON template_versions (source_pack_version_id);

-- ---------------------------------------------------------------------------
-- business_pack_templates
-- ---------------------------------------------------------------------------
CREATE TABLE business_pack_templates (
    id                          UUID PRIMARY KEY,
    business_pack_version_id    UUID NOT NULL,
    template_id                 UUID NOT NULL,
    template_version_id         UUID NOT NULL,
    template_code               VARCHAR(150) NOT NULL,
    enabled_by_default          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_bpt_pack_version
        FOREIGN KEY (business_pack_version_id)
        REFERENCES business_pack_versions (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_bpt_template
        FOREIGN KEY (template_id)
        REFERENCES templates (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_bpt_template_version
        FOREIGN KEY (template_version_id)
        REFERENCES template_versions (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_business_pack_templates_version_code
        UNIQUE (business_pack_version_id, template_code)
);

CREATE INDEX idx_pack_template_version
    ON business_pack_templates (business_pack_version_id);

CREATE INDEX idx_business_pack_templates_template
    ON business_pack_templates (template_id);

-- ---------------------------------------------------------------------------
-- business_pack_prompts
-- ---------------------------------------------------------------------------
CREATE TABLE business_pack_prompts (
    id                          UUID PRIMARY KEY,
    business_pack_version_id    UUID NOT NULL,
    prompt_code                 VARCHAR(150) NOT NULL,
    prompt_version              VARCHAR(50) NOT NULL,
    content                     TEXT NOT NULL,
    checksum                    VARCHAR(100) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_bpp_pack_version
        FOREIGN KEY (business_pack_version_id)
        REFERENCES business_pack_versions (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_business_pack_prompts_version_code
        UNIQUE (business_pack_version_id, prompt_code, prompt_version)
);

CREATE INDEX idx_business_pack_prompts_version
    ON business_pack_prompts (business_pack_version_id);

-- ---------------------------------------------------------------------------
-- business_pack_files
-- ---------------------------------------------------------------------------
CREATE TABLE business_pack_files (
    id                          UUID PRIMARY KEY,
    business_pack_version_id    UUID NOT NULL,
    logical_path                VARCHAR(1000) NOT NULL,
    file_type                   VARCHAR(50) NOT NULL,
    storage_key                 VARCHAR(1000),
    checksum                    VARCHAR(100) NOT NULL,
    size_bytes                  BIGINT NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_bpf_pack_version
        FOREIGN KEY (business_pack_version_id)
        REFERENCES business_pack_versions (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_business_pack_files_version_path
        UNIQUE (business_pack_version_id, logical_path),
    CONSTRAINT chk_business_pack_files_size_non_negative
        CHECK (size_bytes >= 0)
);

CREATE INDEX idx_business_pack_files_version
    ON business_pack_files (business_pack_version_id);

-- ---------------------------------------------------------------------------
-- business_pack_installations
-- ---------------------------------------------------------------------------
CREATE TABLE business_pack_installations (
    id                          UUID PRIMARY KEY,
    company_id                  UUID,
    business_pack_id            UUID NOT NULL,
    business_pack_version_id    UUID NOT NULL,
    installation_type           VARCHAR(30) NOT NULL,
    installed_by                UUID,
    installed_at                TIMESTAMPTZ NOT NULL,
    disabled_at                 TIMESTAMPTZ,
    uninstalled_at              TIMESTAMPTZ,
    status                      VARCHAR(30) NOT NULL,
    metadata                    JSONB,
    CONSTRAINT fk_bpi_company
        FOREIGN KEY (company_id)
        REFERENCES companies (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_bpi_pack
        FOREIGN KEY (business_pack_id)
        REFERENCES business_packs (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_bpi_pack_version
        FOREIGN KEY (business_pack_version_id)
        REFERENCES business_pack_versions (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_pack_installation_company
    ON business_pack_installations (company_id);

CREATE INDEX idx_business_pack_installations_pack
    ON business_pack_installations (business_pack_id);

CREATE INDEX idx_business_pack_installations_status
    ON business_pack_installations (status);

-- ---------------------------------------------------------------------------
-- pack_import_jobs
-- ---------------------------------------------------------------------------
CREATE TABLE pack_import_jobs (
    id                      UUID PRIMARY KEY,
    company_id              UUID,
    original_filename       VARCHAR(500),
    status                  VARCHAR(30) NOT NULL,
    detected_pack_key       VARCHAR(255),
    detected_version        VARCHAR(50),
    validation_report       JSONB,
    uploaded_by             UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ,
    error_code              VARCHAR(100),
    error_message           TEXT,
    locked_at               TIMESTAMPTZ,
    locked_by               VARCHAR(100),
    CONSTRAINT fk_pack_import_jobs_company
        FOREIGN KEY (company_id)
        REFERENCES companies (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_pack_import_status ON pack_import_jobs (status);
CREATE INDEX idx_pack_import_company ON pack_import_jobs (company_id);
CREATE INDEX idx_pack_import_locked_at ON pack_import_jobs (status, locked_at);
