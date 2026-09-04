import { apiJson } from "./client";

export interface AiStatus {
  enabled: boolean;
  provider: string;
  model: string;
}

export interface AiAssistResult {
  result: string;
  operation: string;
  provider: string;
  model: string;
  promptVersion: string;
  durationMs: number;
}

export function getAiStatus(token: string) {
  return apiJson<AiStatus>("/api/v1/ai/status", { token });
}

export function aiRewrite(token: string, text: string, instruction?: string) {
  return apiJson<AiAssistResult>("/api/v1/ai/rewrite", {
    method: "POST",
    token,
    body: JSON.stringify({ text, instruction }),
  });
}

export function aiFormalize(token: string, text: string) {
  return apiJson<AiAssistResult>("/api/v1/ai/formalize", {
    method: "POST",
    token,
    body: JSON.stringify({ text }),
  });
}

export function aiSummarize(token: string, text: string) {
  return apiJson<AiAssistResult>("/api/v1/ai/summarize", {
    method: "POST",
    token,
    body: JSON.stringify({ text }),
  });
}

export function aiGenerate(token: string, instruction: string, context?: string, text?: string) {
  return apiJson<AiAssistResult>("/api/v1/ai/generate", {
    method: "POST",
    token,
    body: JSON.stringify({ text, instruction, context }),
  });
}
