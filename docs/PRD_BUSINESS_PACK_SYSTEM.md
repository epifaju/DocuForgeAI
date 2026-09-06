# PRD — DocuForge Business Pack System

**Produit :** DocuForge AI  
**Module :** Business Pack System  
**Version PRD :** 1.1 (optimisée — bibliothèques tranchées §19, §24, §137, §140, §70-71 ; sections 199-207 ajoutées)  
**Format de pack :** DocuForge Business Pack Format v1 — `DBPF-1`  
**Statut :** Spécification d'implémentation  
**Audience :** Cursor / équipe de développement DocuForge AI  
**Priorité :** Haute  
**Dépendance :** moteur de templates DocuForge AI existant

---

# 1. VISION

Créer dans DocuForge AI un système standardisé permettant de distribuer, importer, installer, mettre à jour, désactiver, exporter et exploiter des ensembles cohérents de modèles documentaires métier.

Exemples :

```text
Pack Artisan
Pack RH
Pack Association
Pack Cabinet de conseil
Pack Immobilier
Pack Administration
Pack Formation
Pack Commerce
Pack Garage
```

Un Business Pack ne doit pas être un simple dossier de fichiers DOCX.

Il constitue un package métier versionné comprenant potentiellement :

```text
templates DOCX
+
définition des variables
+
formulaires
+
prompts IA
+
catégories
+
données d'exemple
+
previews
+
assets
+
règles de compatibilité
+
métadonnées
```

---

# 2. OBJECTIF PRODUIT

Permettre le scénario :

```text
Administrateur
      ↓
Packs métier
      ↓
Importer pack-artisan-1.0.0.zip
      ↓
Validation
      ↓
Aperçu
      ↓
Installation
      ↓
10 templates disponibles
      ↓
Utilisateur choisit "Devis Artisan"
      ↓
Formulaire dynamique
      ↓
Génération DOCX/PDF
```

sans développement spécifique pour chaque métier.

---

# 3. PRINCIPE ARCHITECTURAL

Le Business Pack System ne remplace PAS le moteur actuel de templates.

Architecture :

```text
                   DocuForge AI
                       │
        ┌──────────────┴──────────────┐
        │                             │
 Business Pack System          Template Engine
        │                             │
        ├── manifest                  ├── DOCX
        ├── installation              ├── variables
        ├── versioning                ├── generation
        ├── validation                ├── PDF
        ├── prompts                   └── versions
        └── metadata
                │
                ▼
         Template instances
                │
                └──────────────► Template Engine
```

Le pack constitue une **couche de distribution et de configuration** au-dessus du moteur existant.

---

# 4. RÈGLE FONDAMENTALE

Le moteur de génération ne doit pas avoir :

```java
if (pack.equals("ARTISAN")) { ... }
if (pack.equals("RH")) { ... }
if (pack.equals("IMMOBILIER")) { ... }
```

INTERDIT.

Les particularités doivent être exprimées par :

```text
manifest
metadata
variables
configuration
prompts
templates
```

Le moteur doit rester générique.

---

# 5. OBJECTIFS MVP

Le MVP doit permettre :

1. définir DBPF-1 ;
2. importer un ZIP ;
3. analyser le ZIP sans l'installer ;
4. valider sa structure ;
5. valider `manifest.json` ;
6. valider les fichiers ;
7. vérifier les checksums ;
8. valider les templates ;
9. détecter les placeholders ;
10. comparer placeholders et metadata ;
11. afficher un rapport de validation ;
12. prévisualiser le pack ;
13. installer atomiquement le pack ;
14. créer ses templates ;
15. créer les variables ;
16. importer les prompts ;
17. importer les previews ;
18. activer/désactiver un pack ;
19. lister ses templates ;
20. identifier l'origine d'un template ;
21. installer une nouvelle version ;
22. conserver les anciennes versions ;
23. empêcher les régressions historiques ;
24. désinstaller lorsque cela est autorisé ;
25. exporter un pack ;
26. auditer les opérations ;
27. exposer une API REST ;
28. fournir les écrans React ;
29. tester la sécurité des ZIP ;
30. préparer architecturalement une marketplace.

---

# 6. NON-OBJECTIFS MVP

Ne PAS développer maintenant :

```text
marketplace publique
paiements
Stripe
commission créateurs
DRM
licensing online
signature cryptographique PKI obligatoire
remote marketplace repository
auto-update Internet
ratings
reviews
affiliate system
creator payouts
```

Préparer l'architecture sans implémentation prématurée.

---

# 7. TYPES DE PACK

Enum :

```text
OFFICIAL
CUSTOM
THIRD_PARTY
```

### OFFICIAL

Créé et distribué par DocuForge.

### CUSTOM

Créé par l'organisation utilisatrice.

### THIRD_PARTY

Prévu pour future marketplace.

---

# 8. ORIGINE DES TEMPLATES

Ajouter :

```text
SYSTEM
PACK
USER
```

`SYSTEM` :

templates livrés avec DocuForge.

`PACK` :

templates installés via Business Pack.

`USER` :

templates créés/importés manuellement par l'utilisateur.

---

# 9. IDENTIFIANT GLOBAL D'UN PACK

Chaque pack possède un identifiant stable.

Convention :

```text
com.docuforge.pack.artisan
com.docuforge.pack.rh
com.docuforge.pack.association
com.docuforge.pack.consulting
com.docuforge.pack.realestate
```

Pour les organisations :

```text
com.acme.docuforge.pack.custom-sales
```

Regex :

```regex
^[a-z][a-z0-9]*(\.[a-z][a-z0-9-]*){2,}$
```

Cet identifiant ne change jamais entre versions.

---

# 10. VERSIONING

Utiliser Semantic Versioning :

```text
MAJOR.MINOR.PATCH
```

Exemple :

```text
1.0.0
1.0.1
1.1.0
2.0.0
```

### Décision — bibliothèque de comparaison

```text
semver4j (com.vdurmont:semver4j)
```

Comparer `minimumDocuForgeVersion`/`maximumDocuForgeVersion` (§23), détecter un downgrade (§71) ou classer une mise à jour MAJOR/MINOR/PATCH (§93) par découpage manuel de chaînes gère mal les cas limites (suffixes pre-release, tri lexicographique incorrect entre "10.0.0" et "9.0.0"). semver4j fournit directement les opérateurs de comparaison nécessaires à ces trois usages.

---

# 11. SÉMANTIQUE

PATCH :

```text
correction typographique
correction mise en page
correction prompt sans changement d'interface
```

MINOR :

```text
nouveau template
nouvelle variable optionnelle
nouveau prompt
nouvelle preview
```

MAJOR :

```text
suppression template
renommage variable
variable obligatoire supplémentaire
changement incompatible
```

---

# 12. IMMUTABILITÉ

Une version installée est immutable.

INTERDIT :

```text
Pack Artisan 1.0.0
→ modifier directement 1.0.0
```

Il faut publier :

```text
1.0.1
```

---

# 13. DOCUMENTS HISTORIQUES

Un document généré doit conserver :

```text
template_id
template_version_id
pack_id
pack_version_id
```

si applicable.

Exemple :

```text
Document D123

Pack Artisan 1.2.0
Template Devis 2.1.0
```

Une mise à jour vers :

```text
Pack Artisan 1.3.0
```

ne doit jamais changer D123.

---

# 14. FORMAT DU PACKAGE

Extension :

```text
.zip
```

Convention :

```text
docuforge-pack-{slug}-{version}.zip
```

Exemple :

```text
docuforge-pack-artisan-1.0.0.zip
```

---

# 15. STRUCTURE DBPF-1

```text
docuforge-pack-artisan-1.0.0/
│
├── manifest.json
│
├── README.md
│
├── CHANGELOG.md
│
├── LICENSE.txt
│
├── templates/
│   ├── artisan-devis.docx
│   ├── artisan-facture.docx
│   └── artisan-intervention.docx
│
├── metadata/
│   ├── artisan-devis.json
│   ├── artisan-facture.json
│   └── artisan-intervention.json
│
├── prompts/
│   ├── work-description.txt
│   └── customer-message.txt
│
├── samples/
│   ├── artisan-devis.json
│   └── artisan-batch.csv
│
├── previews/
│   ├── artisan-devis.png
│   └── artisan-facture.png
│
└── assets/
    └── README.md
```

---

# 16. RÈGLE RACINE

Le ZIP doit contenir exactement une racine logique.

Accepter :

```text
manifest.json
templates/...
```

ou :

```text
pack-name/
   manifest.json
   templates/
```

Le parser doit normaliser cette différence.

---

# 17. FICHIERS AUTORISÉS MVP

Whitelist :

```text
.json
.docx
.txt
.md
.csv
.png
.jpg
.jpeg
.webp
```

Refuser :

```text
.exe
.dll
.bat
.cmd
.ps1
.sh
.jar
.class
.js
.py
.php
.com
.scr
.msi
```

Un pack ne doit jamais pouvoir exécuter du code.

---

# 18. LIMITES CONFIGURABLES

Exemple :

```text
PACK_MAX_UPLOAD_SIZE_MB=100
PACK_MAX_UNCOMPRESSED_SIZE_MB=250
PACK_MAX_FILES=500
PACK_MAX_TEMPLATES=100
PACK_MAX_SINGLE_FILE_MB=25
PACK_MAX_COMPRESSION_RATIO=50
```

Toutes ces valeurs doivent être configurables.

---

# 19. PROTECTION ZIP BOMB

Avant extraction complète :

vérifier :

```text
compressed size
uncompressed size
number of entries
compression ratio
```

Refuser les archives dépassant les limites.

Code :

```text
PACK_ARCHIVE_LIMIT_EXCEEDED
```

### Décision — bibliothèque ZIP

```text
Apache Commons Compress (org.apache.commons:commons-compress)
```

Choisi plutôt que `java.util.zip` seul, car il permet une **stratégie en deux passes** alignée sur le pipeline §65 : une passe 1 (scan) lit les en-têtes d'entrées (`ZipArchiveEntry`) sans extraction pour calculer taille compressée/non compressée, nombre d'entrées et ratio de compression avant qu'un seul octet ne soit décompressé sur disque ; la passe 2 (extraction) n'a lieu qu'après validation complète. `java.util.zip.ZipInputStream` ne garantit pas de manière fiable la taille non compressée avant extraction complète selon la présence de data descriptors — Commons Compress expose cette information de façon déterministe via son API `ZipFile` à accès aléatoire.

---

# 20. ZIP SLIP

Pour chaque entrée :

```text
target = destination.resolve(entryName).normalize()
```

Vérifier :

```text
target.startsWith(destination)
```

Refuser :

```text
../../etc/passwd
..\..\file
/absolute/path
C:\...
```

Erreur :

```text
PACK_UNSAFE_PATH
```

---

# 21. SYMLINKS

Ne pas accepter de liens symboliques dans DBPF-1.

---

# 22. MANIFEST

`manifest.json` obligatoire.

Encoding :

```text
UTF-8
```

---

# 23. MANIFEST COMPLET

Exemple :

```json
{
  "schemaVersion": "DBPF-1",

  "id": "com.docuforge.pack.artisan",

  "name": "Pack Artisan",

  "slug": "artisan",

  "version": "1.0.0",

  "type": "OFFICIAL",

  "description": "Modèles documentaires pour artisans et petites entreprises.",

  "publisher": {
    "id": "docuforge",
    "name": "DocuForge AI"
  },

  "compatibility": {
    "minimumDocuForgeVersion": "1.0.0",
    "maximumDocuForgeVersion": null
  },

  "locales": [
    "fr-FR"
  ],

  "defaultLocale": "fr-FR",

  "categories": [
    "ARTISAN",
    "COMMERCIAL"
  ],

  "tags": [
    "artisan",
    "devis",
    "facture",
    "intervention"
  ],

  "templates": [
    {
      "code": "ARTISAN_DEVIS",
      "name": "Devis Artisan",
      "version": "1.0.0",
      "templateFile": "templates/artisan-devis.docx",
      "metadataFile": "metadata/artisan-devis.json",
      "previewFile": "previews/artisan-devis.png",
      "enabledByDefault": true
    }
  ],

  "prompts": [
    {
      "code": "ARTISAN_WORK_DESCRIPTION",
      "version": "1.0.0",
      "file": "prompts/work-description.txt"
    }
  ],

  "samples": [
    {
      "code": "ARTISAN_DEVIS_SAMPLE",
      "type": "JSON",
      "file": "samples/artisan-devis.json",
      "templateCode": "ARTISAN_DEVIS"
    }
  ],

  "checksums": {
    "templates/artisan-devis.docx": "sha256:...",
    "metadata/artisan-devis.json": "sha256:...",
    "prompts/work-description.txt": "sha256:..."
  }
}
```

---

# 24. JSON SCHEMA

Créer :

```text
backend/src/main/resources/schemas/
docuforge-business-pack-v1.schema.json
```

Valider le manifest contre JSON Schema avant toute logique métier.

### Décision — bibliothèque de validation

```text
networknt/json-schema-validator (com.networknt:json-schema-validator)
```

Bibliothèque Java la plus activement maintenue pour JSON Schema (support Draft 2020-12), s'intègre directement avec Jackson déjà utilisé pour le `PackManifestParser` (§64) : le même `ObjectMapper` sert à parser le manifest et à le valider contre le schéma, avant toute désérialisation en DTO Java — respecte l'ordre du pipeline §65 ("JSON schema validation" avant "Manifest semantic validation").

---

# 25. CHAMPS MANIFEST OBLIGATOIRES

Obligatoires :

```text
schemaVersion
id
name
slug
version
type
description
publisher
compatibility
locales
defaultLocale
templates
checksums
```

---

# 26. SLUG

Regex :

```regex
^[a-z0-9]+(?:-[a-z0-9]+)*$
```

Exemple valide :

```text
cabinet-conseil
```

---

# 27. LOCALE

Format BCP 47.

Exemples :

```text
fr-FR
pt-PT
en-US
```

`defaultLocale` doit appartenir à `locales`.

---

# 28. TEMPLATE METADATA

Exemple :

```json
{
  "schemaVersion": "DBPF-TEMPLATE-1",

  "code": "ARTISAN_DEVIS",

  "name": "Devis Artisan",

  "description": "Devis professionnel pour artisan.",

  "category": "DEVIS",

  "version": "1.0.0",

  "outputFormats": [
    "DOCX",
    "PDF"
  ],

  "variables": [
    {
      "key": "company.name",
      "label": "Nom de l'entreprise",
      "type": "TEXT",
      "required": true,
      "order": 10
    },

    {
      "key": "client.name",
      "label": "Nom du client",
      "type": "TEXT",
      "required": true,
      "order": 20
    },

    {
      "key": "quote.date",
      "label": "Date du devis",
      "type": "DATE",
      "required": true,
      "order": 30
    },

    {
      "key": "work.description",
      "label": "Description des travaux",
      "type": "LONG_TEXT",
      "required": true,
      "order": 40,
      "ai": {
        "enabled": true,
        "operations": [
          "FORMALIZE",
          "EXPAND"
        ],
        "promptCode": "ARTISAN_WORK_DESCRIPTION"
      }
    }
  ]
}
```

---

# 29. VARIABLE TYPES

Supporter :

```text
TEXT
LONG_TEXT
INTEGER
DECIMAL
CURRENCY
DATE
DATETIME
BOOLEAN
EMAIL
PHONE
SELECT
MULTISELECT
```

Future :

```text
IMAGE
SIGNATURE
TABLE
REPEATING_SECTION
FILE
```

Ne pas implémenter les types futurs dans cette fonctionnalité s'ils n'existent pas déjà dans DocuForge.

---

# 30. VARIABLE KEY

Regex :

```regex
^[a-z][a-zA-Z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$
```

Exemples :

```text
client.name
client.address.city
quote.reference
employee.firstName
```

---

# 31. OPTIONS SELECT

Exemple :

```json
{
  "key": "contract.type",
  "label": "Type de contrat",
  "type": "SELECT",
  "required": true,
  "options": [
    {
      "value": "CDI",
      "label": "CDI"
    },
    {
      "value": "CDD",
      "label": "CDD"
    }
  ]
}
```

---

# 32. VALIDATIONS DE VARIABLE

Prévoir :

```json
{
  "validation": {
    "minLength": 2,
    "maxLength": 150,
    "pattern": null,
    "minimum": null,
    "maximum": null
  }
}
```

Le backend reste autoritaire.

Le frontend reproduit les règles pour UX.

---

# 33. PLACEHOLDERS DOCX

Syntaxe officielle :

```text
{{company.name}}
{{client.name}}
{{quote.reference}}
```

Le parser doit extraire les placeholders.

---

# 34. COHÉRENCE PLACEHOLDERS/METADATA

Pour chaque template :

```text
DOCX placeholders
        ↕
metadata variables
```

Cas A :

DOCX :

```text
{{client.name}}
```

Metadata absent.

Erreur :

```text
PACK_TEMPLATE_UNDECLARED_VARIABLE
```

Cas B :

metadata :

```text
client.email
```

mais jamais utilisé dans DOCX.

Warning :

```text
PACK_TEMPLATE_UNUSED_VARIABLE
```

Ne pas nécessairement bloquer l'installation.

---

# 35. VARIABLES RÉSERVÉES

Prévoir namespace :

```text
system.*
```

Exemples :

```text
system.generationDate
system.documentReference
```

Un pack ne peut pas redéfinir arbitrairement une variable système.

---

# 36. PROMPTS IA

Les prompts sont déclarés dans le manifest.

Exemple :

```json
{
  "code": "CONSULTING_EXECUTIVE_SUMMARY",
  "version": "1.0.0",
  "file": "prompts/executive-summary.txt"
}
```

---

# 37. PROMPT STORAGE

Lors de l'installation :

ne pas dépendre du ZIP original pour l'exécution.

Importer le contenu dans le système de stockage/versioning DocuForge.

Conserver :

```text
prompt code
version
content
checksum
pack version
```

---

# 38. PROMPT SECURITY

Un prompt ne doit jamais pouvoir :

```text
contourner les permissions
accéder directement à PostgreSQL
exécuter du code
récupérer les secrets
```

Le prompt n'est qu'une donnée transmise à `AIProvider`.

---

# 39. SAMPLE DATA

Formats MVP :

```text
JSON
CSV
```

Les données doivent être explicitement présentées comme exemples.

Les packs officiels ne doivent contenir aucune donnée personnelle réelle.

---

# 40. PREVIEWS

Formats :

```text
PNG
JPEG
WEBP
```

Taille maximale configurable.

La preview est purement illustrative.

Elle ne doit jamais être utilisée comme source du document.

---

# 41. CHECKSUM

Utiliser :

```text
SHA-256
```

Format :

```text
sha256:<64 hex chars>
```

Tous les fichiers fonctionnels doivent être couverts :

```text
templates
metadata
prompts
```

Les previews et samples peuvent également l'être.

---

# 42. VÉRIFICATION CHECKSUM

Pipeline :

```text
Read manifest
     ↓
Resolve declared file
     ↓
Compute SHA-256
     ↓
Compare
```

Mismatch :

```text
PACK_CHECKSUM_MISMATCH
```

Installation bloquée.

---

# 43. SIGNATURE FUTURE

Préparer sans implémenter :

```text
signature
publisher certificate
trusted publisher
```

DBPF-2 pourra introduire la signature cryptographique.

---

# 44. MODÈLE DE DONNÉES

Ajouter les tables suivantes :

```text
business_packs
business_pack_versions
business_pack_templates
business_pack_prompts
business_pack_installations
business_pack_files
pack_import_jobs
```

Réutiliser les tables existantes :

```text
templates
template_versions
template_variables
```

---

# 45. BUSINESS_PACKS

SQL conceptuel PostgreSQL :

```sql
CREATE TABLE business_packs (
    id UUID PRIMARY KEY,

    organization_id UUID NULL,

    pack_key VARCHAR(255) NOT NULL,
    slug VARCHAR(120) NOT NULL,

    name VARCHAR(255) NOT NULL,
    description TEXT,

    pack_type VARCHAR(30) NOT NULL,

    publisher_id VARCHAR(255),
    publisher_name VARCHAR(255),

    status VARCHAR(30) NOT NULL,

    current_version_id UUID NULL,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_business_pack_key_org
        UNIQUE (organization_id, pack_key)
);
```

---

# 46. PACK STATUS

```text
INSTALLED
DISABLED
UPDATE_AVAILABLE
BROKEN
UNINSTALLED
```

`UNINSTALLED` peut être conservé pour audit plutôt que supprimer physiquement la ligne.

---

# 47. BUSINESS_PACK_VERSIONS

```sql
CREATE TABLE business_pack_versions (
    id UUID PRIMARY KEY,

    business_pack_id UUID NOT NULL,

    version VARCHAR(50) NOT NULL,
    schema_version VARCHAR(30) NOT NULL,

    manifest JSONB NOT NULL,

    minimum_docuforge_version VARCHAR(50),
    maximum_docuforge_version VARCHAR(50),

    archive_checksum VARCHAR(100),

    status VARCHAR(30) NOT NULL,

    installed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_pack_version_pack
        FOREIGN KEY (business_pack_id)
        REFERENCES business_packs(id),

    CONSTRAINT uq_business_pack_version
        UNIQUE (business_pack_id, version)
);
```

---

# 48. PACK VERSION STATUS

```text
VALIDATING
VALID
INSTALLING
INSTALLED
SUPERSEDED
FAILED
```

---

# 49. BUSINESS_PACK_TEMPLATES

```sql
CREATE TABLE business_pack_templates (
    id UUID PRIMARY KEY,

    business_pack_version_id UUID NOT NULL,
    template_id UUID NOT NULL,
    template_version_id UUID NOT NULL,

    template_code VARCHAR(150) NOT NULL,

    enabled_by_default BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE (business_pack_version_id, template_code)
);
```

---

# 50. BUSINESS_PACK_PROMPTS

```sql
CREATE TABLE business_pack_prompts (
    id UUID PRIMARY KEY,

    business_pack_version_id UUID NOT NULL,

    prompt_code VARCHAR(150) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,

    content TEXT NOT NULL,

    checksum VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE (
        business_pack_version_id,
        prompt_code,
        prompt_version
    )
);
```

---

# 51. BUSINESS_PACK_FILES

Permet audit/intégrité.

```sql
CREATE TABLE business_pack_files (
    id UUID PRIMARY KEY,

    business_pack_version_id UUID NOT NULL,

    logical_path VARCHAR(1000) NOT NULL,
    file_type VARCHAR(50) NOT NULL,

    storage_key VARCHAR(1000),

    checksum VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE (
        business_pack_version_id,
        logical_path
    )
);
```

---

# 52. INSTALLATIONS

```sql
CREATE TABLE business_pack_installations (
    id UUID PRIMARY KEY,

    organization_id UUID NULL,
    business_pack_id UUID NOT NULL,
    business_pack_version_id UUID NOT NULL,

    installation_type VARCHAR(30) NOT NULL,

    installed_by UUID,
    installed_at TIMESTAMPTZ NOT NULL,

    disabled_at TIMESTAMPTZ,
    uninstalled_at TIMESTAMPTZ,

    status VARCHAR(30) NOT NULL,

    metadata JSONB
);
```

---

# 53. PACK_IMPORT_JOBS

```sql
CREATE TABLE pack_import_jobs (
    id UUID PRIMARY KEY,

    organization_id UUID NULL,

    original_filename VARCHAR(500),

    status VARCHAR(30) NOT NULL,

    detected_pack_key VARCHAR(255),
    detected_version VARCHAR(50),

    validation_report JSONB,

    uploaded_by UUID,

    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,

    error_code VARCHAR(100),
    error_message TEXT,

    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(100)
);
```

### Décision — mécanisme de traitement

Aucune nouvelle technologie de file d'attente : réutiliser le pattern **`SELECT ... FOR UPDATE SKIP LOCKED` + `@Async`/`ThreadPoolTaskExecutor`** déjà retenu au Core PRD (§40) pour le batch de génération. `pack_import_jobs` suit la même forme que `batch_jobs` — même mécanisme de reprise, même exécuteur dédié pour ne pas affamer les threads HTTP. Le nettoyage des imports expirés (§133, `PACK_IMPORT_RETENTION_HOURS=24`) est un `@Scheduled` Spring standard, sans dépendance additionnelle.

---

# 54. IMPORT JOB STATUS

```text
UPLOADED
SCANNING
VALIDATING
VALID
INVALID
INSTALLING
INSTALLED
FAILED
EXPIRED
```

---

# 55. TEMPLATE MODIFICATIONS

Adapter la table existante `templates`.

Ajouter si absent :

```text
origin
source_pack_id nullable
```

`origin` :

```text
SYSTEM
PACK
USER
```

---

# 56. TEMPLATE VERSION

`template_versions` doit pouvoir conserver :

```text
source_pack_version_id
source_template_code
```

Ainsi la provenance historique est déterminable.

---

# 57. CONTRAINTES FK

Utiliser :

```text
ON DELETE RESTRICT
```

sur les relations historiques importantes.

Ne jamais cascade-delete une version utilisée par des documents générés.

---

# 58. INDEXES

Créer au minimum :

```sql
CREATE INDEX idx_pack_key
ON business_packs(pack_key);

CREATE INDEX idx_pack_status
ON business_packs(status);

CREATE INDEX idx_pack_version_pack
ON business_pack_versions(business_pack_id);

CREATE INDEX idx_pack_template_version
ON business_pack_templates(business_pack_version_id);

CREATE INDEX idx_pack_import_status
ON pack_import_jobs(status);

CREATE INDEX idx_pack_installation_org
ON business_pack_installations(organization_id);
```

---

# 59. MIGRATIONS

Utiliser Flyway.

Exemple :

```text
V20__business_pack_core.sql
V21__business_pack_templates.sql
V22__business_pack_prompts.sql
V23__business_pack_import_jobs.sql
V24__template_pack_origin.sql
```

Adapter les numéros aux migrations réellement présentes.

Cursor doit inspecter les migrations existantes avant de choisir les numéros.

---

# 60. INTERDICTION FLYWAY

Ne jamais modifier une migration déjà appliquée.

---

# 61. ARCHITECTURE BACKEND

Créer package :

```text
com.docuforge.pack
```

Sous-packages :

```text
api
application
domain
infrastructure
validation
archive
manifest
installation
export
```

---

# 62. SERVICES PRINCIPAUX

Créer des responsabilités séparées :

```text
PackUploadService
PackArchiveInspector
PackManifestParser
PackManifestValidator
PackCompatibilityService
PackChecksumValidator
PackTemplateValidator
PackSecurityValidator
PackValidationService
PackInstallationService
PackUpdateService
PackUninstallService
PackExportService
PackQueryService
```

Éviter un `BusinessPackService` de 3 000 lignes.

---

# 63. ARCHIVE INSPECTOR

Responsable uniquement de :

```text
ZIP structure
entry count
sizes
compression ratios
unsafe paths
extensions
```

Ne doit pas créer d'entités métier.

---

# 64. MANIFEST PARSER

Responsable :

```text
JSON
→ Java DTO
```

Utiliser Jackson.

Unknown fields :

choisir une stratégie documentée.

Pour DBPF-1 :

recommandation :

```text
unknown fields = warning
```

sauf lorsque leur présence rend la structure ambiguë.

---

# 65. VALIDATION PIPELINE

Pipeline obligatoire :

```text
Upload
   ↓
Basic file validation
   ↓
Archive security scan
   ↓
Find manifest
   ↓
JSON schema validation
   ↓
Manifest semantic validation
   ↓
Compatibility validation
   ↓
File existence validation
   ↓
Checksum validation
   ↓
Template DOCX validation
   ↓
Placeholder extraction
   ↓
Metadata validation
   ↓
Prompt validation
   ↓
Sample validation
   ↓
Build validation report
```

L'étape "Archive security scan" recouvre : protection zip bomb (§19), zip slip (§20), rejet des symlinks (§21), et un **scan ClamAV** de l'archive complète — extension du service ClamAV déjà déployé au Core PRD (§34) pour les templates DOCX/CSV, appelé ici avant toute extraction.

Aucune installation avant succès.

---

# 66. VALIDATION REPORT

Format :

```json
{
  "valid": true,
  "pack": {
    "id": "com.docuforge.pack.artisan",
    "name": "Pack Artisan",
    "version": "1.0.0"
  },
  "summary": {
    "errors": 0,
    "warnings": 2,
    "templates": 10,
    "prompts": 4
  },
  "issues": []
}
```

---

# 67. ISSUE FORMAT

```json
{
  "severity": "WARNING",
  "code": "PACK_TEMPLATE_UNUSED_VARIABLE",
  "message": "Variable déclarée mais non utilisée.",
  "file": "metadata/artisan-devis.json",
  "templateCode": "ARTISAN_DEVIS",
  "variable": "client.phone"
}
```

---

# 68. SEVERITY

```text
INFO
WARNING
ERROR
```

Toute `ERROR` :

```text
valid=false
```

---

# 69. CODES D'ERREUR

Créer au minimum :

```text
PACK_FILE_REQUIRED
PACK_FILE_TOO_LARGE
PACK_ARCHIVE_INVALID
PACK_ARCHIVE_LIMIT_EXCEEDED
PACK_UNSAFE_PATH
PACK_UNSUPPORTED_FILE_TYPE

PACK_MANIFEST_MISSING
PACK_MANIFEST_INVALID_JSON
PACK_MANIFEST_SCHEMA_INVALID
PACK_SCHEMA_UNSUPPORTED

PACK_ID_INVALID
PACK_VERSION_INVALID
PACK_LOCALE_INVALID

PACK_INCOMPATIBLE_DOCUFORGE_VERSION

PACK_FILE_MISSING
PACK_CHECKSUM_MISMATCH

PACK_TEMPLATE_INVALID
PACK_TEMPLATE_CODE_DUPLICATED
PACK_TEMPLATE_UNDECLARED_VARIABLE
PACK_TEMPLATE_UNUSED_VARIABLE

PACK_PROMPT_MISSING
PACK_PROMPT_DUPLICATED

PACK_ALREADY_INSTALLED
PACK_VERSION_ALREADY_INSTALLED
PACK_DOWNGRADE_NOT_ALLOWED

PACK_INSTALLATION_FAILED
PACK_UNINSTALL_BLOCKED
```

---

# 70. COMPATIBILITY

Comparer :

```text
current DocuForge version

minimumDocuForgeVersion
maximumDocuForgeVersion
```

Exemple :

```text
DocuForge 1.5.0

Pack:
minimum = 1.4.0
maximum = 1.x
```

Compatible.

Utiliser une bibliothèque SemVer éprouvée ou implémentation testée.

Ne pas comparer lexicalement les versions.

---

# 71. DOWNGRADE

Par défaut :

```text
1.3.0 → 1.2.0
```

interdit.

Erreur :

```text
PACK_DOWNGRADE_NOT_ALLOWED
```

Une fonction administrative future pourra permettre un rollback contrôlé.

---

# 72. INSTALLATION ATOMIQUE

Une installation doit être tout ou rien.

Exemple :

```text
10 templates

9 importés
10e échoue
```

Résultat :

```text
0 template installé
```

et non :

```text
9 templates orphelins
```

---

# 73. TRANSACTION

La partie DB de l'installation doit être transactionnelle.

Les fichiers temporaires doivent être gérés séparément avec compensation.

Pipeline :

```text
Stage files
    ↓
Validate
    ↓
Begin transaction
    ↓
Create pack/version
    ↓
Create template versions
    ↓
Create variables
    ↓
Create prompts
    ↓
Create associations
    ↓
Commit
    ↓
Promote staged files
```

Si la promotion finale peut échouer, prévoir une stratégie cohérente de staging/compensation.

---

# 74. STOCKAGE

Utiliser l'abstraction DocuForge existante :

```java
StorageProvider
```

Ne pas écrire directement :

```java
Files.copy(..., "/opt/docuforge/...")
```

depuis le domaine.

---

# 75. STORAGE KEYS

Exemple :

```text
packs/
com.docuforge.pack.artisan/
1.0.0/
templates/
...
```

Les noms physiques peuvent utiliser UUID.

Ne jamais faire confiance au nom fourni dans le ZIP pour déterminer un chemin physique arbitraire.

---

# 76. ARCHIVE ORIGINALE

Option recommandée :

conserver l'archive originale après validation.

Storage :

```text
packs/archives/{packId}/{version}/{uuid}.zip
```

Avec checksum.

Cela facilite :

```text
audit
export
diagnostic
restauration
```

---

# 77. INSTALLATION PREVIEW

L'import doit être en deux étapes :

```text
IMPORT
→ VALIDATE

puis

INSTALL
```

Ne jamais installer automatiquement dès upload.

---

# 78. API BASE

```text
/api/v1/admin/business-packs
```

Toutes les routes sont ADMIN sauf indication contraire.

---

# 79. IMPORT

```http
POST /api/v1/admin/business-packs/import
Content-Type: multipart/form-data
```

Paramètre :

```text
file
```

Réponse :

```json
{
  "jobId": "...",
  "status": "UPLOADED"
}
```

---

# 80. IMPORT STATUS

```http
GET /api/v1/admin/business-packs/imports/{jobId}
```

Retourne :

```text
status
validation report
pack metadata
```

---

# 81. VALIDATION

Si traitement synchrone acceptable pour petits packs :

```http
POST /api/v1/admin/business-packs/imports/{jobId}/validate
```

Sinon job async.

Le contrat API doit rester stable.

---

# 82. INSTALLATION

```http
POST /api/v1/admin/business-packs/imports/{jobId}/install
```

Body :

```json
{
  "enablePack": true,
  "enableTemplates": true
}
```

---

# 83. LIST PACKS

```http
GET /api/v1/admin/business-packs
```

Filtres :

```text
status
type
search
page
size
sort
```

---

# 84. PACK DETAILS

```http
GET /api/v1/admin/business-packs/{packId}
```

Retour :

```text
metadata
current version
versions
templates
prompts
installation status
```

---

# 85. PACK VERSIONS

```http
GET /api/v1/admin/business-packs/{packId}/versions
```

---

# 86. PACK TEMPLATES

```http
GET /api/v1/admin/business-packs/{packId}/templates
```

---

# 87. ENABLE

```http
POST /api/v1/admin/business-packs/{packId}/enable
```

---

# 88. DISABLE

```http
POST /api/v1/admin/business-packs/{packId}/disable
```

Désactiver le pack signifie :

```text
templates non proposés pour nouvelles générations
```

mais :

```text
documents historiques toujours accessibles
```

---

# 89. TEMPLATE ENABLE/DISABLE

Permettre :

```http
POST /api/v1/admin/business-packs/{packId}/templates/{templateCode}/enable
```

et :

```text
/disable
```

Un administrateur peut donc installer Pack RH mais désactiver certains modèles.

---

# 90. UPDATE

Une nouvelle archive portant le même `pack_key` et une version supérieure est considérée comme update candidate.

Workflow :

```text
Import 1.2.0
     ↓
Detected installed 1.1.0
     ↓
Compare
     ↓
Validation
     ↓
Display changes
     ↓
Admin confirms
     ↓
Install 1.2.0
```

---

# 91. CHANGE ANALYSIS

Calculer :

```text
templates added
templates updated
templates removed
variables added
variables removed
required variables added
prompts changed
```

---

# 92. UPDATE PREVIEW

Exemple :

```text
Pack Artisan

1.2.0 → 1.3.0

+ 2 nouveaux modèles
~ 3 modèles mis à jour
- 0 modèle supprimé

+ 5 variables optionnelles
+ 0 variable obligatoire

4 prompts modifiés
```

---

# 93. BREAKING CHANGE DETECTION

Alerter si :

```text
template removed
variable removed
variable type changed
optional → required
template code changed
```

Severity :

```text
WARNING ou ERROR
```

selon compatibilité annoncée.

Si changement incompatible dans MINOR/PATCH :

warning fort :

```text
PACK_SEMVER_BREAKING_CHANGE
```

---

# 94. UNINSTALL

Endpoint :

```http
DELETE /api/v1/admin/business-packs/{packId}
```

Mais ne pas hard-delete immédiatement.

---

# 95. UNINSTALL RULE

Si aucun document historique :

désinstallation autorisée.

Si documents générés :

conserver obligatoirement :

```text
pack metadata
pack version
template version
```

Le pack peut devenir :

```text
UNINSTALLED
```

et ses templates indisponibles pour nouvelles générations.

---

# 96. FORCE DELETE

Ne PAS implémenter un :

```text
force=true
```

qui détruit les dépendances historiques.

---

# 97. EXPORT

Endpoint :

```http
POST /api/v1/admin/business-packs/{packId}/export
```

ou pour pack custom :

```http
GET /api/v1/admin/business-packs/{packId}/export
```

Retour :

```text
application/zip
```

---

# 98. EXPORT REQUIREMENTS

L'export doit reconstruire :

```text
manifest.json
templates
metadata
prompts
samples
previews
checksums
```

et générer un DBPF-1 valide.

---

# 99. ROUND-TRIP TEST

Test obligatoire :

```text
Create custom pack
→ Export ZIP
→ Delete test installation
→ Import ZIP
→ Validate
→ Install
```

Résultat fonctionnel identique.

---

# 100. API TEMPLATE LIBRARY

Adapter :

```http
GET /api/v1/templates
```

Filtres :

```text
origin
packId
category
search
```

Exemple :

```text
?origin=PACK&packId=...
```

---

# 101. TEMPLATE DTO

Ajouter :

```json
{
  "origin": "PACK",
  "pack": {
    "id": "...",
    "name": "Pack Artisan",
    "version": "1.2.0"
  }
}
```

---

# 102. SECURITY API

Import/export/install/update :

```text
ADMIN
```

Lecture catalogue :

```text
ADMIN
EDITOR
USER
VIEWER
```

selon les permissions existantes.

Le backend doit être autoritaire.

---

# 103. MULTI-TENANCY

Si DocuForge possède déjà `company_id`/`organization_id`, toutes les installations custom doivent respecter cette isolation.

Organization A ne doit jamais :

```text
voir
installer
modifier
exporter
```

un pack custom appartenant à B.

---

# 104. OFFICIAL PACKS

Un pack `OFFICIAL` peut être globalement disponible mais son installation reste rattachée à l'organisation lorsque DocuForge devient multi-tenant.

Séparer conceptuellement :

```text
Pack Definition

et

Pack Installation
```

Cette distinction est importante pour le futur SaaS.

---

# 105. AUDIT

Ajouter événements :

```text
PACK_UPLOADED
PACK_VALIDATED
PACK_VALIDATION_FAILED
PACK_INSTALLED
PACK_INSTALLATION_FAILED
PACK_ENABLED
PACK_DISABLED
PACK_UPDATED
PACK_UNINSTALLED
PACK_EXPORTED

PACK_TEMPLATE_ENABLED
PACK_TEMPLATE_DISABLED
```

---

# 106. AUDIT METADATA

Exemple :

```json
{
  "packKey": "com.docuforge.pack.artisan",
  "version": "1.2.0",
  "previousVersion": "1.1.0",
  "templateCount": 12
}
```

Ne pas stocker l'archive entière dans l'audit.

---

# 107. FRONTEND ROUTES

Ajouter :

```text
/admin/business-packs

/admin/business-packs/import

/admin/business-packs/import/:jobId

/admin/business-packs/:packId

/admin/business-packs/:packId/versions

/admin/business-packs/:packId/templates
```

---

# 108. NAVIGATION

Administration :

```text
Dashboard
Templates
Documents
Générations
Packs métier
Utilisateurs
Paramètres
```

---

# 109. PAGE PACKS

Route :

```text
/admin/business-packs
```

Afficher des cards ou table.

Colonnes :

```text
Nom
Catégorie
Éditeur
Version
Templates
Type
Statut
Dernière mise à jour
Actions
```

---

# 110. FILTRES

```text
Tous
Installés
Désactivés
Mise à jour disponible

Official
Custom
Third-party
```

Recherche texte.

---

# 111. PACK CARD

Afficher :

```text
Pack Artisan

12 modèles

Version 1.2.0

OFFICIAL

[Voir]
```

Si disabled :

```text
Désactivé
```

---

# 112. IMPORT WIZARD

Créer wizard :

```text
1 Upload
      ↓
2 Validation
      ↓
3 Review
      ↓
4 Installation
      ↓
5 Success
```

---

# 113. STEP 1 — UPLOAD

Zone drag-and-drop.

Accepter uniquement :

```text
.zip
```

Afficher :

```text
taille max
format DBPF supporté
```

---

# 114. STEP 2 — VALIDATION

Afficher progression :

```text
✓ Archive
✓ Manifest
✓ Compatibilité
✓ Checksums
✓ Templates
✓ Variables
✓ Prompts
```

En cas d'erreur :

```text
✗ templates/devis.docx

Variable {{client.phone}}
non déclarée dans metadata.
```

---

# 115. STEP 3 — REVIEW

Afficher :

```text
Pack Artisan
Version 1.0.0
Éditeur DocuForge AI

10 templates
4 prompts
2 samples

Compatible DocuForge 1.x
```

Liste des modèles avec previews.

---

# 116. CONFIRMATION

Bouton :

```text
Installer le pack
```

avec confirmation explicite.

---

# 117. SUCCESS

Afficher :

```text
Pack Artisan installé

10 modèles ajoutés
4 prompts ajoutés

[Voir les modèles]
[Voir le pack]
```

---

# 118. PACK DETAIL

Sections :

```text
Overview
Templates
Versions
Prompts
Informations
```

---

# 119. OVERVIEW

Afficher :

```text
description
publisher
version
status
categories
locales
installation date
template count
```

---

# 120. TEMPLATES TAB

Pour chaque modèle :

```text
preview
name
code
version
category
enabled
```

Actions :

```text
Preview
Generate document
Enable/Disable
```

---

# 121. VERSIONS TAB

Exemple :

```text
1.3.0   Current
1.2.0   Superseded
1.1.0   Superseded
```

Ne pas permettre de supprimer les versions historiques utilisées.

---

# 122. UPDATE UI

Lorsque nouvelle version importée :

```text
Nouvelle version détectée

Pack Artisan
1.2.0 → 1.3.0
```

Afficher diff.

Bouton :

```text
Mettre à jour
```

---

# 123. TEMPLATE LIBRARY

Ajouter badge :

```text
PACK ARTISAN
```

ou :

```text
RH
```

aux templates issus d'un pack.

---

# 124. TEMPLATE DETAILS

Afficher :

```text
Origine

Pack Artisan
Version pack 1.2.0
Template version 2.0.0
```

---

# 125. CUSTOMIZATION — MVP

Un template provenant d'un pack doit rester immutable.

Si utilisateur veut le modifier :

```text
Dupliquer
```

Résultat :

```text
origin = USER
```

Le template original reste intact.

---

# 126. POURQUOI DUPLIQUER

Sans cette règle :

```text
Pack Artisan 1.2
      ↓
Utilisateur modifie devis.docx
      ↓
Pack Artisan 1.3
      ↓
Conflit
```

Avec duplication :

```text
Pack template
      ↓
Duplicate
      ↓
User template
```

Aucun conflit de mise à jour.

---

# 127. API DUPLICATION

Réutiliser ou ajouter :

```http
POST /api/v1/templates/{templateId}/duplicate
```

Body :

```json
{
  "name": "Mon devis personnalisé"
}
```

Le résultat n'est plus lié au lifecycle du pack.

---

# 128. FRONTEND TECHNOLOGY

Respecter le stack DocuForge existant :

```text
React
TypeScript
Vite
Tailwind
TanStack Query
React Hook Form
Zod
```

Ne pas introduire une deuxième bibliothèque de state management sans justification.

Confirmation : l'état UI du wizard d'import (§131-132) et des filtres de la page Packs (§110) est géré avec **Zustand**, déjà introduit au Core PRD (§7) pour l'application principale — pas de nouvelle bibliothèque. TanStack Query reste responsable de l'état serveur, conformément à ce qui suit.

---

# 129. TANSTACK QUERY

Créer hooks :

```text
useBusinessPacks()
useBusinessPack()
usePackImport()
usePackImportStatus()
useInstallPack()
useEnablePack()
useDisablePack()
usePackVersions()
usePackTemplates()
```

---

# 130. ZOD

Utiliser pour validation UX des réponses/formulaires lorsque pertinent.

La validation backend reste autoritaire.

---

# 131. ASYNCHRONISME

Les gros imports doivent pouvoir être traités en job.

Frontend :

```text
upload
→ jobId
→ polling
→ validation complete
```

Intervalle raisonnable.

Arrêter polling lorsque état terminal.

---

# 132. TERMINAL STATES

```text
VALID
INVALID
INSTALLED
FAILED
EXPIRED
```

---

# 133. CLEANUP TEMPORAIRE

Les imports non installés doivent expirer.

Configuration :

```text
PACK_IMPORT_RETENTION_HOURS=24
```

Job :

```text
expired import
→ delete temporary files
→ status EXPIRED
```

---

# 134. ERROR FORMAT

Respecter le format DocuForge :

```json
{
  "timestamp": "2026-09-06T10:00:00Z",
  "status": 422,
  "code": "PACK_MANIFEST_SCHEMA_INVALID",
  "message": "Le manifeste du pack est invalide.",
  "details": [],
  "traceId": "..."
}
```

---

# 135. HTTP STATUS

Utiliser :

```text
200
201
202
204

400
401
403
404
409
413
415
422
429

500
503
```

---

# 136. UPLOAD ERROR

Fichier > limite :

```text
413
PACK_FILE_TOO_LARGE
```

---

# 137. MIME

Ne jamais faire confiance uniquement :

```text
Content-Type
filename extension
```

Vérifier contenu/signature lorsque possible.

### Décision — bibliothèque de détection

```text
Apache Tika (org.apache.tika:tika-core)
```

Détecte le type réel d'un fichier par ses "magic bytes" plutôt que par extension ou `Content-Type` fourni par le client — appliqué à chaque fichier du ZIP pendant l'étape "Basic file validation" du pipeline §65, avant même la vérification structurelle ci-dessous.

Pour DOCX :

il doit s'agir d'une archive Office Open XML valide.

---

# 138. DOCX VALIDATION

Vérifier au minimum :

```text
valid ZIP
[Content_Types].xml
word/document.xml
```

Ne pas exécuter macros.

---

# 139. DOCM

Refuser :

```text
.docm
```

dans DBPF-1.

---

# 140. EXTERNAL RELATIONSHIPS

Analyser les relations DOCX.

Avertir/refuser selon politique si le template contient :

```text
external remote relationships
OLE objects
embedded executables
```

Les modèles de packs officiels doivent éviter ces éléments.

### Décision — implémentation

Aucune nouvelle dépendance : **Apache POI**, déjà présent via `docx-stamper` (Core PRD §9), expose l'API `OPCPackage` / `PackageRelationshipCollection` pour énumérer les relations d'un document (`word/_rels/document.xml.rels`) et détecter celles dont `TargetMode = EXTERNAL` — couvre le besoin ci-dessus sans bibliothèque OOXML supplémentaire, conformément à la règle "ne duplique pas une abstraction déjà existante" (§194).

---

# 141. SECURITY TESTS — ARCHIVE

Créer tests :

```text
valid ZIP
corrupted ZIP
empty ZIP
nested ZIP
ZIP bomb
too many entries
huge entry
../ traversal
absolute path
Windows traversal
duplicate filename
unsupported extension
symlink
```

---

# 142. SECURITY TEST — ZIP SLIP

Créer archive :

```text
templates/../../evil.txt
```

Attendu :

```text
PACK_UNSAFE_PATH
```

et aucun fichier hors staging.

---

# 143. MANIFEST TESTS

Tester :

```text
missing manifest
invalid JSON
wrong schemaVersion
missing id
invalid id
invalid SemVer
invalid locale
duplicate template code
duplicate prompt code
unknown template file
checksum mismatch
```

---

# 144. TEMPLATE TESTS

Tester :

```text
valid DOCX
corrupted DOCX
undeclared placeholder
unused variable
duplicate variable
invalid variable key
invalid variable type
missing metadata
```

---

# 145. INSTALLATION TESTS

Tester :

```text
new pack
existing pack/new version
same version twice
older version
incompatible version
installation failure midway
DB rollback
storage failure
```

---

# 146. ATOMICITY TEST

Simuler :

```text
10 templates
```

faire échouer le template 8.

Attendu :

```text
aucun pack actif
aucun template partiellement installé
aucun prompt orphelin
```

---

# 147. UPDATE TESTS

Pack :

```text
1.0.0
```

installer :

```text
1.1.0
```

Vérifier :

```text
1.1.0 current
1.0.0 superseded
historical references intact
```

---

# 148. DOCUMENT HISTORY TEST

Générer document avec :

```text
Pack Artisan 1.0.0
Template Devis 1.0.0
```

Installer :

```text
Pack Artisan 2.0.0
```

Télécharger ancien document.

Attendu :

identique.

---

# 149. DISABLE TEST

Désactiver Pack Artisan.

Attendu :

```text
nouvelle génération
→ template non proposé
```

mais :

```text
ancien document
→ accessible
```

---

# 150. DUPLICATION TEST

Dupliquer template Pack.

Attendu :

```text
new template origin=USER
```

Modifier copie.

Mettre à jour pack.

Attendu :

copie utilisateur inchangée.

---

# 151. UNINSTALL TEST

Pack utilisé par 50 documents.

Uninstall.

Attendu :

```text
templates indisponibles pour nouvelles générations
documents historiques accessibles
historical metadata intact
```

---

# 152. EXPORT TEST

Exporter pack.

Vérifier :

```text
manifest
files
metadata
checksums
structure
```

Réimporter.

Validation :

```text
VALID
```

---

# 153. MULTI-TENANT TEST

Organization A :

```text
custom pack A
```

Organization B tente :

```text
GET
EXPORT
DISABLE
DELETE
```

Attendu :

```text
404/403
```

Aucune fuite d'information.

---

# 154. PERFORMANCE

Objectifs MVP indicatifs.

Listing :

```text
< 500 ms
```

Validation petit pack :

```text
< 5 sec
```

hors conversion/previews lourdes.

Pack de 100 templates :

traitement async accepté.

---

# 155. LOGGING

Logger :

```text
jobId
packKey
version
phase
duration
status
traceId
```

---

# 156. NE PAS LOGGER

```text
template document content
personal sample data
prompt complet inutilement
JWT
password
API key
storage credentials
```

---

# 157. METRICS

Préparer :

```text
pack.import.count
pack.import.failure.count
pack.validation.duration
pack.install.duration
pack.install.failure.count
```

Si Micrometer est déjà présent, l'utiliser.

Ne pas introduire une nouvelle stack d'observabilité uniquement pour ce module.

---

# 158. ACTUATOR

Le module Pack ne doit pas rendre `/actuator/health` DOWN simplement parce qu'un import utilisateur particulier a échoué.

---

# 159. BACKUP

Les packs installés doivent être couverts par la stratégie de sauvegarde DocuForge :

```text
PostgreSQL
+
StorageProvider
```

---

# 160. RESTORE

Après restauration :

```text
pack
pack versions
templates
template versions
prompts
documents historiques
```

doivent conserver leurs associations.

---

# 161. PACK OFFICIEL ARTISAN DE TEST

Créer un pack de démonstration interne :

```text
com.docuforge.pack.artisan-demo
```

Version :

```text
1.0.0
```

Contenu :

```text
3 templates
2 prompts
1 CSV
1 JSON
previews
```

Uniquement données fictives.

---

# 162. TEMPLATE 1

```text
ARTISAN_DEVIS
```

Variables :

```text
company.name
company.address
client.name
client.address
quote.reference
quote.date
quote.validUntil
work.description
pricing.subtotal
pricing.vat
pricing.total
```

---

# 163. TEMPLATE 2

```text
ARTISAN_INTERVENTION
```

Variables :

```text
company.name
client.name
intervention.reference
intervention.date
intervention.location
intervention.description
technician.name
```

---

# 164. TEMPLATE 3

```text
ARTISAN_COMPLETION_CERTIFICATE
```

Variables :

```text
company.name
client.name
work.description
work.location
work.completionDate
issuer.name
```

Il doit s'agir d'un document privé de démonstration, pas d'un document officiel imitant une autorité publique.

---

# 165. FUTURS PACKS OFFICIELS

Architecture compatible avec :

```text
com.docuforge.pack.rh
com.docuforge.pack.association
com.docuforge.pack.consulting
com.docuforge.pack.realestate
```

Aucun de ces identifiants ne doit nécessiter de code Java spécifique.

---

# 166. PACK BUILDER — POST-MVP

Préparer architecture pour futur écran :

```text
Créer un pack
     ↓
Informations
     ↓
Sélectionner templates
     ↓
Ajouter prompts
     ↓
Ajouter samples
     ↓
Preview
     ↓
Export DBPF
```

Pas nécessaire au MVP si `PackExportService` suffit initialement.

---

# 167. MARKETPLACE READINESS

Prévoir dans modèle :

```text
publisher_id
publisher_name
pack_type
```

Future :

```text
marketplace_id
license
price
signature
rating
download_count
```

Ne PAS ajouter tous ces champs maintenant s'ils ne sont pas utilisés.

YAGNI.

---

# 168. LICENCE

`LICENSE.txt` recommandé.

Le manifest peut ultérieurement contenir :

```text
license
```

Dans MVP, considérer comme metadata informative.

---

# 169. INTERNATIONALISATION

Le pack peut déclarer plusieurs locales.

Architecture future :

```text
metadata/
  fr-FR/
  pt-PT/
```

Pour DBPF-1 MVP, un template peut être associé principalement à une locale.

Ne pas complexifier prématurément si DocuForge n'a encore qu'une langue.

---

# 170. CONVENTION DE NOMMAGE TEMPLATE

Codes :

```text
PACKPREFIX_DOCUMENT
```

Exemples :

```text
ARTISAN_DEVIS
RH_ATTESTATION_EMPLOYEUR
ASSOCIATION_PV_AG
CONSULTING_MISSION_REPORT
REAL_ESTATE_VISIT_REPORT
```

Upper snake case.

Regex :

```regex
^[A-Z][A-Z0-9_]{2,149}$
```

---

# 171. COLLISION TEMPLATE CODE

Deux packs différents peuvent théoriquement déclarer :

```text
DEVIS
```

Pour éviter les collisions, l'identité technique doit inclure le pack.

Conceptuellement :

```text
pack_key + template_code
```

et non `template_code` seul globalement.

---

# 172. PROMPT CODE

Même principe :

```text
pack_key + prompt_code
```

---

# 173. DATABASE TRANSACTION BOUNDARY

Ne pas placer :

```text
ZIP extraction
DOCX parsing lourd
checksum de 100 MB
```

dans une transaction DB longue.

Faire :

```text
validate/stage
```

d'abord.

Puis transaction courte d'installation.

---

# 174. LOCKING UPDATE

Deux administrateurs ne doivent pas pouvoir installer simultanément :

```text
Pack Artisan 1.2.0
```

pour la même organisation.

Utiliser :

```text
unique constraint
+
transaction
```

et éventuellement verrou logique.

---

# 175. IDEMPOTENCE INSTALL

Deux appels d'installation du même `jobId` :

ne doivent pas créer deux installations.

---

# 176. TEMPORARY DIRECTORY

Utiliser un répertoire contrôlé par l'application.

Exemple conceptuel :

```text
/tmp/docuforge-pack-import/{jobUUID}
```

Ne jamais dériver le chemin directement du nom du fichier utilisateur.

---

# 177. CLEANUP FAILURE

Dans :

```java
finally
```

ou mécanisme dédié :

nettoyer staging lorsque sûr.

Conserver uniquement les artefacts nécessaires au diagnostic selon politique de rétention.

---

# 178. APPLICATION CONFIGURATION

Ajouter :

```yaml
docuforge:
  packs:
    enabled: true
    max-upload-size-mb: 100
    max-uncompressed-size-mb: 250
    max-files: 500
    max-templates: 100
    max-single-file-mb: 25
    max-compression-ratio: 50
    import-retention-hours: 24
```

Support environnement.

---

# 179. FEATURE FLAG

Ajouter :

```text
DOCUFORGE_PACKS_ENABLED=true
```

Si false :

API module désactivée ou retourne feature disabled selon architecture existante.

---

# 180. OPENAPI

Documenter tous les endpoints.

Ajouter exemples :

```text
import
validation
installation
update
disable
export
```

---

# 181. DOCUMENTATION

Créer :

```text
docs/business-packs/
│
├── README.md
├── DBPF-1.md
├── manifest-reference.md
├── template-metadata-reference.md
├── security.md
├── versioning.md
├── creating-a-pack.md
├── importing-a-pack.md
└── troubleshooting.md
```

---

# 182. DBPF-1.md

Doit devenir la spécification officielle du format.

Inclure :

```text
structure
required files
allowed files
manifest
metadata
checksums
versioning
security
examples
```

---

# 183. README CRÉATEUR

Créer tutoriel :

```text
Créer votre premier pack en 15 minutes
```

Exemple Pack Artisan Demo.

---

# 184. CI

Ajouter tests Business Pack au pipeline.

Minimum :

```text
unit tests
integration tests
security archive tests
DB tests
```

### Décision — pipeline unique

Étendre la pipeline **GitHub Actions** déjà définie au Core PRD (§109 `ci.yml`) plutôt que d'en créer une seconde : ajouter au job `mvn verify` existant les tests de sécurité archive (§141-153) et le test round-trip export→import→validate→install (§99), afin qu'aucune régression du module Pack ne puisse être mergée sans que l'ensemble de la suite — Core + Business Pack — soit vert.

---

# 185. TESTCONTAINERS

Utiliser PostgreSQL Testcontainers pour :

```text
installation
versioning
constraints
transactions
rollback
tenant isolation
```

Ne pas remplacer ces tests par H2.

---

# 186. TEST FIXTURES

Créer :

```text
src/test/resources/packs/
```

Avec :

```text
valid-pack.zip
missing-manifest.zip
invalid-manifest.zip
checksum-error.zip
zip-slip.zip
too-large-metadata fixture
invalid-docx.zip
duplicate-template.zip
incompatible-version.zip
```

Les fixtures doivent être petites.

---

# 187. TESTS FRONTEND

Tester :

```text
pack list
upload
validation errors
validation warnings
review
install
disable
update
template filtering
```

Utiliser la stack de tests déjà présente dans DocuForge.

---

# 188. E2E PRINCIPAL

```text
Login ADMIN
     ↓
Packs métier
     ↓
Importer artisan-demo.zip
     ↓
Validation VALID
     ↓
Review
     ↓
Install
     ↓
Pack visible
     ↓
Templates
     ↓
ARTISAN_DEVIS
     ↓
Generate
     ↓
DOCX/PDF
```

---

# 189. E2E UPDATE

```text
Install 1.0.0
→ generate document
→ import 1.1.0
→ review diff
→ update
→ generate new document
→ open old document
```

Les deux doivent rester reproductibles historiquement.

---

# 190. CRITÈRES D'ACCEPTATION

Le module est MVP-ready lorsque :

1. ZIP DBPF-1 valide accepté.
2. ZIP invalide refusé.
3. ZIP Slip bloqué.
4. ZIP bomb contrôlé.
5. Manifest validé.
6. SemVer validé.
7. Compatibilité DocuForge validée.
8. Checksums vérifiés.
9. DOCX validés.
10. Placeholders détectés.
11. Metadata validée.
12. Prompts importés.
13. Validation report affiché.
14. Aucun pack n'est installé avant confirmation.
15. Installation atomique.
16. Templates liés au pack.
17. Origine PACK visible.
18. Pack désactivable.
19. Templates individuellement désactivables.
20. Templates Pack immutables.
21. Duplication vers USER fonctionne.
22. Mise à jour fonctionne.
23. Downgrade refusé.
24. Versions historiques conservées.
25. Documents historiques inchangés.
26. Uninstall non destructif.
27. Export DBPF fonctionne.
28. Round-trip export/import fonctionne.
29. Audit fonctionne.
30. Isolation organisation fonctionne.
31. Aucun secret dans logs.
32. Tests backend verts.
33. Tests intégration verts.
34. Tests frontend verts.
35. E2E principal vert.
36. Documentation DBPF-1 complète.

---

# 191. DEFINITION OF DONE

Une phase n'est terminée que si :

```text
implementation
+
migration si nécessaire
+
validation
+
authorization
+
error handling
+
audit
+
tests
+
documentation
+
build green
```

---

# 192. PLAN D'IMPLÉMENTATION CURSOR

Le développement doit être strictement incrémental.

Ne jamais demander à Cursor :

```text
Implémente tout le PRD.
```

---

# PHASE 0 — AUDIT DE L'EXISTANT

Cursor doit uniquement analyser.

Inspecter :

```text
backend architecture
frontend architecture
templates
template_versions
template_variables
generated_documents
StorageProvider
AIProvider
authentication
RBAC
Flyway
error model
audit
Docker
tests
```

Livrable :

```text
docs/business-packs/IMPLEMENTATION_ANALYSIS.md
```

Aucun changement métier.

STOP.

---

# PHASE 1 — DBPF-1 SPECIFICATION

Créer :

```text
DBPF-1.md
manifest-reference.md
template-metadata-reference.md

JSON Schema manifest
JSON Schema template metadata
```

Créer fixtures JSON.

Tests JSON Schema.

Aucune DB.

STOP.

---

# PHASE 2 — DOMAIN MODEL

Créer :

```text
BusinessPack
BusinessPackVersion
BusinessPackType
BusinessPackStatus
PackVersionStatus
```

Migrations :

```text
business_packs
business_pack_versions
```

Repositories.

Tests PostgreSQL.

STOP.

---

# PHASE 3 — PACK RELATIONS

Ajouter :

```text
business_pack_templates
business_pack_prompts
business_pack_files
business_pack_installations
pack_import_jobs
```

Adapter template origin.

Migrations + tests.

STOP.

---

# PHASE 4 — SECURE ARCHIVE INSPECTOR

Implémenter :

```text
PackArchiveInspector
```

Uniquement :

```text
size
entries
ratio
extensions
paths
ZIP validity
```

Tests sécurité exhaustifs.

Aucune installation.

STOP.

---

# PHASE 5 — MANIFEST PARSER

Implémenter :

```text
PackManifestParser
PackManifestValidator
```

JSON Schema + validation sémantique.

Tests.

STOP.

---

# PHASE 6 — CHECKSUMS & COMPATIBILITY

Implémenter :

```text
PackChecksumValidator
PackCompatibilityService
SemVer
```

Tests.

STOP.

---

# PHASE 7 — DOCX TEMPLATE VALIDATION

Implémenter :

```text
DOCX validation
placeholder extraction
metadata comparison
variable validation
```

Tests avec templates réels minimaux.

STOP.

---

# PHASE 8 — COMPLETE VALIDATION PIPELINE

Assembler :

```text
PackValidationService
```

Produire :

```text
PackValidationReport
PackValidationIssue
```

Pas encore d'installation.

STOP.

---

# PHASE 9 — IMPORT API

Implémenter :

```text
POST import
GET import status
```

Staging + retention.

ADMIN uniquement.

Audit.

STOP.

---

# PHASE 10 — INSTALLATION ENGINE

Implémenter :

```text
PackInstallationService
```

Installation transactionnelle.

Créer :

```text
pack
pack version
templates
template versions
variables
prompts
files
installation
```

Atomicité obligatoire.

STOP.

---

# PHASE 11 — PACK QUERY API

Implémenter :

```text
list
detail
versions
templates
```

Pagination.

RBAC.

STOP.

---

# PHASE 12 — ENABLE / DISABLE

Implémenter :

```text
pack enable
pack disable
template enable
template disable
```

Tests historiques.

STOP.

---

# PHASE 13 — FRONTEND LIST & DETAIL

Créer :

```text
Packs page
Pack detail
Templates tab
Versions tab
```

TanStack Query.

Loading/error/empty states.

STOP.

---

# PHASE 14 — IMPORT WIZARD

Créer :

```text
Upload
Validation
Review
Install
Success
```

Gestion warnings/errors.

STOP.

---

# PHASE 15 — UPDATE ENGINE

Implémenter :

```text
version detection
diff
breaking change detection
update
superseded versions
```

Tests historiques.

STOP.

---

# PHASE 16 — UPDATE UI

Afficher :

```text
old version
new version
diff
warnings
breaking changes
```

Confirmation.

STOP.

---

# PHASE 17 — DUPLICATE PACK TEMPLATE

Implémenter :

```text
PACK
→ duplicate
→ USER
```

Tests indépendance.

STOP.

---

# PHASE 18 — UNINSTALL

Implémenter désinstallation logique non destructive.

Tests documents historiques.

STOP.

---

# PHASE 19 — EXPORT

Implémenter :

```text
PackExportService
```

Créer ZIP DBPF-1 valide.

Checksums recalculés.

STOP.

---

# PHASE 20 — ROUND TRIP

Automatiser :

```text
export
→ import
→ validate
→ install
```

Corriger jusqu'à test vert.

STOP.

---

# PHASE 21 — ARTISAN DEMO PACK

Créer pack réel de démonstration :

```text
com.docuforge.pack.artisan-demo
```

3 templates.

Prompts.

Samples.

Previews.

Données fictives.

STOP.

---

# PHASE 22 — SECURITY HARDENING

Effectuer campagne :

```text
ZIP Slip
ZIP bomb
malformed ZIP
malformed DOCX
MIME spoofing
oversized files
tenant isolation
RBAC
path traversal
duplicate entries
resource exhaustion
```

Corriger.

STOP.

---

# PHASE 23 — E2E

Automatiser scénario complet.

STOP.

---

# PHASE 24 — DOCUMENTATION

Finaliser :

```text
creator guide
admin guide
DBPF reference
troubleshooting
security
versioning
```

STOP.

---

# PHASE 25 — RELEASE READINESS

Exécuter :

```text
backend build
frontend build
unit tests
integration tests
security tests
E2E
docker compose validation
secret scan si disponible
```

Produire :

```text
BUSINESS_PACK_RELEASE_REPORT.md
```

STOP.

---

# 193. PROMPT INITIAL À DONNER À CURSOR

Tu travailles sur le projet DocuForge AI.

Le document :

`docs/PRD_BUSINESS_PACK_SYSTEM.md`

est la source de vérité pour le module **DocuForge Business Pack System**.

Lis intégralement ce PRD avant toute modification.

IMPORTANT :

Le projet DocuForge AI existe déjà.

Tu ne dois donc PAS supposer que les noms de tables, packages, classes, routes, migrations ou abstractions proposés dans le PRD correspondent exactement à l'implémentation actuelle.

Pour cette itération, réalise UNIQUEMENT :

**PHASE 0 — AUDIT DE L'EXISTANT**

Tu ne dois implémenter aucune fonctionnalité Business Pack.

Analyse précisément le repository existant.

Recherche notamment :

- structure backend ;
- structure frontend ;
- version Java/Spring Boot ;
- version React/TypeScript ;
- système d'authentification ;
- rôles ;
- modèle Organization/Company ;
- tables et entités Template ;
- TemplateVersion ;
- TemplateVariable ;
- GeneratedDocument ;
- moteur de génération DOCX ;
- moteur PDF ;
- StorageProvider ;
- AIProvider ;
- Audit ;
- format d'erreur API ;
- Flyway ;
- OpenAPI ;
- tests ;
- Docker Compose ;
- conventions du projet.

Compare ensuite l'existant avec les exigences du PRD Business Pack System.

Créer uniquement :

`docs/business-packs/IMPLEMENTATION_ANALYSIS.md`

Le document doit contenir :

1. CURRENT ARCHITECTURE
2. EXISTING REUSABLE COMPONENTS
3. DATABASE CURRENT STATE
4. BACKEND CURRENT STATE
5. FRONTEND CURRENT STATE
6. STORAGE CURRENT STATE
7. AI CURRENT STATE
8. SECURITY CURRENT STATE
9. TEST CURRENT STATE
10. GAPS AGAINST PRD
11. REQUIRED DATABASE CHANGES
12. REQUIRED BACKEND CHANGES
13. REQUIRED FRONTEND CHANGES
14. COMPATIBILITY RISKS
15. MIGRATION RISKS
16. SECURITY RISKS
17. PROPOSED PACKAGE STRUCTURE
18. PROPOSED IMPLEMENTATION ORDER
19. OPEN QUESTIONS
20. PHASE 1 READINESS

Ne modifie :

- aucune entité ;
- aucune migration ;
- aucune API ;
- aucun composant React ;
- aucun Docker Compose ;
- aucune configuration métier.

Ne commence surtout PAS la Phase 1.

À la fin, affiche :

PHASE:
Phase 0 — Audit de l'existant

FILES CREATED:
...

FILES MODIFIED:
...

FINDINGS:
...

RISKS:
...

BLOCKERS:
...

NEXT PHASE:
Phase 1 — DBPF-1 Specification

Puis STOP.

---

# 194. PROMPT STANDARD POUR CHAQUE PHASE SUIVANTE

Relis intégralement :

`docs/PRD_BUSINESS_PACK_SYSTEM.md`

Puis lis :

`docs/business-packs/IMPLEMENTATION_ANALYSIS.md`

Analyse également l'état actuel réel du repository.

Nous t'autorisons maintenant à travailler UNIQUEMENT sur :

**[PHASE À REMPLACER]**

Avant toute modification :

1. résume les exigences exactes de la phase ;
2. inspecte les composants existants concernés ;
3. liste les fichiers à modifier ;
4. liste les fichiers à créer ;
5. identifie les migrations nécessaires ;
6. identifie les risques de régression ;
7. identifie les tests nécessaires ;
8. vérifie que la solution respecte les conventions existantes.

Ensuite seulement, implémente la phase.

RÈGLES :

- ne travaille sur aucune autre phase ;
- ne change pas la stack ;
- ne duplique pas une abstraction déjà existante ;
- réutilise StorageProvider ;
- réutilise AIProvider ;
- respecte le modèle de sécurité existant ;
- respecte le format d'erreur existant ;
- respecte Flyway ;
- ne modifie jamais une migration déjà appliquée ;
- aucun secret réel ;
- aucune donnée personnelle réelle ;
- ne désactive aucun test ;
- ne contourne aucune erreur de compilation ;
- ne supprime aucune fonctionnalité existante pour simplifier l'implémentation.

Après développement :

1. compile le backend ;
2. exécute les tests unitaires ;
3. exécute les tests d'intégration concernés ;
4. compile le frontend si concerné ;
5. exécute les tests frontend si concerné ;
6. vérifie les migrations ;
7. vérifie les erreurs/lint ;
8. corrige toutes les erreurs introduites ;
9. mets à jour la documentation de la phase.

Produis ensuite :

PHASE:
...

IMPLEMENTED:
...

FILES CREATED:
...

FILES MODIFIED:
...

MIGRATIONS:
...

API CHANGES:
...

TESTS ADDED:
...

TEST RESULTS:
...

SECURITY CHECKS:
...

KNOWN LIMITATIONS:
...

TECHNICAL DEBT:
...

NEXT PHASE:
...

Puis STOP.

Ne commence pas la phase suivante sans autorisation explicite.

---

# 195. RÈGLES ABSOLUES POUR CURSOR

Cursor ne doit jamais :

1. installer directement un ZIP non validé ;
2. extraire un chemin sans normalisation ;
3. faire confiance au MIME fourni par le client ;
4. autoriser du code exécutable dans DBPF-1 ;
5. autoriser DOCM ;
6. ignorer un checksum incorrect ;
7. écraser une version historique ;
8. modifier un template Pack directement ;
9. cascade-delete un template utilisé ;
10. modifier un ancien document après update ;
11. permettre un downgrade silencieux ;
12. installer partiellement un pack ;
13. stocker des fichiers temporaires indéfiniment ;
14. faire confiance au frontend pour la sécurité ;
15. contourner Organization/Tenant ;
16. exposer les fichiers physiques directement ;
17. mettre des chemins utilisateur dans le filesystem sans validation ;
18. mettre des secrets dans les logs ;
19. désactiver des tests pour obtenir un build vert ;
20. développer une marketplace avant stabilisation du DBPF-1.

---

# 196. DÉCISION ARCHITECTURALE FINALE

Le Business Pack System doit respecter :

```text
Business Pack
     │
     ├── distribution
     ├── metadata
     ├── versioning
     ├── validation
     ├── prompts
     └── templates
             │
             ▼
      Existing DocuForge
       Template Engine
             │
             ▼
      Document Generation
```

Le Business Pack System ne doit jamais devenir un deuxième moteur documentaire.

---

# 197. OBJECTIF COMMERCIAL À PRÉSERVER

Cette architecture doit permettre à DocuForge de proposer à terme :

```text
DocuForge Core
       +
       ├── Pack Artisan
       ├── Pack RH
       ├── Pack Association
       ├── Pack Conseil
       ├── Pack Immobilier
       └── Packs partenaires
```

avec trois sources potentielles de revenus :

```text
1. Licence / abonnement DocuForge

2. Vente de packs officiels

3. Future commission Marketplace
```

sans avoir à développer une application différente pour chaque secteur.

---

# 198. SUCCESS CRITERION GLOBAL

Le test conceptuel final du système est :

```text
Créer un nouveau métier
        ↓
Créer des DOCX
        ↓
Déclarer les variables
        ↓
Ajouter éventuellement prompts + samples
        ↓
Créer manifest
        ↓
ZIP
        ↓
Importer dans DocuForge
        ↓
Validation automatique
        ↓
Installation
        ↓
Templates immédiatement utilisables
```

Si l'ajout d'un nouveau métier nécessite de modifier le code Java ou React du moteur DocuForge, le Business Pack System n'a pas atteint son objectif architectural.
---

# 199. NOTE DE COHÉRENCE — AJOUTS TECHNIQUES (v1.1)

Les ajouts intégrés dans cette version (Apache Commons Compress §19, networknt/json-schema-validator §24, extension ClamAV §65, Apache Tika §137, réutilisation Apache POI §140, semver4j §10-11, réutilisation de la file PostgreSQL SKIP LOCKED §53, confirmation Zustand §128, extension de la pipeline CI §184) respectent tous les Règles Absolues du §195, en particulier la règle "ne duplique pas une abstraction déjà existante" : Commons Compress et json-schema-validator sont des ajouts ciblés au module Pack, tandis qu'Apache Tika, semver4j, ClamAV, la file de jobs, Zustand et la pipeline CI sont des extensions ou réutilisations strictes de dépendances déjà tranchées, soit ici soit au Core PRD DocuForge AI (v1.1). Aucun changement de stack imposée, aucune duplication d'abstraction.
