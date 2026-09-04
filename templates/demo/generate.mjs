#!/usr/bin/env node
/**
 * Build demo OOXML (.docx) templates with {{placeholders}}.
 * Usage: node templates/demo/generate.mjs
 * Requires jszip (cd tests/e2e && npm install).
 */
import { createWriteStream, existsSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { pipeline } from "node:stream/promises";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, "../..");
const require = createRequire(join(root, "tests/e2e/package.json"));

let JSZip;
try {
  JSZip = require("jszip");
} catch {
  const alt = join(root, "tests/e2e/node_modules/jszip");
  if (!existsSync(alt)) {
    throw new Error("jszip introuvable. Exécutez: cd tests/e2e && npm install");
  }
  JSZip = require(alt);
}

function paragraphXml(text) {
  const escaped = text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
  return `<w:p><w:r><w:t xml:space="preserve">${escaped}</w:t></w:r></w:p>`;
}

async function writeDocx(filename, paragraphs) {
  const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    ${paragraphs.map(paragraphXml).join("\n    ")}
    <w:sectPr/>
  </w:body>
</w:document>`;

  const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`;

  const rels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`;

  const zip = new JSZip();
  zip.file("[Content_Types].xml", contentTypes);
  zip.folder("_rels").file(".rels", rels);
  zip.folder("word").file("document.xml", documentXml);

  const outPath = join(__dirname, filename);
  const buffer = await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" });
  await pipeline(
    async function* () {
      yield buffer;
    },
    createWriteStream(outPath),
  );
  console.log(`Wrote ${outPath} (${buffer.length} bytes)`);
}

await writeDocx("lettre-simple.docx", [
  "Objet : Courrier a l'attention de {{client.firstName}} {{client.lastName}}",
  "",
  "Madame, Monsieur {{client.lastName}},",
  "",
  "Nous vous contactons concernant votre dossier.",
  "Email de contact : {{client.email}}",
  "",
  "Cordialement,",
  "L'equipe DocuForge",
]);

await writeDocx("facture.docx", [
  "FACTURE {{invoice.number}}",
  "Date : {{invoice.date}}",
  "",
  "Client : {{client.firstName}} {{client.lastName}}",
  "Email : {{client.email}}",
  "",
  "Montant TTC : {{invoice.total}} EUR",
  "",
  "Merci de votre confiance.",
]);
