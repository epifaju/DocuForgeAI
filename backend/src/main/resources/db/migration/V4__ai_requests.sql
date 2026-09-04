-- Phase 13: AI request metadata (PRD §54) — no full LLM payloads

CREATE TABLE ai_requests (
    id              UUID PRIMARY KEY,
    company_id      UUID NOT NULL REFERENCES companies (id),
    user_id         UUID REFERENCES users (id),
    provider        VARCHAR(50) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    operation       VARCHAR(50) NOT NULL,
    prompt_version  VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    duration_ms     BIGINT,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ai_requests_company_created_at ON ai_requests (company_id, created_at);
