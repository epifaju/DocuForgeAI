# Templates démo

Fichiers DOCX prêts à importer dans DocuForge AI (placeholders `{{...}}`).

| Fichier | Code suggéré | Variables |
|---------|--------------|-----------|
| `lettre-simple.docx` | `lettre_simple` | `client.firstName`, `client.lastName`, `client.email` |
| `facture.docx` | `facture` | `client.firstName`, `client.lastName`, `client.email`, `invoice.number`, `invoice.date`, `invoice.total` |
| `facture-batch.csv` | — | CSV d’exemple pour `/batches` avec le template facture |

## Régénérer les DOCX

```bash
cd tests/e2e && npm install   # fournit jszip
node ../../templates/demo/generate.mjs
```

## Importer (UI)

1. Se connecter en ADMIN ou EDITOR  
2. `/templates` → créer le template → **Upload DOCX** → **Activer**  
3. Ouvrir **Formulaire**

## Importer (API)

```bash
# POST /api/v1/templates puis multipart /versions puis /activate
```

Voir [`docs/template-guide.md`](../../docs/template-guide.md).
