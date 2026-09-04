-- Phase 14: batch items + job artifact columns (PRD §39–§41, §60)

ALTER TABLE batch_jobs
    ADD COLUMN csv_storage_key VARCHAR(500),
    ADD COLUMN zip_storage_key VARCHAR(500),
    ADD COLUMN error_report_storage_key VARCHAR(500),
    ADD COLUMN column_mapping JSONB,
    ADD COLUMN template_id UUID REFERENCES templates (id);

CREATE TABLE batch_items (
    id                      UUID PRIMARY KEY,
    batch_job_id            UUID NOT NULL REFERENCES batch_jobs (id) ON DELETE CASCADE,
    row_number              INTEGER NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    error_message           VARCHAR(2000),
    generated_document_id   UUID REFERENCES generated_documents (id),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_batch_items_job_row UNIQUE (batch_job_id, row_number)
);

CREATE INDEX idx_batch_items_job_status ON batch_items (batch_job_id, status);
