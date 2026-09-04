-- Phase 15: email deliveries (PRD §42–§43, §46)

CREATE TABLE email_deliveries (
    id              UUID PRIMARY KEY,
    company_id      UUID NOT NULL REFERENCES companies (id),
    document_id     UUID NOT NULL REFERENCES generated_documents (id),
    recipient       VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    attachment_format VARCHAR(20) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    error_message   VARCHAR(2000),
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_email_deliveries_company_created_at ON email_deliveries (company_id, created_at);
CREATE INDEX idx_email_deliveries_document_id ON email_deliveries (document_id);
