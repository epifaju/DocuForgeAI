# Pack Artisan Demo

**Pack id:** `com.docuforge.pack.artisan-demo`  
**Version:** `1.0.0`  
**Type:** `OFFICIAL` (demo interne)  
**PRD:** §§161–164 / Phase 21

Pack de démonstration DocuForge pour artisans et petites entreprises.

## Contenu

| Élément | Détail |
|---------|--------|
| Templates | `ARTISAN_DEVIS`, `ARTISAN_INTERVENTION`, `ARTISAN_COMPLETION_CERTIFICATE` |
| Prompts | `ARTISAN_WORK_DESCRIPTION`, `ARTISAN_INTERVENTION_NOTES` |
| Samples | JSON (`artisan-devis.json`) + CSV (`artisan-intervention.csv`) |
| Previews | PNG pour chaque template |

Toutes les données sont **fictives**. L’attestation de fin de travaux est un document **privé de démonstration**, pas un acte officiel.

## Artefact

Le ZIP distributable est généré par :

```text
ai.docuforge.businesspack.demo.ArtisanDemoPackFactory
```

Fichier recommandé :

```text
docuforge-pack-artisan-demo-1.0.0.zip
```

Emplacement documenté :

```text
docs/business-packs/packs/artisan-demo/dist/docuforge-pack-artisan-demo-1.0.0.zip
```

## Import

```http
POST /api/v1/admin/business-packs/import
```

Puis validate → install. Compatible `minimumDocuForgeVersion: 0.1.0`.

## Régénération

```bash
cd backend
mvn -Dtest=ArtisanDemoPackFactoryTest,ArtisanDemoPackApiTest test
# puis script de materialisation (voir PHASE_21.md)
```
