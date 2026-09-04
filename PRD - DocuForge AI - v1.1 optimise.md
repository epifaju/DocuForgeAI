# PRD — Intelligent Document Generator

**Nom de travail :** DocuForge AI  
**Version :** 1.1 (optimisée — décisions techniques tranchées §9, §10, §39 ; sections 109 à 121 ajoutées)  
**Statut :** MVP / spécification destinée à Cursor  
**Marché initial :** TPE/PME françaises  
**Marchés futurs :** associations, cabinets professionnels, collectivités et organismes publics  
**Mode initial :** Self-hosted / Docker  
**Langue initiale :** Français  
**Devise :** EUR  
**Fuseau par défaut :** Europe/Paris

---

# 1. OBJECTIF DU PRODUIT

Construire une application permettant à une organisation de créer automatiquement des documents professionnels à partir :

- de modèles DOCX ;
- de formulaires ;
- de données métier ;
- de fichiers CSV/JSON ;
- d'API ;
- de données saisies manuellement ;
- et, facultativement, de contenu généré ou reformulé par IA.

Exemples :

- devis ;
- attestations ;
- contrats ;
- courriers ;
- rapports ;
- propositions commerciales ;
- comptes rendus ;
- certificats ;
- convocations ;
- lettres personnalisées ;
- documents administratifs.

Le système doit permettre de transformer :

```text
Template + Data + Business Rules
              ↓
        Document Engine
              ↓
          DOCX / PDF
```

Le produit doit être utilisable sans connaissances en programmation après son installation.

---

# 2. PROPOSITION DE VALEUR

## Problème

De nombreuses organisations produisent régulièrement les mêmes documents en modifiant manuellement :

- nom ;
- adresse ;
- date ;
- montant ;
- référence ;
- coordonnées ;
- clauses ;
- tableaux ;
- paragraphes ;
- signatures ;
- informations client.

Cette activité provoque :

- perte de temps ;
- erreurs de copier-coller ;
- incohérences ;
- utilisation d'anciens modèles ;
- documents incomplets ;
- absence de traçabilité.

## Solution

DocuForge AI centralise les modèles et automatise la génération documentaire.

Exemple :

```text
Utilisateur
    ↓
Choisit "Attestation"
    ↓
Formulaire dynamique
    ↓
Validation des données
    ↓
Prévisualisation
    ↓
Génération
    ↓
DOCX + PDF
    ↓
Archivage
    ↓
Téléchargement / Email
```

---

# 3. OBJECTIFS DU MVP

Le MVP doit permettre :

1. créer un modèle documentaire ;
2. importer un template DOCX ;
3. définir ses variables ;
4. générer automatiquement un formulaire ;
5. saisir les données ;
6. valider les données ;
7. générer un DOCX ;
8. convertir le DOCX en PDF ;
9. prévisualiser le résultat ;
10. télécharger le document ;
11. conserver l'historique ;
12. créer une nouvelle version ;
13. envoyer le document par email ;
14. générer plusieurs documents en batch ;
15. utiliser l'IA pour certaines zones de texte ;
16. journaliser les opérations.

---

# 4. NON-OBJECTIFS DU MVP

Ne PAS développer immédiatement :

- ERP ;
- CRM complet ;
- GED complète ;
- signature électronique qualifiée ;
- paiement ;
- application mobile native ;
- marketplace ;
- éditeur DOCX complet dans le navigateur ;
- collaboration temps réel ;
- Kubernetes ;
- microservices complexes ;
- moteur BPM complet ;
- SaaS multi-tenant complet ;
- facturation SaaS ;
- OCR avancé ;
- reconnaissance manuscrite.

---

# 5. PERSONAS

## Persona A — TPE

Exemple :

artisan, agence, consultant, cabinet.

Produit régulièrement :

- devis ;
- courriers ;
- attestations ;
- rapports.

---

## Persona B — PME

Plusieurs salariés produisent des documents à partir de modèles communs.

Problèmes :

- versions différentes ;
- modèles obsolètes ;
- erreurs ;
- manque de contrôle.

---

## Persona C — Administration

Produit :

- attestations ;
- courriers ;
- convocations ;
- certificats ;
- décisions ;
- formulaires personnalisés.

Nécessite :

- audit ;
- validation ;
- historique ;
- confidentialité.

---

## Persona D — Consultant / intégrateur

Installe DocuForge AI chez plusieurs clients.

Souhaite :

- Docker ;
- API ;
- webhooks ;
- templates exportables ;
- automatisation n8n.

---

# 6. STACK TECHNIQUE IMPOSÉE

## Backend

```text
Java 21+
Spring Boot
Spring Web
Spring Data JPA
Spring Security
Bean Validation
Flyway
Spring Actuator
OpenAPI / Swagger
Maven
Lombok
MapStruct
```

Cursor ne doit PAS remplacer Spring Boot par Node.js, Python ou une autre technologie sans instruction explicite.

### Conventions de code

```text
Lombok      → réduit le boilerplate getters/setters/constructeurs sur les entités (§46-55)
MapStruct   → mapping Entity ↔ DTO explicite et testable, évite le mapping manuel répétitif entre les tables du §46 et leurs représentations API
ApiResponse<T> / PageResponse<T>  → enveloppe de réponse uniforme sur toutes les routes du §56-63, à définir avant la Phase 4
BaseEntity  → id, createdAt, updatedAt, createdBy factorisés, au lieu d'être redéfinis sur chaque entité du §46
```

---

# 7. FRONTEND

```text
React
TypeScript
Vite
Tailwind CSS
React Router
TanStack Query
React Hook Form
Zod
shadcn/ui
Zustand
react-pdf
```

`shadcn/ui` fournit des composants accessibles cohérents avec Tailwind, pertinents pour rendre de façon uniforme les types de champs variés du formulaire dynamique (§20, §23). `Zustand` gère l'état UI global (étape courante d'un wizard de génération, filtres du Document Repository §32), distinct de l'état serveur déjà couvert par TanStack Query. `react-pdf` (wrapper pdf.js) permet le rendu inline du PDF généré pour la prévisualisation (§31), plutôt qu'un simple lien de téléchargement.

---

# 8. DATABASE

```text
PostgreSQL
```

Toutes les modifications du schéma doivent être réalisées avec Flyway.

Interdiction :

```text
spring.jpa.hibernate.ddl-auto=update
```

Utiliser :

```text
validate
```

dans les environnements contrôlés.

---

# 9. DOCUMENT ENGINE

Le moteur doit être isolé derrière une interface.

Concept :

```java
public interface DocumentGenerator {

    GeneratedDocument generate(
        Template template,
        Map<String, Object> data
    );

}
```

### Décision — bibliothèque de fusion DOCX

```text
docx-stamper (org.wickedsource:docx-stamper)
```

Construit au-dessus d'Apache POI. Choisi plutôt qu'un remplacement par regex brute sur le XML car Word éclate fréquemment une même variable (`{{client.name}}`) sur plusieurs runs internes (`<w:r>`) après une correction orthographique ou un collage — un remplacement naïf échoue silencieusement sur une proportion significative de documents réels produits par des utilisateurs non techniques (personas A/B/C). docx-stamper gère nativement la fusion de runs éclatés, résout les variables via une syntaxe d'expression proche de Spring EL (cohérent avec §19), et supporte les blocs conditionnels et répétitions — utile pour anticiper `REPEATING_SECTION` (§20, prévu "ultérieurement"). Préserve le formatage Word, exigence déjà posée ci-dessus.

Ce choix reste implémenté derrière l'interface `DocumentGenerator` définie plus haut — aucun changement d'architecture.

---

# 10. CONVERSION PDF

La conversion doit être encapsulée derrière :

```java
public interface PdfConverter {

    Path convert(Path sourceDocument);

}
```

Implémentation MVP recommandée :

```text
LibreOffice headless
```

Le backend ne doit jamais dépendre directement de commandes LibreOffice dispersées dans le code.

### Décision — mécanisme d'appel

```text
JODConverter (org.jodconverter:jodconverter-local ou jodconverter-remote)
```

Choisi plutôt qu'un `ProcessBuilder` invoquant `soffice --headless --convert-to pdf` à chaque génération, car cette approche naïve provoque en production : un coût de démarrage de process LibreOffice à chaque appel (plusieurs secondes), un risque de process zombies en cas de génération concurrente (cf. §70), et l'absence de garantie de libération du verrou de profil utilisateur LibreOffice sous charge. JODConverter maintient un **pool de process LibreOffice persistants** et gère nativement les timeouts (§73) et les tentatives limitées (§72). Le mode `jodconverter-remote` est compatible avec le conteneur `libreoffice` isolé prévu au §13, en exposant LibreOffice via une API HTTP dédiée — ce qui respecte l'exigence ci-dessus de ne jamais disperser de commandes shell dans le code métier.

L'interface `PdfConverter` définie ci-dessus n'est pas modifiée — seule l'implémentation change.

---

# 11. IA

IA locale par défaut :

```text
Ollama
```

Architecture :

```text
AIProvider
   │
   ├── OllamaAIProvider
   │
   ├── OpenAIProvider      [future]
   ├── MistralProvider     [future]
   └── AnthropicProvider   [future]
```

L'utilisation de l'IA doit rester facultative.

La génération documentaire traditionnelle doit fonctionner sans IA.

---

# 12. ARCHITECTURE

```text
                     USER
                       │
                       ▼
              ┌─────────────────┐
              │ React Frontend  │
              └────────┬────────┘
                       │
                    HTTPS
                       │
                       ▼
              ┌─────────────────┐
              │ Spring Boot API │
              └──────┬────┬─────┘
                     │    │
             ┌───────┘    └─────────┐
             ▼                      ▼
       PostgreSQL                 Ollama
             │
             │
             ▼
       Document Engine
             │
             ▼
        DOCX generated
             │
             ▼
      LibreOffice Headless
             │
             ▼
             PDF
```

---

# 13. DOCKER

Le MVP doit contenir :

```text
postgres
backend
frontend
ollama
libreoffice
mailpit
```

Prévoir éventuellement :

```text
n8n
minio
reverse-proxy (Traefik)
redis
clamav
```

mais ne pas les rendre obligatoires au MVP.

`Traefik` est retenu pour le reverse-proxy plutôt que Nginx : auto-découverte native des services Docker Compose via labels (pas de fichier de config séparé à maintenir) et renouvellement TLS Let's Encrypt automatique — pertinent pour le persona D (intégrateur déployant chez plusieurs clients avec un minimum de configuration manuelle par site).

---

# 14. ARBORESCENCE

```text
docuforge-ai/
│
├── PRD.md
├── README.md
├── CHANGELOG.md
├── LICENSE
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── .gitignore
│
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   └── resources/
│       └── test/
│
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   └── src/
│
├── document-engine/
│   └── README.md
│
├── infrastructure/
│   ├── libreoffice/
│   └── postgres/
│
├── templates/
│   └── demo/
│
├── storage/
│   ├── templates/
│   ├── generated/
│   └── temporary/
│
├── scripts/
│   ├── install.sh
│   ├── install.ps1
│   ├── backup.sh
│   ├── restore.sh
│   └── healthcheck.sh
│
├── docs/
│   ├── architecture.md
│   ├── installation.md
│   ├── template-guide.md
│   ├── api.md
│   ├── security.md
│   ├── backup.md
│   └── troubleshooting.md
│
└── tests/
    ├── integration/
    └── e2e/
```

---

# 15. MODULES

Le MVP doit être divisé en :

```text
M01 Authentication
M02 Template Management
M03 Variable Management
M04 Dynamic Forms
M05 Document Generation
M06 PDF Conversion
M07 Document Repository
M08 Version Management
M09 AI Assistance
M10 Batch Generation
M11 Email Distribution
M12 Audit
M13 Administration
```

---

# 16. AUTHENTIFICATION

## Endpoint

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

## Rôles MVP

```text
ADMIN
EDITOR
USER
VIEWER
```

### ADMIN

Administration complète.

### EDITOR

Gestion des templates et génération.

### USER

Génération de documents.

### VIEWER

Consultation uniquement.

Toutes les autorisations doivent être contrôlées côté backend.

Masquer un bouton React n'est PAS un contrôle d'autorisation.

---

# 17. TEMPLATE MANAGEMENT

Un template représente un modèle documentaire.

Exemples :

```text
Attestation de travail
Proposition commerciale
Contrat de prestation
Courrier client
Rapport d'intervention
```

Un template possède :

```text
id
company_id
name
code
description
category
status
current_version
created_by
created_at
updated_at
```

Statuts :

```text
DRAFT
ACTIVE
ARCHIVED
```

---

# 18. TEMPLATE VERSION

Chaque modification importante crée une version.

```text
template
   │
   ├── version 1
   ├── version 2
   └── version 3
```

Une génération doit toujours référencer précisément la version utilisée.

Un document historique ne doit jamais changer lorsqu'un template est modifié.

---

# 19. SYNTAXE DES VARIABLES

Convention MVP :

```text
{{client.name}}
{{client.address}}
{{document.date}}
{{invoice.total}}
{{company.name}}
```

Exemple dans Word :

```text
Je soussigné {{company.managerName}}, représentant de
{{company.name}}, atteste que {{employee.firstName}}
{{employee.lastName}}...
```

---

# 20. TYPES DE VARIABLES

Support minimum :

```text
TEXT
LONG_TEXT
NUMBER
DECIMAL
DATE
DATETIME
BOOLEAN
EMAIL
PHONE
SELECT
MULTISELECT
CURRENCY
```

Prévoir ultérieurement :

```text
IMAGE
SIGNATURE
TABLE
REPEATING_SECTION
```

---

# 21. DÉFINITION D'UNE VARIABLE

```json
{
  "key": "client.name",
  "label": "Nom du client",
  "type": "TEXT",
  "required": true,
  "defaultValue": null,
  "placeholder": "Entreprise Dupont",
  "validation": {
    "minLength": 2,
    "maxLength": 150
  }
}
```

---

# 22. EXTRACTION AUTOMATIQUE DES VARIABLES

Lors de l'import d'un DOCX :

```text
Upload
 ↓
Analyse
 ↓
Détection {{...}}
 ↓
Liste des variables
 ↓
Validation utilisateur
 ↓
Création du formulaire
```

Exemple :

DOCX :

```text
Bonjour {{client.firstName}},

Votre référence est {{case.reference}}.
```

Le système détecte :

```text
client.firstName
case.reference
```

---

# 23. FORMULAIRES DYNAMIQUES

Le frontend ne doit pas nécessiter un formulaire React codé pour chaque template.

Le formulaire doit être généré depuis les métadonnées des variables.

Pipeline :

```text
Template
 ↓
Template Variables
 ↓
Form Schema
 ↓
React Dynamic Form
```

---

# 24. VALIDATION

Validation frontend pour UX.

Validation backend obligatoire pour sécurité et intégrité.

Exemple :

```text
email → valid email
date → valid ISO date
currency → decimal >= 0
required → non-null
```

Ne jamais faire confiance uniquement à la validation frontend.

---

# 25. GENERATION

Workflow :

```text
Select template
      ↓
Load current active version
      ↓
Build dynamic form
      ↓
Enter data
      ↓
Validate
      ↓
Preview request
      ↓
Generate DOCX
      ↓
Convert PDF
      ↓
Store
      ↓
Audit
```

---

# 26. ÉTATS D'UNE GÉNÉRATION

```text
PENDING
VALIDATING
GENERATING
GENERATED
CONVERTING
COMPLETED
FAILED
REVIEW_REQUIRED
```

Les transitions invalides doivent être rejetées.

---

# 27. DOCUMENT GÉNÉRÉ

Métadonnées :

```text
id
company_id
template_id
template_version_id
reference
title
status
data_snapshot
docx_path
pdf_path
checksum
created_by
created_at
updated_at
```

`data_snapshot` doit conserver les données utilisées lors de la génération.

---

# 28. IMMUTABILITÉ

Un document `COMPLETED` doit être considéré comme un artefact historique.

Si l'utilisateur veut le modifier :

```text
Original
   ↓
Create New Version
   ↓
New Generated Document
```

Ne pas remplacer silencieusement l'original.

---

# 29. RÉFÉRENCE DOCUMENTAIRE

Créer un service dédié :

```java
DocumentReferenceService
```

Exemple :

```text
DOC-2026-000001
DOC-2026-000002
```

Le format doit être configurable ultérieurement.

La génération doit être concurrency-safe.

---

# 30. CHECKSUM

Calculer SHA-256 du fichier final.

Stocker :

```text
checksum_algorithm = SHA-256
checksum_value
```

Objectif :

détecter les modifications accidentelles.

Ce mécanisme ne constitue pas une signature électronique.

---

# 31. PRÉVISUALISATION

L'utilisateur doit pouvoir voir le PDF avant téléchargement/envoi.

Actions :

```text
Download DOCX
Download PDF
Create new version
Send email
```

---

# 32. DOCUMENT REPOSITORY

Page :

```text
Documents
```

Colonnes :

```text
Reference
Title
Template
Version
Status
Created By
Created At
Actions
```

Filtres :

```text
reference
template
status
date
creator
```

Recherche paginée côté serveur.

---

# 33. STOCKAGE

Créer abstraction :

```java
public interface StorageProvider {

    StoredFile store(...);

    InputStream read(...);

    void delete(...);

}
```

MVP :

```text
LocalStorageProvider
```

Future :

```text
S3StorageProvider
MinioStorageProvider
AzureBlobStorageProvider
```

Le code métier ne doit jamais construire directement des chemins filesystem.

---

# 34. SÉCURITÉ DES FICHIERS

Obligatoire :

- UUID pour les noms physiques ;
- validation extension ;
- validation MIME ;
- limite de taille ;
- empêcher path traversal ;
- fichiers hors répertoire Web public ;
- aucune exécution ;
- nettoyage des fichiers temporaires.

Nom original conservé uniquement comme métadonnée.

### Analyse antivirus

Chaque template DOCX importé (§17) et chaque fichier CSV de batch (§39) proviennent d'un upload utilisateur, potentiellement partagé entre plusieurs organisations en installation mutualisée (cf. §91). Un DOCX peut embarquer des macros ou objets OLE malveillants qu'une validation MIME/extension ne détecte pas.

```text
ClamAV (conteneur clamav/clamav, scan via socket TCP avant persistance en storage)
```

Conteneur additionnel optionnel dans le `docker-compose.yml` (§13), activable via `ANTIVIRUS_ENABLED=` (§77) — désactivable par défaut pour ne pas complexifier l'installation d'un artisan solo (persona A).

---

# 35. AI ASSISTANCE

L'IA doit assister la rédaction, jamais contrôler tout le moteur documentaire.

Cas MVP :

```text
REWRITE
SUMMARIZE
EXPAND
SHORTEN
FORMALIZE
GENERATE_PARAGRAPH
```

Exemple :

Champ :

```text
{{proposal.executiveSummary}}
```

L'utilisateur saisit :

```text
Migration logiciel client vers cloud.
Durée 3 mois.
Budget 25k.
```

IA :

```text
→ proposition de paragraphe professionnel
```

L'utilisateur doit pouvoir :

```text
Accept
Edit
Reject
Regenerate
```

---

# 36. AI FIELD

Une variable peut avoir :

```json
{
  "key": "proposal.summary",
  "type": "LONG_TEXT",
  "aiEnabled": true,
  "aiMode": "GENERATE_PARAGRAPH"
}
```

Mais la valeur finale doit être enregistrée dans le `data_snapshot`.

---

# 37. PROMPT MANAGEMENT

Prompts séparés du code :

```text
backend/src/main/resources/prompts/
```

Exemples :

```text
formalize-fr.txt
summarize-fr.txt
generate-commercial-proposal-fr.txt
rewrite-professional-fr.txt
```

Les prompts doivent être versionnés.

---

# 38. AI GATEWAY

```java
public interface AIProvider {

    AIResponse generate(AIRequest request);

}
```

MVP :

```text
OllamaAIProvider
```

Configuration :

```text
AI_ENABLED=
AI_PROVIDER=
OLLAMA_BASE_URL=
OLLAMA_MODEL=
AI_TIMEOUT_SECONDS=
```

Si l'IA est indisponible :

le système documentaire traditionnel doit continuer à fonctionner.

### Rate limiting

```text
Bucket4j (com.bucket4j:bucket4j-core)
```

Empêche un utilisateur ou un script de saturer Ollama (ou un futur provider payant OpenAI/Mistral/Anthropic, §11). Buckets en mémoire pour le MVP mono-instance ; backend Redis en option si l'application devient multi-instance (Redis déjà listé en optionnel §13). Le même mécanisme protège `/api/v1/auth/login` (§16) contre le brute-force.

---

# 39. BATCH GENERATION

Parsing CSV : **Apache Commons CSV** (`org.apache.commons:commons-csv`), qui gère nativement les cas limites (guillemets, séparateurs variables, encodage) qu'un parsing manuel via `String.split()` gère mal — pertinent puisque les fichiers CSV batch proviendront typiquement d'un export Excel utilisateur, avec ses particularités d'encodage et de séparateur selon la locale.

Permettre :

```text
CSV
 ↓
Mapping
 ↓
Validation
 ↓
N documents
 ↓
ZIP
```

Exemple CSV :

```text
firstname,lastname,email
Alice,Martin,alice@example.test
Jean,Dupont,jean@example.test
```

Template :

```text
Bonjour {{firstname}} {{lastname}}
```

Résultat :

```text
document-Alice-Martin.pdf
document-Jean-Dupont.pdf
```

---

# 40. LIMITES BATCH

Valeur MVP configurable :

```text
BATCH_MAX_ROWS=500
```

Un batch ne doit pas bloquer une requête HTTP pendant plusieurs minutes.

Prévoir traitement asynchrone.

### Décision — mécanisme technique

```text
Table batch_jobs (§53) + colonnes status, locked_at, locked_by
SELECT ... FOR UPDATE SKIP LOCKED pour la reprise de jobs
Spring @Async + ThreadPoolTaskExecutor dédié (pool séparé du pool HTTP)
```

Choisi plutôt qu'une dépendance à Redis/une queue externe, cohérent avec la contrainte "pas de microservices complexes" (§4). Le pattern `SKIP LOCKED` sur PostgreSQL est la solution standard pour une file de jobs fiable et concurrency-safe sans infrastructure additionnelle — même logique de sûreté déjà exigée pour `DocumentReferenceService` (§29). Le `ThreadPoolTaskExecutor` dédié évite qu'un batch volumineux n'affame les threads servant les requêtes HTTP interactives.

Évolution post-MVP si le volume dépasse ce qu'une file PostgreSQL simple absorbe confortablement : **Spring Batch** (chunk processing, restart-on-failure natif) au-dessus de Redis, déjà listé en optionnel §13.

---

# 41. BATCH STATUS

```text
CREATED
VALIDATING
PROCESSING
COMPLETED
PARTIALLY_FAILED
FAILED
```

Stocker :

```text
total
processed
success
failed
```

---

# 42. EMAIL

Après génération :

```text
Send by email
```

Champs :

```text
recipient
subject
message
attachmentFormat
```

Formats :

```text
PDF
DOCX
BOTH
```

En développement :

```text
SMTP → Mailpit
```

---

# 43. EMAIL SAFETY

Avant envoi :

- validation email ;
- document existant ;
- contrôle permission ;
- journalisation ;
- limite de taille des pièces jointes.

Le MVP doit demander confirmation utilisateur avant l'envoi.

---

# 44. AUDIT

Toutes les opérations sensibles doivent être enregistrées.

Événements :

```text
LOGIN
TEMPLATE_CREATED
TEMPLATE_UPDATED
TEMPLATE_ACTIVATED
TEMPLATE_ARCHIVED
DOCUMENT_GENERATED
DOCUMENT_DOWNLOADED
DOCUMENT_EMAILED
DOCUMENT_VERSION_CREATED
AI_REQUEST
BATCH_STARTED
BATCH_COMPLETED
SETTINGS_CHANGED
```

---

# 45. AUDIT LOG

```text
id
company_id
user_id
action
entity_type
entity_id
status
ip_address
metadata
created_at
```

Ne jamais enregistrer :

```text
password
JWT
API key
SMTP password
secret
```

---

# 46. MODÈLE DE DONNÉES

Tables minimum :

```text
companies
users
roles
user_roles

templates
template_versions
template_variables

generated_documents
document_versions

generation_jobs

batch_jobs
batch_items

ai_requests

email_deliveries

audit_logs

application_settings
```

---

# 47. COMPANIES

```text
id UUID PK
name VARCHAR(200)
identifier VARCHAR(100)
created_at TIMESTAMP
updated_at TIMESTAMP
```

---

# 48. USERS

```text
id UUID PK
company_id UUID FK
email VARCHAR(255)
password_hash VARCHAR(255)
first_name VARCHAR(100)
last_name VARCHAR(100)
enabled BOOLEAN
created_at TIMESTAMP
updated_at TIMESTAMP
```

Unique :

```text
company_id + email
```

---

# 49. TEMPLATES

```text
id UUID PK
company_id UUID FK
code VARCHAR(100)
name VARCHAR(200)
description TEXT
category VARCHAR(100)
status VARCHAR(30)
current_version_id UUID
created_by UUID
created_at TIMESTAMP
updated_at TIMESTAMP
```

Unique :

```text
company_id + code
```

---

# 50. TEMPLATE_VERSIONS

```text
id UUID PK
template_id UUID FK
version_number INTEGER
original_filename VARCHAR(255)
storage_key VARCHAR(500)
checksum VARCHAR(64)
created_by UUID
created_at TIMESTAMP
```

Unique :

```text
template_id + version_number
```

---

# 51. TEMPLATE_VARIABLES

```text
id UUID PK
template_version_id UUID FK
variable_key VARCHAR(200)
label VARCHAR(200)
type VARCHAR(50)
required BOOLEAN
default_value TEXT
placeholder TEXT
display_order INTEGER
configuration JSONB
created_at TIMESTAMP
```

Unique :

```text
template_version_id + variable_key
```

---

# 52. GENERATED_DOCUMENTS

```text
id UUID PK
company_id UUID FK
template_id UUID FK
template_version_id UUID FK
reference VARCHAR(100)
title VARCHAR(255)
status VARCHAR(30)
data_snapshot JSONB
docx_storage_key VARCHAR(500)
pdf_storage_key VARCHAR(500)
checksum VARCHAR(64)
created_by UUID
created_at TIMESTAMP
```

Unique :

```text
company_id + reference
```

---

# 53. BATCH_JOBS

```text
id UUID PK
company_id UUID FK
template_version_id UUID FK
status VARCHAR(30)
total_items INTEGER
processed_items INTEGER
successful_items INTEGER
failed_items INTEGER
created_by UUID
created_at TIMESTAMP
started_at TIMESTAMP
completed_at TIMESTAMP
```

---

# 54. AI_REQUESTS

Stocker :

```text
id
company_id
user_id
provider
model
operation
prompt_version
status
duration_ms
created_at
```

Ne pas stocker systématiquement l'intégralité des données sensibles envoyées au LLM.

---

# 55. INDEXES

Créer au minimum des indexes sur :

```text
users(company_id,email)

templates(company_id,status)
templates(company_id,code)

generated_documents(company_id,reference)
generated_documents(company_id,created_at)
generated_documents(template_id)

audit_logs(company_id,created_at)

batch_jobs(company_id,status)
```

---

# 56. API

Base :

```text
/api/v1
```

---

# 57. TEMPLATE API

```text
GET    /templates
POST   /templates
GET    /templates/{id}
PUT    /templates/{id}
DELETE /templates/{id}

POST /templates/{id}/versions
GET  /templates/{id}/versions
GET  /templates/{id}/versions/{version}

POST /templates/{id}/activate
POST /templates/{id}/archive
```

---

# 58. VARIABLE API

```text
GET /template-versions/{id}/variables

PUT /template-versions/{id}/variables
```

---

# 59. GENERATION API

```text
POST /documents/generate

POST /documents/preview

GET /documents
GET /documents/{id}

GET /documents/{id}/download/docx
GET /documents/{id}/download/pdf

POST /documents/{id}/email
POST /documents/{id}/new-version
```

---

# 60. BATCH API

```text
POST /batches
GET  /batches
GET  /batches/{id}
GET  /batches/{id}/errors
GET  /batches/{id}/download
```

---

# 61. AI API

```text
POST /ai/rewrite
POST /ai/summarize
POST /ai/formalize
POST /ai/generate
```

Tous doivent être authentifiés.

---

# 62. ADMIN API

```text
GET /admin/users
POST /admin/users

GET /admin/settings
PUT /admin/settings

GET /audit
```

---

# 63. API ERROR FORMAT

Toutes les erreurs doivent utiliser une structure cohérente :

```json
{
  "timestamp": "2026-09-03T12:00:00Z",
  "status": 400,
  "code": "INVALID_TEMPLATE_DATA",
  "message": "Les données du document sont invalides.",
  "details": [
    {
      "field": "client.email",
      "message": "Adresse email invalide."
    }
  ],
  "traceId": "..."
}
```

Ne jamais retourner une stack trace en production.

---

# 64. HTTP STATUS

Respecter :

```text
200 OK
201 CREATED
202 ACCEPTED
204 NO CONTENT

400 BAD REQUEST
401 UNAUTHORIZED
403 FORBIDDEN
404 NOT FOUND
409 CONFLICT
413 PAYLOAD TOO LARGE
422 UNPROCESSABLE ENTITY
500 INTERNAL SERVER ERROR
503 SERVICE UNAVAILABLE
```

---

# 65. FRONTEND

Routes :

```text
/login

/dashboard

/templates
/templates/new
/templates/:id
/templates/:id/edit

/generate
/generate/:templateId

/documents
/documents/:id

/batches
/batches/:id

/audit

/settings
/settings/company
/settings/users
/settings/ai
/settings/email
```

---

# 66. DASHBOARD

Afficher :

```text
Documents generated today
Documents generated this month
Active templates
Failed generations
Batch jobs
AI usage
Recent documents
Recent activity
```

---

# 67. TEMPLATE CREATION UX

Wizard :

```text
STEP 1
Upload DOCX

STEP 2
Detect variables

STEP 3
Configure variables

STEP 4
Preview form

STEP 5
Test generation

STEP 6
Activate template
```

Impossible d'activer un template qui échoue au test de génération.

---

# 68. TEMPLATE VALIDATION

Avant activation :

vérifier :

- DOCX lisible ;
- variables valides ;
- absence de doublons incohérents ;
- noms de variables valides ;
- génération de test réussie ;
- conversion PDF réussie si PDF activé.

---

# 69. CONVENTION VARIABLE KEY

Regex recommandée :

```text
^[a-zA-Z][a-zA-Z0-9_.]{0,199}$
```

Refuser notamment :

```text
../
/
\
{{}}
scripts
```

selon contexte.

---

# 70. CONCURRENCE

Deux utilisateurs peuvent générer simultanément des documents.

Aucun fichier temporaire ne doit utiliser un nom prévisible commun tel que :

```text
output.docx
```

Utiliser :

```text
UUID
```

pour les espaces temporaires.

---

# 71. TRANSACTIONS

Une génération ne doit pas créer un document `COMPLETED` si le fichier final n'a pas été produit correctement.

Le workflow doit gérer explicitement les erreurs entre :

```text
database
filesystem
DOCX generation
PDF conversion
```

---

# 72. RETRY

Les opérations externes peuvent avoir un retry limité :

```text
Ollama
SMTP
LibreOffice
```

Ne pas effectuer de retry infini.

Les politiques doivent être configurables.

---

# 73. TIMEOUT

Prévoir :

```text
AI timeout
PDF conversion timeout
SMTP timeout
```

Une dépendance bloquée ne doit pas bloquer indéfiniment les threads backend.

---

# 74. HEALTHCHECKS

Backend :

```text
/actuator/health
```

Contrôler :

```text
database
storage
```

Ollama et LibreOffice peuvent avoir des indicateurs distincts afin qu'une panne IA ne rende pas nécessairement toute l'application indisponible.

---

# 75. LOGGING

Utiliser logging structuré.

Inclure :

```text
timestamp
level
traceId
userId
operation
```

Ne pas logger :

```text
password
JWT
API key
document complet
données personnelles inutilement
```

### Implémentation

```text
Logback + logstash-logback-encoder → sortie JSON structurée
MDC (Mapped Diagnostic Context) → propagation de traceId/generationId à travers tous les logs d'une même génération (§76)
```

---

# 76. OBSERVABILITÉ

Chaque génération doit avoir :

```text
generationId
traceId
```

Ils doivent permettre de suivre :

```text
HTTP request
→ validation
→ document generation
→ conversion
→ storage
```

`Micrometer` (inclus transitivement par Spring Actuator, §6) expose ces métriques via `/actuator/metrics`.

Post-MVP, à ne considérer qu'après validation commerciale (cohérent §4) : stack **Grafana + Loki + Tempo**, autohébergeable en Docker, pour l'agrégation de logs et le tracing distribué — utile notamment au persona D gérant plusieurs installations clientes.

---

# 77. CONFIGURATION

`.env.example` :

```text
APP_ENV=
APP_BASE_URL=

POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=

BACKEND_PORT=
FRONTEND_PORT=

JWT_SECRET=
JWT_ACCESS_EXPIRATION=
JWT_REFRESH_EXPIRATION=

STORAGE_ROOT=
MAX_UPLOAD_SIZE_MB=

PDF_CONVERSION_ENABLED=
LIBREOFFICE_URL=
PDF_CONVERSION_TIMEOUT=

AI_ENABLED=
AI_PROVIDER=
OLLAMA_BASE_URL=
OLLAMA_MODEL=
AI_TIMEOUT_SECONDS=

SMTP_HOST=
SMTP_PORT=
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=

BATCH_MAX_ROWS=

ANTIVIRUS_ENABLED=
CLAMAV_HOST=
CLAMAV_PORT=

RATE_LIMIT_LOGIN_PER_MINUTE=
RATE_LIMIT_AI_PER_MINUTE=

DEFAULT_TIMEZONE=Europe/Paris
```

---

# 78. LOCAL DEVELOPMENT

Le développeur doit pouvoir lancer :

```bash
docker compose up -d
```

Puis accéder à :

```text
Frontend
Backend API
Swagger
Mailpit
```

Documenter les ports dans README.

---

# 79. MAILPIT

Aucun email réel ne doit partir depuis la configuration DEV par défaut.

Mailpit doit permettre de tester :

```text
recipient
subject
body
attachments
```

---

# 80. SEED

Créer :

```text
1 company
1 admin
3 users

5 templates

20 generated documents

2 batch jobs

30 audit records
```

Templates de démonstration :

```text
Attestation
Courrier
Devis
Rapport
Proposition commerciale
```

---

# 81. TESTS UNITAIRES

Couverture prioritaire :

```text
variable validation
template parsing
reference generation
document generation
status transitions
permissions
checksum
AI gateway
storage
```

### Qualité de code

```text
Backend : Spotless (formatage) + Checkstyle (règles) — plugin Maven
Frontend : ESLint + Prettier
```

À exécuter en pre-commit (`husky` + `lint-staged` côté frontend) et intégrés à la CI (§109) pour qu'aucune régression de style ne dépende de la discipline manuelle d'un agent Cursor.

---

# 82. TESTCONTAINERS

Utiliser Testcontainers pour les tests nécessitant PostgreSQL lorsque pertinent.

Ne pas remplacer PostgreSQL par H2 pour valider des comportements PostgreSQL spécifiques.

---

# 83. TEST D'INTÉGRATION — TEMPLATE

```text
Upload DOCX
→ detect variables
→ configure
→ test generation
→ activate
```

Résultat attendu :

```text
ACTIVE template
```

---

# 84. TEST D'INTÉGRATION — DOCUMENT

```text
Select active template
→ submit valid data
→ generate DOCX
→ convert PDF
→ checksum
→ persist
```

Résultat :

```text
COMPLETED
```

---

# 85. TEST D'INTÉGRATION — INVALID DATA

```text
required field missing
```

Attendu :

```text
422
```

Aucun document final créé.

---

# 86. TEST D'INTÉGRATION — PDF FAILURE

Simuler panne LibreOffice.

Attendu :

- erreur contrôlée ;
- génération non marquée COMPLETED ;
- fichiers temporaires nettoyés ;
- audit créé ;
- message utilisateur compréhensible.

---

# 87. TEST D'INTÉGRATION — AI FAILURE

Arrêter Ollama.

La génération traditionnelle doit toujours fonctionner.

Les fonctions IA doivent retourner une erreur contrôlée.

---

# 88. TEST D'INTÉGRATION — EMAIL

```text
Generate
→ Email
→ Mailpit
```

Vérifier :

```text
recipient
subject
body
attachment filename
attachment content
```

---

# 89. TEST D'INTÉGRATION — BATCH

Importer 100 lignes.

Attendu :

```text
100 items processed
```

Si 5 sont invalides :

```text
95 success
5 failed
PARTIALLY_FAILED
```

L'utilisateur doit pouvoir télécharger le rapport d'erreurs.

---

# 90. TESTS DE SÉCURITÉ

Minimum :

```text
unauthenticated API
wrong role
path traversal upload
invalid MIME
oversized upload
malicious filename
invalid JWT
expired JWT
cross-company resource access
```

---

# 91. MULTI-TENANT READINESS

Même si l'installation MVP correspond généralement à une entreprise :

toutes les principales données doivent être rattachées à :

```text
company_id
```

Un utilisateur de Company A ne doit jamais pouvoir accéder aux données de Company B.

Créer des tests spécifiques.

---

# 92. RGPD

Prévoir :

- minimisation ;
- suppression ;
- export ;
- rétention configurable ;
- IA locale ;
- audit ;
- documentation des traitements.

Ne jamais afficher :

```text
"100% RGPD"
"certifié RGPD"
```

sans base juridique correspondante.

---

# 93. BACKUP

Créer :

```text
scripts/backup.sh
scripts/restore.sh
```

Backup :

```text
PostgreSQL
templates
generated documents
configuration nécessaire
```

---

# 94. RESTORE TEST

La documentation doit décrire :

```text
fresh installation
→ restore DB
→ restore storage
→ start
→ verify documents
```

Un backup non restaurable n'est pas considéré comme valide.

---

# 95. SUPPRESSION

La suppression d'un template ayant servi à générer des documents historiques ne doit pas casser ces documents.

Privilégier :

```text
ARCHIVED
```

plutôt que suppression physique.

---

# 96. PERFORMANCE MVP

Objectifs indicatifs sur machine raisonnable :

```text
API standard < 500 ms hors opérations lourdes
pagination obligatoire pour listes
génération unitaire asynchrone si nécessaire
batch traité en background
```

Ne pas optimiser prématurément.

---

# 97. ACCESSIBILITÉ

Frontend :

- labels de formulaire ;
- navigation clavier ;
- messages d'erreur lisibles ;
- contrastes suffisants ;
- boutons identifiables ;
- états loading accessibles.

---

# 98. INTERNATIONALISATION

Même si MVP = français :

ne pas disperser les textes dans les composants.

Préparer :

```text
fr
```

et architecture future :

```text
en
pt
es
```

---

# 99. EXPORT / IMPORT TEMPLATE

Prévoir dans V1.1 :

```text
template package
```

contenant :

```text
template DOCX
metadata
variables
version
```

Objectif futur :

permettre de vendre des packs de modèles.

---

# 100. WEBHOOKS — FUTURE READY

Préparer architecture permettant :

```text
document.generated
document.failed
batch.completed
```

afin de connecter ultérieurement :

```text
n8n
Make
Zapier
ERP
CRM
```

Ne pas développer une infrastructure événementielle complexe dans le MVP.

---

# 101. CRITÈRES D'ACCEPTATION MVP

Le MVP est terminé lorsque :

1. installation Docker fonctionne ;
2. utilisateur peut se connecter ;
3. rôles fonctionnent ;
4. DOCX peut être importé ;
5. variables sont détectées ;
6. variables peuvent être configurées ;
7. formulaire est généré dynamiquement ;
8. données invalides sont rejetées ;
9. DOCX peut être généré ;
10. PDF peut être généré ;
11. PDF peut être prévisualisé ;
12. DOCX/PDF peuvent être téléchargés ;
13. génération est historisée ;
14. version du template est conservée ;
15. checksum est calculé ;
16. IA peut reformuler un champ ;
17. panne IA n'empêche pas génération normale ;
18. batch CSV fonctionne ;
19. erreurs batch sont consultables ;
20. document peut être envoyé vers Mailpit ;
21. audit fonctionne ;
22. contrôle d'accès fonctionne ;
23. isolation company fonctionne ;
24. backup existe ;
25. documentation d'installation existe ;
26. tests automatisés passent ;
27. aucun secret réel n'est présent dans Git.

---

# 102. DEFINITION OF DONE

Une fonctionnalité n'est PAS terminée simplement parce que l'interface fonctionne.

Definition of Done :

```text
Implementation
+
Database migration
+
Backend validation
+
Authorization
+
Error handling
+
Audit where applicable
+
Unit tests
+
Integration tests
+
Frontend states
+
Documentation
+
No secret
```

---

# 103. RÈGLES CURSOR

Cursor doit considérer :

```text
PRD.md
```

comme source fonctionnelle principale.

Cursor doit :

1. lire le PRD avant chaque phase ;
2. analyser le code existant ;
3. conserver l'architecture ;
4. travailler par petites itérations ;
5. compiler après modification ;
6. exécuter les tests ;
7. corriger les erreurs ;
8. documenter les décisions importantes.

Cursor ne doit PAS :

- réécrire tout le projet inutilement ;
- changer la stack ;
- supprimer un test pour faire passer le build ;
- ignorer une erreur ;
- ajouter des secrets ;
- inventer des dépendances externes ;
- implémenter plusieurs phases sans autorisation ;
- modifier une migration Flyway déjà appliquée pour corriger le schéma.

Créer une nouvelle migration.

---

# 104. PLAN D'IMPLÉMENTATION

## Phase 0 — Repository

Créer :

```text
repository
PRD.md
README
.gitignore
.env.example
directories
Docker Compose skeleton
```

---

## Phase 1 — Infrastructure

Créer :

```text
PostgreSQL
Mailpit
Ollama
LibreOffice
```

Ajouter healthchecks.

---

## Phase 2 — Backend Skeleton

Spring Boot :

```text
Web
JPA
Validation
Security
Flyway
Actuator
OpenAPI
```

---

## Phase 3 — Database

Créer migrations pour :

```text
companies
users
roles
templates
template_versions
template_variables
generated_documents
batch_jobs
audit
```

---

## Phase 4 — Authentication

Créer :

```text
login
refresh
logout
/me
RBAC
```

Tests sécurité.

---

## Phase 5 — Storage

Implémenter :

```text
StorageProvider
LocalStorageProvider
```

Tests :

```text
store
read
delete
path traversal
```

---

## Phase 6 — Template Management

CRUD templates.

Versioning.

Upload DOCX.

---

## Phase 7 — Variable Parser

Analyser DOCX.

Détecter :

```text
{{variable}}
```

Créer les variables.

---

## Phase 8 — Dynamic Forms

API schema.

React form renderer.

Validation frontend/backend.

---

## Phase 9 — DOCX Engine

Remplacer les variables.

Conserver formatage.

Créer fichier final.

Tester cas limites.

---

## Phase 10 — PDF

LibreOffice headless.

Conversion.

Timeout.

Cleanup.

---

## Phase 11 — Repository

Liste documents.

Recherche.

Filtres.

Preview.

Downloads.

---

## Phase 12 — Versioning

Versions templates.

Versions documents.

Immutabilité historique.

---

## Phase 13 — AI

Créer :

```text
AIProvider
OllamaAIProvider
```

Puis :

```text
rewrite
formalize
summarize
generate paragraph
```

---

## Phase 14 — Batch

CSV.

Mapping.

Validation.

Background processing.

ZIP.

Error report.

---

## Phase 15 — Email

SMTP.

Confirmation.

Attachments.

Mailpit tests.

---

## Phase 16 — Audit

Finaliser audit global et vérifier couverture des événements.

---

## Phase 17 — Dashboard

KPIs.

Recent activity.

---

## Phase 18 — Security Hardening

Tester :

```text
uploads
permissions
tenant isolation
JWT
secrets
logs
```

---

## Phase 19 — Backup

Backup.

Restore.

Documentation.

---

## Phase 20 — E2E

Tester tous les critères d'acceptation.

Outil : **Playwright**, retenu plutôt que Cypress car il permet de tester directement le téléchargement des fichiers générés (DOCX/PDF, §31) et l'upload de templates (§17) — deux parcours critiques du produit — via une API native d'inspection des fichiers téléchargés, plus robuste que l'équivalent Cypress sur ce point.

---

## Phase 21 — Packaging

Préparer release :

```text
docker-compose.yml
.env.example
README
installation guide
admin guide
template guide
demo templates
```

---

# 105. ORDRE STRICT

Cursor ne doit PAS commencer par le frontend.

Ordre :

```text
Infrastructure
     ↓
Database
     ↓
Security
     ↓
Storage
     ↓
Templates
     ↓
Variables
     ↓
DOCX
     ↓
PDF
     ↓
Documents
     ↓
AI
     ↓
Batch
     ↓
Email
     ↓
Frontend final
     ↓
Hardening
```

---

# 106. PREMIER PROMPT À DONNER À CURSOR

Tu es l'agent de développement principal du projet DocuForge AI.

Le fichier `PRD.md` constitue la source de vérité fonctionnelle et architecturale.

Lis intégralement `PRD.md` avant toute modification.

Nous allons construire le système de manière incrémentale.

Pour cette itération, travaille UNIQUEMENT sur :

**Phase 0 — Repository**

Ne commence aucune autre phase.

Avant d'écrire les fichiers :

1. analyse les exigences de la Phase 0 ;
2. vérifie les contraintes globales du PRD qui affectent cette phase ;
3. propose l'arborescence initiale ;
4. liste les fichiers à créer ;
5. liste les décisions techniques nécessaires ;
6. signale toute contradiction éventuelle dans le PRD.

Ensuite seulement, implémente la Phase 0.

Crée notamment :

```text
PRD.md
README.md
.gitignore
.env.example
docker-compose.yml
docker-compose.dev.yml

backend/
frontend/
infrastructure/
storage/
templates/
scripts/
docs/
tests/
```

Le `docker-compose.yml` de cette phase peut être un squelette cohérent avec l'architecture future, mais ne développe aucune fonctionnalité métier.

Respecte les règles suivantes :

- aucun secret réel ;
- aucune dépendance métier non nécessaire ;
- versions des images Docker explicitement définies lorsque pertinent ;
- volumes nommés lorsque nécessaire ;
- réseau Docker clairement défini ;
- variables externalisées ;
- compatibilité Windows + Docker Desktop prise en compte ;
- ne pas utiliser de chemins absolus propres à une machine.

Exécute ensuite les validations disponibles, notamment :

```bash
docker compose config
```

Vérifie également :

```text
.gitignore
.env.example
repository structure
Docker syntax
```

Ne masque aucune erreur.

À la fin, fournis un compte rendu structuré :

```text
PHASE
Phase 0

FILES CREATED
...

DECISIONS
...

VALIDATIONS
...

ERRORS
...

NEXT PHASE
Phase 1 — Infrastructure
```

STOP.

N'implémente PAS la Phase 1 tant que je ne t'ai pas explicitement demandé de continuer.

---

# 107. PROMPT STANDARD POUR CHAQUE PHASE SUIVANTE

Utilise le prompt suivant pour éviter que Cursor parte trop loin :

```text
Relis PRD.md.

Analyse l'état actuel du repository et les résultats de la phase précédente.

Implémente UNIQUEMENT la prochaine phase autorisée.

Avant modification :
- indique les exigences concernées ;
- indique les fichiers qui seront modifiés/créés ;
- identifie les risques de régression.

Après implémentation :
- compile ;
- lance les tests concernés ;
- lance les validations d'infrastructure nécessaires ;
- corrige les erreurs ;
- vérifie qu'aucun secret n'a été introduit.

Ne désactive aucun test pour obtenir un build vert.

Ne commence aucune phase suivante.

Fournis le compte rendu final puis STOP.
```

---

# 108. VISION POST-MVP

Une fois le MVP validé commercialement, le même moteur pourra évoluer vers une plateforme plus large :

```text
                    DocuForge Platform

                         Templates
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
       Forms              APIs              CSV
          │                 │                 │
          └─────────────────┼─────────────────┘
                            ▼
                     Document Engine
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
             DOCX          PDF            AI
              │             │              │
              └─────────────┼──────────────┘
                            ▼
                       Repository
                            │
        ┌───────────────────┼──────────────────┐
        ▼                   ▼                  ▼
      Email               API               n8n
```

Évolutions commerciales possibles :

- génération par API ;
- formulaires publics ;
- bibliothèque de templates ;
- packs métiers ;
- génération massive ;
- workflow d'approbation ;
- signatures ;
- connecteurs CRM ;
- connecteurs ERP ;
- Microsoft 365 ;
- Google Workspace ;
- n8n ;
- API REST ;
- SaaS multi-tenant ;
- white-label ;
- portail client.

L'objectif architectural est de permettre ces évolutions sans transformer le MVP initial en plateforme excessivement complexe.
---

# 109. CI/CD

Le PRD initial ne prévoyait aucune pipeline d'intégration continue, alors que le §103 (Règles Cursor) exige explicitement que chaque phase compile et passe ses tests. Sans CI, cette discipline dépend uniquement de l'exécution manuelle.

```text
.github/workflows/ci.yml (GitHub Actions)
  - backend: mvn verify (compile + tests unitaires + Testcontainers, §82)
  - backend: spotless:check + checkstyle:check (§81)
  - frontend: npm run lint && npm run build
  - docker: docker compose config (validation déjà exigée en Phase 0, §106)
```

Déclenché sur chaque pull request. Pas de déploiement automatisé au MVP (cohérent avec le mode self-hosted/Docker manuel du produit, §4) — la CI se limite à la validation, pas au déploiement.

---

# 110. NOTE DE COHÉRENCE — AJOUTS TECHNIQUES (v1.1)

Les ajouts intégrés dans cette version (docx-stamper §9, JODConverter §10, file de jobs PostgreSQL §40, ClamAV §34, Bucket4j §38, Logback/Micrometer §75-76, Traefik §13, shadcn/ui/Zustand/react-pdf §7, Lombok/MapStruct §6, Apache Commons CSV §39, Playwright Phase 20, CI/CD §109) respectent tous les Règles Cursor du §103 : aucun ne remplace la stack imposée, chacun s'insère dans une interface déjà prévue par le PRD (`DocumentGenerator`, `PdfConverter`, `StorageProvider`, `AIProvider`) ou tranche un choix explicitement laissé ouvert par le document d'origine. Ils peuvent être introduits phase par phase, au moment où le module concerné est implémenté (ex. docx-stamper en Phase 9, JODConverter en Phase 10, Bucket4j/ClamAV en Phase 18 Security Hardening, CI/CD dès la Phase 0).
