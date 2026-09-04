-- Company-scoped application settings (PRD §46 application_settings, §62 admin/settings)

CREATE TABLE application_settings (
    id             UUID PRIMARY KEY,
    company_id     UUID NOT NULL REFERENCES companies (id),
    setting_key    VARCHAR(100) NOT NULL,
    setting_value  TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_application_settings_company_key UNIQUE (company_id, setting_key)
);

CREATE INDEX idx_application_settings_company_id ON application_settings (company_id);
