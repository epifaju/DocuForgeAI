# Installeur Windows — DocuForge AI

Installeur grand public (wizard + barre système) pour postes Windows 11, **sans licence Docker Desktop**.  
Runtime conteneurs : **Rancher Desktop** (voir aussi [DEPLOYMENT_WINDOWS.md](../DEPLOYMENT_WINDOWS.md)).

---

## Guide utilisateur final (après installation)

### Faut-il seulement exécuter `DocuForgeAI-Setup-0.1.0.exe` ?

**Oui** pour le parcours client : double-cliquer l’installeur **en administrateur**.

Sur un poste neuf, prévoyez toutefois :

1. **Droits administrateur** (WSL2 / Rancher).
2. **WSL2** peut exiger un **redémarrage**, puis de **relancer** l’installeur.
3. **Rancher Desktop** doit être disponible (déjà installé, ou embarqué dans le build — voir ci-dessous).
4. **Images Docker** : si le pack offline n’a pas été inclus au build, le poste a besoin de **réseau** (ou d’images embarquées).
5. **Ressources** : ~4 Go RAM recommandés (plus avec l’IA / Ollama).

Sans Rancher ni images dans le `.exe`, ce n’est **pas** encore un déploiement 100 % offline / clé USB autonome.

### Lancer l’application

- Raccourci **Bureau** ou **menu Démarrer** « DocuForge AI » → ouvre le navigateur.
- **Icône barre système** (près de l’horloge) → clic droit → **Ouvrir DocuForge AI**.
- Si les services sont arrêtés : même menu → **Démarrer les services**, attendre le statut « en ligne ».

### Accéder à l’interface

URL (port choisi à l’installation, **5174** par défaut) :

```text
http://localhost:5174
```

### Se connecter

Identifiants saisis dans le wizard d’installation :

| Champ | Valeur |
|-------|--------|
| Société | Identifiant dérivé du nom d’organisation (slug, ex. `mon-organisation`) |
| Email | E-mail administrateur indiqué à l’install |
| Mot de passe | Mot de passe administrateur indiqué à l’install |

### En cas de problème

- Vérifier que **Rancher Desktop** est démarré.
- Barre système → **Voir les logs** : `%ProgramData%\DocuForgeAI\logs\`
- Barre système → **Démarrer les services** / **Redémarrer les services**

---

## Contenu du dépôt `installer/`

| Chemin | Rôle |
|--------|------|
| `DocuForge.iss` | Script Inno Setup 6 (wizard FR) |
| `scripts/` | Orchestration silencieuse (env, compose, prereqs, backup, tray) |
| `tray/` | App WPF .NET 8 (barre système) |
| `payload/compose/` | docker-compose + `.env.example` |
| `payload/images/` | Pack offline optionnel (`*.tar`) |
| `payload/prereqs/` | Installeur Rancher à embarquer |
| `Launch-Tray.cmd` | Lance le tray EXE ou le fallback PowerShell |
| `Open-DocuForge.cmd` | Ouvre l’UI dans le navigateur |
| `dist/` | Sortie du build (`DocuForgeAI-Setup-*.exe`) — non versionnée |

> Les scripts d’installeur sont sous **`installer/scripts/`**, pas sous `DocuForgeAI/scripts/` (racine).

---

## Prérequis de build (équipe / CI)

1. **Windows** + PowerShell 5.1+
2. **.NET 8 SDK** — [download](https://dotnet.microsoft.com/download/dotnet/8.0) (`dotnet --list-sdks`)
3. **Inno Setup 6** — `ISCC.exe` sous `C:\Program Files (x86)\Inno Setup 6\` ([jrsoftware.org](https://jrsoftware.org/isinfo.php))
4. **Docker** (Rancher ou autre) — pour construire / exporter les images

Sans SDK .NET, le build empaquette le tray PowerShell (`scripts/DocuForge.Tray.ps1`) via `Launch-Tray.cmd`.

---

## Construire l’installeur

```powershell
cd C:\Users\epifa\cursor-workspace\DocuForgeAI\installer

# 1) À la racine du repo : construire les images applicatives
cd ..
docker compose build
cd installer

# 2) Packager
powershell -File .\scripts\Build-Installer.ps1
```

Variantes :

```powershell
powershell -File .\scripts\Build-Installer.ps1 -SkipImages
powershell -File .\scripts\Build-Installer.ps1 -SkipInno
```

Sortie attendue : `installer\dist\DocuForgeAI-Setup-0.1.0.exe`

### Les images et Rancher sont-ils dans l’installeur ?

**Pas automatiquement.** Par défaut, `payload/images/` et `payload/prereqs/` ne contiennent que des `README.txt`.  
Vérifiez avant de redistribuer le `.exe` :

```powershell
Get-ChildItem .\payload\images
Get-ChildItem .\payload\prereqs
```

### Pack images offline

Le script est ici (pas dans `DocuForgeAI\scripts\`) :

```powershell
cd installer
powershell -File .\scripts\Export-OfflineImages.ps1
# → installer\payload\images\*.tar
```

Si une image manque : `docker compose build` à la racine du repo, puis relancer l’export.  
Au premier démarrage client, `Start-Stack.ps1` charge ces tar via `Load-OfflineImages.ps1` puis lance Compose.

### Embarquer Rancher Desktop

Rancher **n’est pas** livré dans ce dépôt. Téléchargement officiel :

- Releases : https://github.com/rancher-sandbox/rancher-desktop/releases  
- Doc : https://docs.rancherdesktop.io/getting-started/installation/

Sur Windows récent, le fichier est souvent un **MSI** :

```text
Rancher.Desktop.Setup.X.Y.Z.msi
```

Placez-le dans :

```text
installer\payload\prereqs\
```

> Note : le script d’install cherche encore historiquement `RancherDesktopSetup.exe`. Pour un MSI, adapter `Install-Prereqs.ps1` (`msiexec`) ou installer Rancher manuellement sur le poste avant / pendant le déploiement.

Puis rebuild :

```powershell
powershell -File .\scripts\Build-Installer.ps1
```

---

## Comportement côté client (wizard)

1. Configuration FR : organisation, e-mail/mot de passe admin, port UI (défaut 5174), IA optionnelle  
2. Génération de `%ProgramData%\DocuForgeAI\.env` (secrets auto, ACL restreintes, **jamais** en clair dans les logs)  
3. Détection / installation WSL2 + Rancher (redémarrage possible pour WSL)  
4. `docker compose` self-host + attente health  
5. Raccourcis Bureau / Démarrer + icône barre système  
6. Désinstallation : **conserve les données** par défaut ; suppression seulement après confirmation explicite  

---

## Mises à jour

- Tray : **Vérifier les mises à jour** → `Update-Stack.ps1` (URL optionnelle `DOCUFORGE_UPDATE_URL` dans `.env`)  
- Exemple : [`payload/update-manifest.example.json`](payload/update-manifest.example.json)  
- Canal manuel : redistribuer un nouvel `.exe` Inno (volumes conservés si l’utilisateur ne supprime pas les données)

---

## Licence runtime

**Ne pas embarquer Docker Desktop** pour les organismes publics / consulats : abonnement Docker souvent requis.  
**Rancher Desktop** est le runtime retenu pour ce canal client.

---

## Logs

`%ProgramData%\DocuForgeAI\logs\` — secrets redactés.
