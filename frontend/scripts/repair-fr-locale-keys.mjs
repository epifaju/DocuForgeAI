/**
 * Repair FR locale keys / interpolations corrupted by blanket accent replacement.
 * Run: node scripts/repair-fr-locale-keys.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const file = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../src/i18n/locales/fr.json",
);

const KEY_FIXES = {
  colRéférence: "colReference",
  schémaLoading: "schemaLoading",
  schémaMissing: "schemaMissing",
  acceptéd: "accepted",
  détail: "detail",
  batchDétail: "batchDetail",
  rôles: "roles",
  colRôles: "colRoles",
  détailTitle: "detailTitle",
  détailDescription: "detailDescription",
  uninstalléd: "uninstalled",
  exportéd: "exported",
  schémaVersion: "schemaVersion",
  installédAt: "installedAt",
  duplicateSuccèss: "duplicateSuccess",
  détectéd: "detected",
  rétentionDays: "retentionDays",
  rôlesRequired: "rolesRequired",
};

function renameKeys(node) {
  if (Array.isArray(node)) return node.map(renameKeys);
  if (!node || typeof node !== "object") return node;
  const out = {};
  for (const [k, v] of Object.entries(node)) {
    const nk = KEY_FIXES[k] ?? k;
    out[nk] = renameKeys(v);
  }
  return out;
}

function fixInterpolations(node) {
  if (typeof node === "string") {
    return node.replace(/\{\{référence\}\}/g, "{{reference}}");
  }
  if (Array.isArray(node)) return node.map(fixInterpolations);
  if (!node || typeof node !== "object") return node;
  const out = {};
  for (const [k, v] of Object.entries(node)) out[k] = fixInterpolations(v);
  return out;
}

const raw = JSON.parse(fs.readFileSync(file, "utf8"));
const fixed = fixInterpolations(renameKeys(raw));
fs.writeFileSync(file, `${JSON.stringify(fixed, null, 2)}\n`, "utf8");

// verify
const text = fs.readFileSync(file, "utf8");
const data = JSON.parse(text);
const bad = [];
function walk(o, p = "") {
  if (!o || typeof o !== "object") return;
  for (const [k, v] of Object.entries(o)) {
    if (/[àâäéèêëïîôùûüçœÀÂÄÉÈÊËÏÎÔÙÛÜÇŒ]/.test(k)) bad.push(`${p}.${k}`);
    if (typeof v === "string" && /\{\{[^}]*[àâäéèêëïîôùûüç]/.test(v)) {
      bad.push(`interp ${p}.${k}`);
    }
    walk(v, p ? `${p}.${k}` : k);
  }
}
walk(data);
console.log("documents.colReference =", data.documents?.colReference);
console.log("batchDetail exists =", !!data.batchDetail);
console.log("remaining bad:", bad.length ? bad.join(", ") : "none");
