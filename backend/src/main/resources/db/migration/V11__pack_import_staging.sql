-- Phase 9: pack import staging metadata (do not alter V1–V10)
ALTER TABLE pack_import_jobs
    ADD COLUMN staging_storage_key VARCHAR(500),
    ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX idx_pack_import_expires_at ON pack_import_jobs (expires_at)
    WHERE expires_at IS NOT NULL AND status NOT IN ('INSTALLED', 'EXPIRED');
