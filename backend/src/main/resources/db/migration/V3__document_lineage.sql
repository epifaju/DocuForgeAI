-- Phase 12: document lineage / immutability (PRD §28)
-- Lineage on generated_documents (PRD names document_versions without column schema).

ALTER TABLE generated_documents
    ADD COLUMN root_document_id UUID,
    ADD COLUMN parent_document_id UUID REFERENCES generated_documents (id),
    ADD COLUMN document_version_number INTEGER;

UPDATE generated_documents
SET root_document_id = id,
    document_version_number = 1
WHERE root_document_id IS NULL;

ALTER TABLE generated_documents
    ALTER COLUMN root_document_id SET NOT NULL,
    ALTER COLUMN document_version_number SET NOT NULL;

ALTER TABLE generated_documents
    ADD CONSTRAINT uq_generated_documents_root_version
        UNIQUE (root_document_id, document_version_number);

CREATE INDEX idx_generated_documents_root ON generated_documents (root_document_id);
CREATE INDEX idx_generated_documents_parent ON generated_documents (parent_document_id);
