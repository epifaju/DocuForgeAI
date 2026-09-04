/**
 * Build a minimal OOXML (.docx) with {{placeholders}} for E2E seeding.
 */
import { createWriteStream } from "node:fs";
import { mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { pipeline } from "node:stream/promises";
import JSZip from "jszip";

const __dirname = dirname(fileURLToPath(import.meta.url));
const outPath = join(__dirname, "e2e-template.docx");

const paragraphs = [
  "DocuForge E2E — Hello {{client.firstName}}",
  "Email: {{client.email}}",
  "Total: {{invoice.total}}",
];

function paragraphXml(text) {
  const escaped = text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
  return `<w:p><w:r><w:t xml:space="preserve">${escaped}</w:t></w:r></w:p>`;
}

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

await mkdir(__dirname, { recursive: true });
const buffer = await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" });
await pipeline(
  async function* () {
    yield buffer;
  },
  createWriteStream(outPath),
);

console.log(`Wrote ${outPath} (${buffer.length} bytes)`);
