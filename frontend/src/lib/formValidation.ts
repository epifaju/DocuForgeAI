/** Tiny helpers for CreatePanel-style forms (avoid browser-locale HTML5 bubbles). */

export function isBlank(value: string | null | undefined): boolean {
  return value == null || value.trim() === "";
}

export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

export const TEMPLATE_CODE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_.-]{0,99}$/;
