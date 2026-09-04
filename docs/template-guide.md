# Guide des templates

Création et configuration des modèles DOCX — PRD §§17–23, §67.

## Syntaxe des variables

Dans Microsoft Word / LibreOffice Writer, saisir des placeholders **Mustache** :

```text
{{client.firstName}}
{{client.email}}
{{invoice.total}}
```

Règles :

- Clé : lettres, chiffres, `_`, `.` (ex. `client.firstName`)
- Pas d’espaces dans la clé ; le libellé UI est dérivé automatiquement
- Les runs Word éclatés sont recombinés à la détection

## Types inférés (à l’upload)

| Mot-clé dans la clé | Type |
|---------------------|------|
| `email`, `mail` | EMAIL |
| `phone`, `tel`, `mobile` | PHONE |
| `date` | DATE |
| `total`, `amount`, `price`, `montant` | CURRENCY |
| `description`, `notes`, `comment` | LONG_TEXT |
| `count`, `qty`, `number` | NUMBER |
| sinon | TEXT |

Affiner ensuite via `PUT /api/v1/template-versions/{id}/variables` (label, required, validation).

## Workflow recommandé

1. Rédiger le DOCX (mise en page, styles, tableaux)  
2. Insérer les `{{variables}}`  
3. Créer le template (code unique, nom)  
4. Uploader la version (`setAsCurrent=true`)  
5. Vérifier les variables détectées  
6. **Activer** le template  
7. Tester une génération unitaire puis un batch CSV si besoin  

## Templates démo

Répertoire [`templates/demo/`](../templates/demo/) :

- `lettre-simple.docx` — courrier client  
- `facture.docx` — facture simple  
- `facture-batch.csv` — 2 lignes d’exemple  

Régénération :

```bash
cd tests/e2e && npm install
node ../../templates/demo/generate.mjs
```

## Import API (exemple)

```bash
TOKEN=…   # Bearer access token
curl -s -X POST http://localhost:18081/api/v1/templates \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"code":"lettre_simple","name":"Lettre simple","category":"demo"}'

# Puis multipart versions + activate — voir docs/api.md
```

## Bonnes pratiques

- Une variable = une information atomique  
- Éviter les clés ambigües (`data1`)  
- Versionner le DOCX dans Git sous `templates/` (sans secrets)  
- Après activation, préférer une **nouvelle version** plutôt que d’écraser silencieusement  
- Archiver plutôt que supprimer si des documents historiques existent  

## Formulaire dynamique

`GET /api/v1/template-versions/{id}/form-schema` alimente l’UI `/forms/:versionId`.  
Les clés avec points (`client.email`) sont gérées correctement côté formulaire (clés aplaties en interne).
