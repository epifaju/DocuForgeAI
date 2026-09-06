-- Phase 2 — Business Pack core domain (PRD §§45–48, §58)
-- Adaptation: organization_id → company_id (FK companies, nullable for global OFFICIAL catalog rows)

CREATE TABLE business_packs (
    id                  UUID PRIMARY KEY,
    company_id          UUID REFERENCES companies (id),
    pack_key            VARCHAR(255) NOT NULL,
    slug                VARCHAR(120) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    pack_type           VARCHAR(30) NOT NULL,
    publisher_id        VARCHAR(255),
    publisher_name      VARCHAR(255),
    status              VARCHAR(30) NOT NULL,
    current_version_id  UUID,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

-- Per-tenant uniqueness when company_id is set
CREATE UNIQUE INDEX uq_business_packs_company_pack_key
    ON business_packs (company_id, pack_key)
    WHERE company_id IS NOT NULL;

-- Global uniqueness for catalog rows (company_id IS NULL)
CREATE UNIQUE INDEX uq_business_packs_global_pack_key
    ON business_packs (pack_key)
    WHERE company_id IS NULL;

CREATE INDEX idx_business_packs_pack_key ON business_packs (pack_key);
CREATE INDEX idx_business_packs_status ON business_packs (status);
CREATE INDEX idx_business_packs_company_id ON business_packs (company_id);

CREATE TABLE business_pack_versions (
    id                          UUID PRIMARY KEY,
    business_pack_id            UUID NOT NULL,
    version                     VARCHAR(50) NOT NULL,
    schema_version              VARCHAR(30) NOT NULL,
    manifest                    JSONB NOT NULL,
    minimum_docuforge_version   VARCHAR(50),
    maximum_docuforge_version   VARCHAR(50),
    archive_checksum            VARCHAR(100),
    status                      VARCHAR(30) NOT NULL,
    installed_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_business_pack_versions_pack
        FOREIGN KEY (business_pack_id)
        REFERENCES business_packs (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_business_pack_version
        UNIQUE (business_pack_id, version)
);

CREATE INDEX idx_business_pack_versions_pack
    ON business_pack_versions (business_pack_id);

CREATE INDEX idx_business_pack_versions_status
    ON business_pack_versions (status);

ALTER TABLE business_packs
    ADD CONSTRAINT fk_business_packs_current_version
        FOREIGN KEY (current_version_id)
        REFERENCES business_pack_versions (id)
        ON DELETE RESTRICT;
