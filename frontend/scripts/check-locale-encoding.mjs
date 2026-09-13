/**
 * Guardrail: locale JSON must be UTF-8 (no BOM) and contain real accented glyphs.
 * Fails if files look ASCII-stripped (0 accent chars) or invalid UTF-8 / BOM.
 *
 * Run: node scripts/check-locale-encoding.mjs
 * Also wired via: npm run check:locales
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const localesDir = path.resolve(__dirname, "../src/i18n/locales");

const ACCENT_RE = /[àâäéèêëïîôùûüçœÀÂÄÉÈÊËÏÎÔÙÛÜÇŒãõáíóúÃÕÁÍÓÚ]/g;

/** Stems that must not appear unaccented in FR copy (ASCII regression). */
const FR_FORBIDDEN = [
  /\bactivite\b/i,
  /\bgeneres?\b/i,
  /\bTermine\b/,
  /\bSucces\b/i,
  /\bEchecs?\b/,
  /\bParametres\b/i,
  /\bDeconnexion\b/i,
  /\bCreer\b/,
  /\bsociete\b/i,
  /\bdonnees\b/i,
  /\bmetier\b/i,
  /\bFrancais\b/,
  /\bPrecedent\b/,
  /\bA traiter\b/,
  /\bdeposez\b/i,
];

function isValidUtf8(buf) {
  try {
    const decoded = new TextDecoder("utf-8", { fatal: true }).decode(buf);
    return decoded;
  } catch {
    return null;
  }
}

function fail(msg) {
  console.error(`check-locale-encoding: ${msg}`);
  process.exitCode = 1;
}

const files = ["fr.json", "pt.json"];
for (const name of files) {
  const filePath = path.join(localesDir, name);
  const buf = fs.readFileSync(filePath);
  if (buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf) {
    fail(`${name}: UTF-8 BOM is not allowed (save as UTF-8 without BOM)`);
    continue;
  }
  const text = isValidUtf8(buf);
  if (text == null) {
    fail(`${name}: not valid UTF-8`);
    continue;
  }
  try {
    JSON.parse(text);
  } catch (err) {
    fail(`${name}: invalid JSON (${err.message})`);
    continue;
  }
  const data = JSON.parse(text);
  const accents = (text.match(ACCENT_RE) || []).length;
  if (accents < 50) {
    fail(`${name}: only ${accents} accented characters — likely ASCII-stripped encoding`);
    continue;
  }

  // Keys and {{interpolation}} names must stay ASCII (accent scripts must not rewrite them).
  const badKeys = [];
  const walk = (o, p = "") => {
    if (!o || typeof o !== "object") return;
    for (const [k, v] of Object.entries(o)) {
      if (ACCENT_RE.test(k)) badKeys.push(`${p}.${k}`);
      ACCENT_RE.lastIndex = 0;
      if (typeof v === "string") {
        const m = v.match(/\{\{([^}]+)\}\}/g);
        if (m) {
          for (const token of m) {
            if (ACCENT_RE.test(token)) badKeys.push(`interp ${p}.${k}: ${token}`);
            ACCENT_RE.lastIndex = 0;
          }
        }
      } else {
        walk(v, p ? `${p}.${k}` : k);
      }
    }
  };
  walk(data);
  if (badKeys.length) {
    fail(`${name}: accented i18n keys/interpolations: ${badKeys.slice(0, 8).join(", ")}`);
    continue;
  }

  if (name === "fr.json") {
    const valueHits = [];
    const scanValues = (o) => {
      if (typeof o === "string") {
        for (const re of FR_FORBIDDEN) {
          if (re.test(o)) valueHits.push(`${re} in "${o.slice(0, 60)}"`);
        }
      } else if (o && typeof o === "object") {
        Object.values(o).forEach(scanValues);
      }
    };
    scanValues(data);
    if (valueHits.length) {
      fail(`${name}: forbidden unaccented stem in values: ${valueHits[0]}`);
    }
  }
  if (!process.exitCode) {
    console.log(`OK ${name} (UTF-8 no BOM, accentChars=${accents})`);
  }
}

if (process.exitCode) {
  process.exit(process.exitCode);
}
