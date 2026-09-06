package ai.docuforge.businesspack.demo;

import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * Builds the official fictional demo pack {@code com.docuforge.pack.artisan-demo} (PRD §§161–164).
 * All content is synthetic — no real personal data.
 */
public final class ArtisanDemoPackFactory {

    public static final String PACK_ID = "com.docuforge.pack.artisan-demo";
    public static final String PACK_VERSION = "1.0.0";
    public static final String PACK_SLUG = "artisan-demo";
    public static final String RECOMMENDED_FILENAME = "docuforge-pack-artisan-demo-1.0.0.zip";

    private ArtisanDemoPackFactory() {
    }

    public static byte[] buildZip(PackChecksumValidator checksumValidator) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();

        byte[] devisDocx = docx(
                "DEVIS ARTISAN (DEMO)",
                """
                        Entreprise : {{company.name}}
                        Adresse entreprise : {{company.address}}
                        Client : {{client.name}}
                        Adresse client : {{client.address}}
                        Reference devis : {{quote.reference}}
                        Date : {{quote.date}}
                        Validite : {{quote.validUntil}}
                        Travaux : {{work.description}}
                        Sous-total : {{pricing.subtotal}}
                        TVA : {{pricing.vat}}
                        Total : {{pricing.total}}
                        """
        );
        byte[] interventionDocx = docx(
                "FICHE INTERVENTION (DEMO)",
                """
                        Entreprise : {{company.name}}
                        Client : {{client.name}}
                        Reference : {{intervention.reference}}
                        Date : {{intervention.date}}
                        Lieu : {{intervention.location}}
                        Description : {{intervention.description}}
                        Technicien : {{technician.name}}
                        """
        );
        byte[] certificateDocx = docx(
                "ATTESTATION DE FIN DE TRAVAUX (DEMO — DOCUMENT PRIVE)",
                """
                        Document de demonstration DocuForge — ne constitue pas un acte officiel.
                        Entreprise : {{company.name}}
                        Client : {{client.name}}
                        Travaux : {{work.description}}
                        Lieu : {{work.location}}
                        Date d'achevement : {{work.completionDate}}
                        Emis par : {{issuer.name}}
                        """
        );

        files.put("templates/artisan-devis.docx", devisDocx);
        files.put("templates/artisan-intervention.docx", interventionDocx);
        files.put("templates/artisan-completion-certificate.docx", certificateDocx);

        files.put("metadata/artisan-devis.json", utf8(METADATA_DEVIS));
        files.put("metadata/artisan-intervention.json", utf8(METADATA_INTERVENTION));
        files.put("metadata/artisan-completion-certificate.json", utf8(METADATA_CERTIFICATE));

        files.put("prompts/work-description.txt", utf8(PROMPT_WORK));
        files.put("prompts/intervention-notes.txt", utf8(PROMPT_INTERVENTION));

        files.put("samples/artisan-devis.json", utf8(SAMPLE_DEVIS_JSON));
        files.put("samples/artisan-intervention.csv", utf8(SAMPLE_INTERVENTION_CSV));

        byte[] previewPng = minimalPng();
        files.put("previews/artisan-devis.png", previewPng);
        files.put("previews/artisan-intervention.png", previewPng);
        files.put("previews/artisan-completion-certificate.png", previewPng);

        files.put("README.md", utf8(README));
        files.put("CHANGELOG.md", utf8(CHANGELOG));
        files.put("LICENSE.txt", utf8(LICENSE));

        Map<String, String> checksums = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey();
            if ("README.md".equals(path) || "CHANGELOG.md".equals(path) || "LICENSE.txt".equals(path)) {
                continue;
            }
            checksums.put(path, checksumValidator.digestPrefixed(entry.getValue()));
        }

        String manifest = MANIFEST_TEMPLATE.formatted(
                checksums.get("templates/artisan-devis.docx"),
                checksums.get("metadata/artisan-devis.json"),
                checksums.get("previews/artisan-devis.png"),
                checksums.get("templates/artisan-intervention.docx"),
                checksums.get("metadata/artisan-intervention.json"),
                checksums.get("previews/artisan-intervention.png"),
                checksums.get("templates/artisan-completion-certificate.docx"),
                checksums.get("metadata/artisan-completion-certificate.json"),
                checksums.get("previews/artisan-completion-certificate.png"),
                checksums.get("prompts/work-description.txt"),
                checksums.get("prompts/intervention-notes.txt"),
                checksums.get("samples/artisan-devis.json"),
                checksums.get("samples/artisan-intervention.csv")
        );
        files.put("manifest.json", utf8(manifest));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                put(out, entry.getKey(), entry.getValue());
            }
        }
        return bos.toByteArray();
    }

    private static void put(ZipArchiveOutputStream out, String name, byte[] data) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        out.putArchiveEntry(entry);
        out.write(data);
        out.closeArchiveEntry();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] docx(String title, String body) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph titlePara = document.createParagraph();
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setBold(true);
            titleRun.setText(title);
            for (String line : body.strip().split("\n")) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line.strip());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    /** Minimal valid 1x1 PNG (public domain style fixture). */
    private static byte[] minimalPng() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
                (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8,
                (byte) 0xCF, (byte) 0xC0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, (byte) 0xFE, (byte) 0xD4,
                (byte) 0xEF, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }

    private static final String MANIFEST_TEMPLATE = """
            {
              "schemaVersion": "DBPF-1",
              "id": "com.docuforge.pack.artisan-demo",
              "name": "Pack Artisan Demo",
              "slug": "artisan-demo",
              "version": "1.0.0",
              "type": "OFFICIAL",
              "description": "Pack de demonstration DocuForge pour artisans (donnees fictives uniquement).",
              "publisher": {
                "id": "docuforge",
                "name": "DocuForge AI"
              },
              "compatibility": {
                "minimumDocuForgeVersion": "0.1.0"
              },
              "locales": ["fr-FR"],
              "defaultLocale": "fr-FR",
              "categories": ["ARTISAN", "COMMERCIAL"],
              "tags": ["artisan", "devis", "intervention", "demo"],
              "templates": [
                {
                  "code": "ARTISAN_DEVIS",
                  "name": "Devis Artisan",
                  "version": "1.0.0",
                  "templateFile": "templates/artisan-devis.docx",
                  "metadataFile": "metadata/artisan-devis.json",
                  "previewFile": "previews/artisan-devis.png",
                  "enabledByDefault": true
                },
                {
                  "code": "ARTISAN_INTERVENTION",
                  "name": "Fiche Intervention",
                  "version": "1.0.0",
                  "templateFile": "templates/artisan-intervention.docx",
                  "metadataFile": "metadata/artisan-intervention.json",
                  "previewFile": "previews/artisan-intervention.png",
                  "enabledByDefault": true
                },
                {
                  "code": "ARTISAN_COMPLETION_CERTIFICATE",
                  "name": "Attestation de fin de travaux (demo)",
                  "version": "1.0.0",
                  "templateFile": "templates/artisan-completion-certificate.docx",
                  "metadataFile": "metadata/artisan-completion-certificate.json",
                  "previewFile": "previews/artisan-completion-certificate.png",
                  "enabledByDefault": true
                }
              ],
              "prompts": [
                {
                  "code": "ARTISAN_WORK_DESCRIPTION",
                  "version": "1.0.0",
                  "file": "prompts/work-description.txt"
                },
                {
                  "code": "ARTISAN_INTERVENTION_NOTES",
                  "version": "1.0.0",
                  "file": "prompts/intervention-notes.txt"
                }
              ],
              "samples": [
                {
                  "code": "ARTISAN_DEVIS_SAMPLE",
                  "type": "JSON",
                  "file": "samples/artisan-devis.json",
                  "templateCode": "ARTISAN_DEVIS"
                },
                {
                  "code": "ARTISAN_INTERVENTION_SAMPLE",
                  "type": "CSV",
                  "file": "samples/artisan-intervention.csv",
                  "templateCode": "ARTISAN_INTERVENTION"
                }
              ],
              "checksums": {
                "templates/artisan-devis.docx": "%s",
                "metadata/artisan-devis.json": "%s",
                "previews/artisan-devis.png": "%s",
                "templates/artisan-intervention.docx": "%s",
                "metadata/artisan-intervention.json": "%s",
                "previews/artisan-intervention.png": "%s",
                "templates/artisan-completion-certificate.docx": "%s",
                "metadata/artisan-completion-certificate.json": "%s",
                "previews/artisan-completion-certificate.png": "%s",
                "prompts/work-description.txt": "%s",
                "prompts/intervention-notes.txt": "%s",
                "samples/artisan-devis.json": "%s",
                "samples/artisan-intervention.csv": "%s"
              }
            }
            """;

    private static final String METADATA_DEVIS = """
            {
              "schemaVersion": "DBPF-TEMPLATE-1",
              "code": "ARTISAN_DEVIS",
              "name": "Devis Artisan",
              "description": "Devis professionnel de demonstration pour artisan.",
              "category": "DEVIS",
              "version": "1.0.0",
              "outputFormats": ["DOCX", "PDF"],
              "variables": [
                {"key": "company.name", "label": "Nom de l'entreprise", "type": "TEXT", "required": true, "order": 10},
                {"key": "company.address", "label": "Adresse de l'entreprise", "type": "TEXT", "required": true, "order": 20},
                {"key": "client.name", "label": "Nom du client", "type": "TEXT", "required": true, "order": 30},
                {"key": "client.address", "label": "Adresse du client", "type": "TEXT", "required": true, "order": 40},
                {"key": "quote.reference", "label": "Reference devis", "type": "TEXT", "required": true, "order": 50},
                {"key": "quote.date", "label": "Date du devis", "type": "DATE", "required": true, "order": 60},
                {"key": "quote.validUntil", "label": "Valable jusqu'au", "type": "DATE", "required": true, "order": 70},
                {
                  "key": "work.description",
                  "label": "Description des travaux",
                  "type": "LONG_TEXT",
                  "required": true,
                  "order": 80,
                  "ai": {
                    "enabled": true,
                    "operations": ["FORMALIZE", "EXPAND"],
                    "promptCode": "ARTISAN_WORK_DESCRIPTION"
                  }
                },
                {"key": "pricing.subtotal", "label": "Sous-total HT", "type": "CURRENCY", "required": true, "order": 90},
                {"key": "pricing.vat", "label": "TVA", "type": "CURRENCY", "required": true, "order": 100},
                {"key": "pricing.total", "label": "Total TTC", "type": "CURRENCY", "required": true, "order": 110}
              ]
            }
            """;

    private static final String METADATA_INTERVENTION = """
            {
              "schemaVersion": "DBPF-TEMPLATE-1",
              "code": "ARTISAN_INTERVENTION",
              "name": "Fiche Intervention",
              "description": "Fiche d'intervention de demonstration.",
              "category": "INTERVENTION",
              "version": "1.0.0",
              "outputFormats": ["DOCX", "PDF"],
              "variables": [
                {"key": "company.name", "label": "Nom de l'entreprise", "type": "TEXT", "required": true, "order": 10},
                {"key": "client.name", "label": "Nom du client", "type": "TEXT", "required": true, "order": 20},
                {"key": "intervention.reference", "label": "Reference intervention", "type": "TEXT", "required": true, "order": 30},
                {"key": "intervention.date", "label": "Date d'intervention", "type": "DATE", "required": true, "order": 40},
                {"key": "intervention.location", "label": "Lieu", "type": "TEXT", "required": true, "order": 50},
                {
                  "key": "intervention.description",
                  "label": "Description",
                  "type": "LONG_TEXT",
                  "required": true,
                  "order": 60,
                  "ai": {
                    "enabled": true,
                    "operations": ["FORMALIZE"],
                    "promptCode": "ARTISAN_INTERVENTION_NOTES"
                  }
                },
                {"key": "technician.name", "label": "Technicien", "type": "TEXT", "required": true, "order": 70}
              ]
            }
            """;

    private static final String METADATA_CERTIFICATE = """
            {
              "schemaVersion": "DBPF-TEMPLATE-1",
              "code": "ARTISAN_COMPLETION_CERTIFICATE",
              "name": "Attestation de fin de travaux (demo)",
              "description": "Document prive de demonstration — ne imite aucune autorite publique.",
              "category": "ATTESTATION",
              "version": "1.0.0",
              "outputFormats": ["DOCX", "PDF"],
              "variables": [
                {"key": "company.name", "label": "Nom de l'entreprise", "type": "TEXT", "required": true, "order": 10},
                {"key": "client.name", "label": "Nom du client", "type": "TEXT", "required": true, "order": 20},
                {"key": "work.description", "label": "Description des travaux", "type": "LONG_TEXT", "required": true, "order": 30},
                {"key": "work.location", "label": "Lieu des travaux", "type": "TEXT", "required": true, "order": 40},
                {"key": "work.completionDate", "label": "Date d'achevement", "type": "DATE", "required": true, "order": 50},
                {"key": "issuer.name", "label": "Emetteur", "type": "TEXT", "required": true, "order": 60}
              ]
            }
            """;

    private static final String PROMPT_WORK = """
            Tu aides a rediger une description claire et professionnelle de travaux artisanaux.
            Utilise uniquement les faits fournis. N'invente aucun nom, adresse ou montant reel.
            Style : francais professionnel, sentences courtes.
            """;

    private static final String PROMPT_INTERVENTION = """
            Tu reformules des notes d'intervention artisanales en compte-rendu clair.
            Conserve les faits. N'ajoute aucune donnee personnelle inventee.
            """;

    private static final String SAMPLE_DEVIS_JSON = """
            {
              "company.name": "Atelier Demo SARL",
              "company.address": "12 rue Fictive, 75000 Paris",
              "client.name": "Client Demo Martin",
              "client.address": "5 avenue Exemple, 69000 Lyon",
              "quote.reference": "DEV-DEMO-2026-001",
              "quote.date": "2026-09-01",
              "quote.validUntil": "2026-09-30",
              "work.description": "Remplacement fictif de joint de fenetre et retouche peinture.",
              "pricing.subtotal": "450.00",
              "pricing.vat": "90.00",
              "pricing.total": "540.00"
            }
            """;

    private static final String SAMPLE_INTERVENTION_CSV = """
            company.name,client.name,intervention.reference,intervention.date,intervention.location,intervention.description,technician.name
            Atelier Demo SARL,Client Demo Martin,INT-DEMO-001,2026-09-02,5 avenue Exemple Lyon,Controle et reglage fictif d'un volet,Tech Demo Lea
            """;

    private static final String README = """
            # Pack Artisan Demo

            Pack officiel de demonstration DocuForge (`com.docuforge.pack.artisan-demo`).

            Contenu fictif uniquement — aucune donnee personnelle reelle.
            """;

    private static final String CHANGELOG = """
            # Changelog

            ## 1.0.0
            - Version initiale de demonstration (3 templates, 2 prompts, samples JSON/CSV, previews).
            """;

    private static final String LICENSE = """
            Copyright (c) DocuForge AI — demo content.
            Fictional sample data only. Not for production customer data.
            """;
}
