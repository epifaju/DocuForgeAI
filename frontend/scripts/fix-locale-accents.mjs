/**
 * Restore missing FR/PT accents in locale JSON (ASCII-stripped → proper UTF-8).
 * Run: node scripts/fix-locale-accents.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const localesDir = path.resolve(__dirname, "../src/i18n/locales");

function applyMap(text, pairs) {
  const sorted = [...pairs].sort((a, b) => b[0].length - a[0].length);
  let out = text;
  for (const [from, to] of sorted) {
    const esc = from.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    out = out.replace(new RegExp(esc, "g"), to);
  }
  return out;
}

/** French: longest / most specific first (list is re-sorted anyway). */
const frPairs = [
  ["d'oeil", "d'œil"],
  ["Documents generes aujourd", "Documents générés aujourd"],
  ["Documents generes", "Documents générés"],
  ["documents generes", "documents générés"],
  ["Activite recente", "Activité récente"],
  ["activite enregistree", "activité enregistrée"],
  ["Production, lots et activite", "Production, lots et activité"],
  ["activite", "activité"],
  ["A traiter", "À traiter"],
  ["Echecs", "Échecs"],
  ["Echec", "Échec"],
  ["Succes", "Succès"],
  ["Termine", "Terminé"],
  ["Genere", "Généré"],
  ["generes", "générés"],
  ["generee", "générée"],
  ["genere", "généré"],
  ["generez", "générez"],
  ["Generer", "Générer"],
  ["Generation", "Génération"],
  ["generation", "génération"],
  ["generations", "générations"],
  ["Precedent", "Précédent"],
  ["Creer", "Créer"],
  ["Creation", "Création"],
  ["Cree le", "Créé le"],
  ["creee", "créée"],
  ["cree ", "créé "],
  ["cree.", "créé."],
  ["cree,", "créé,"],
  ["cree)", "créé)"],
  ["creer", "créer"],
  ["Deconnexion", "Déconnexion"],
  ["Francais", "Français"],
  ["Parametres", "Paramètres"],
  ["parametres", "paramètres"],
  ["Packs metier", "Packs métier"],
  ["pack metier", "pack métier"],
  ["Documents metier", "Documents métier"],
  ["metier", "métier"],
  ["Mes donnees", "Mes données"],
  ["mes donnees", "mes données"],
  ["Donnees", "Données"],
  ["metadonnees", "métadonnées"],
  ["donnees", "données"],
  ["Reecrire", "Réécrire"],
  ["Resumer", "Résumer"],
  ["Redige", "Rédige"],
  ["adapte", "adapté"],
  ["facon", "façon"],
  ["Complete ce", "Complète ce"],
  ["Societe", "Société"],
  ["societe", "société"],
  ["oublie", "oublié"],
  ["Reinitialiser", "Réinitialiser"],
  ["Reinitialisation", "Réinitialisation"],
  ["envoye", "envoyé"],
  ["a ete", "a été"],
  ["Retour a ", "Retour à "],
  ["Mettre a jour", "Mettre à jour"],
  ["mis a jour", "mis à jour"],
  ["Mise a jour", "Mise à jour"],
  ["mise a jour", "mise à jour"],
  ["Telecharge", "Télécharge"],
  ["Telecharger", "Télécharger"],
  ["telechargee", "téléchargée"],
  ["telecharge", "téléchargé"],
  ["Televerse", "Téléversé"],
  ["expires", "expirés"],
  ["Retention", "Rétention"],
  ["retention", "rétention"],
  ["Irreversible", "Irréversible"],
  ["supprime(s)", "supprimé(s)"],
  ["supprime", "supprimé"],
  ["prets", "prêts"],
  ["recents", "récents"],
  ["enregistree", "enregistrée"],
  ["enregistres", "enregistrés"],
  ["Categorie", "Catégorie"],
  ["Demandez a ", "Demandez à "],
  ["d'en creer", "d'en créer"],
  ["importee", "importée"],
  ["archive.", "archivé."],
  ['"ARCHIVED": "Archive"', '"ARCHIVED": "Archivé"'],
  ["Reference", "Référence"],
  ["reference", "référence"],
  ["previsualisation", "prévisualisation"],
  ["Piece jointe", "Pièce jointe"],
  ["Apercu", "Aperçu"],
  ["apercu", "aperçu"],
  [" envoye a ", " envoyé à "],
  ["Email envoye a ", "Email envoyé à "],
  [" a {{recipient}}", " à {{recipient}}"],
  ["inchange", "inchangé"],
  ["preparer", "préparer"],
  ["Schema", "Schéma"],
  ["schema", "schéma"],
  ["aller a ", "aller à "],
  ["premiere", "première"],
  ["accepte", "accepté"],
  ["Detail", "Détail"],
  ["detail", "détail"],
  ["demarrer", "démarrer"],
  ["demarre", "démarré"],
  ["Traites", "Traités"],
  ["Reussis", "Réussis"],
  ["operations", "opérations"],
  ["evenement", "événement"],
  ["Entite", "Entité"],
  ["Acces ", "Accès "],
  ["refuse", "refusé"],
  ["Roles", "Rôles"],
  ["roles", "rôles"],
  ["Requete", "Requête"],
  ["terminee", "terminée"],
  ["termine", "terminé"],
  ["modifies", "modifiés"],
  ["Modifie", "Modifié"],
  ["modifie", "modifié"],
  ["demandee", "demandée"],
  ["exportees", "exportées"],
  ["purgees", "purgées"],
  ["Valide", "Validé"],
  ["valide", "validé"],
  ["echouee", "échouée"],
  ["Installe", "Installé"],
  ["installe", "installé"],
  ["Desinstalle", "Désinstallé"],
  ["desinstalle", "désinstallé"],
  ["Desinstaller", "Désinstaller"],
  ["desinstaller", "désinstaller"],
  ["exporte", "exporté"],
  ["Desactive", "Désactivé"],
  ["desactivee", "désactivée"],
  ["desactive", "désactivé"],
  ["Desactiver", "Désactiver"],
  ["desactiver", "désactiver"],
  ["Prenom", "Prénom"],
  ["etre ", "être "],
  ["caracteres", "caractères"],
  ["installes", "installés"],
  ["Modeles", "Modèles"],
  ["Modele", "Modèle"],
  ["modeles", "modèles"],
  ["modele", "modèle"],
  ["Cle pack", "Clé pack"],
  ["Editeur", "Éditeur"],
  ["conserves", "conservés"],
  ["Masque", "Masqué"],
  ["detectee", "détectée"],
  ["detectes", "détectés"],
  ["detecte", "détecté"],
  ["superieure", "supérieure"],
  ["independante", "indépendante"],
  ["Personnalise", "Personnalisé"],
  ["Deposez", "Déposez"],
  ["Selectionnez", "Sélectionnez"],
  ["acceptes", "acceptés"],
  ["quand meme", "quand même"],
  ["pret a ", "prêt à "],
  ["prete", "prête"],
  ["deja ", "déjà "],
  ["retires", "retirés"],
  ["retire", "retiré"],
  ["ajoutees", "ajoutées"],
  ["ajoutee", "ajoutée"],
  ["Ajoute", "Ajouté"],
  ["ajoute", "ajouté"],
  ["Expire", "Expiré"],
  ["Defectueux", "Défectueux"],
  ["affichee", "affichée"],
  ["defaut", "défaut"],
  ["Expediteur", "Expéditeur"],
  ["expediteur", "expéditeur"],
  ["Confidentialite", "Confidentialité"],
  ["duree", "durée"],
  ["purges", "purgés"],
  ["Telephone", "Téléphone"],
  ["apres ", "après "],
  ["Apres ", "Après "],
  ["a la ", "à la "],
  ["a un ", "à un "],
  // verbs / labels that must stay infinitive or adjective (fix over-eager "active"→"activé")
];

const ptPairs = [
  ["sessao", "sessão"],
  ["Pagina", "Página"],
  ["Frances", "Francês"],
  ["Portugues", "Português"],
  ["Navegacao", "Navegação"],
  ["Definicoes", "Definições"],
  ["definicoes", "definições"],
  ["negocio", "negócio"],
  ["indisponivel", "indisponível"],
  ["Disponivel", "Disponível"],
  ["disponivel", "disponível"],
  ["fricao", "fricção"],
  ["formularios", "formulários"],
  ["Formulario", "Formulário"],
  ["formulario", "formulário"],
  ["dinamicos", "dinâmicos"],
  ["espaco", "espaço"],
  ["Inicio", "Início"],
  ["inicio", "início"],
  ["Nao ", "Não "],
  [" nao ", " não "],
  ["Sera ", "Será "],
  ["ligacao", "ligação"],
  ["Ligacao", "Ligação"],
  ["Redefinicao", "Redefinição"],
  ["Exportacao", "Exportação"],
  ["exportacao", "exportação"],
  ["Retencao", "Retenção"],
  ["retencao", "retenção"],
  ["eliminacao", "eliminação"],
  ["Irreversivel", "Irreversível"],
  ["Producao", "Produção"],
  ["producao", "produção"],
  ["num so ", "num só "],
  ["Este mes", "Este mês"],
  ["IA / mes", "IA / mês"],
  ["Codigo", "Código"],
  ["codigo", "código"],
  ["Descricao", "Descrição"],
  ["descricao", "descrição"],
  ["Versao", "Versão"],
  ["versao", "versão"],
  ["Versoes", "Versões"],
  ["versoes", "versões"],
  ["Acoes", "Ações"],
  ["acoes", "ações"],
  ["comecar", "começar"],
  ["Peca ", "Peça "],
  ["Referencia", "Referência"],
  ["referencia", "referência"],
  ["Titulo", "Título"],
  ["titulo", "título"],
  ["Concluido", "Concluído"],
  ["concluido", "concluído"],
  ["Pre-visualizacao", "Pré-visualização"],
  ["pre-visualizacao", "pré-visualização"],
  ["Confirmacao", "Confirmação"],
  ["confirmacao", "confirmação"],
  ["obrigatorias", "obrigatórias"],
  ["obrigatoria", "obrigatória"],
  ["obrigatorios", "obrigatórios"],
  ["obrigatorio", "obrigatório"],
  ["Destinatario", "Destinatário"],
  ["destinatario", "destinatário"],
  [" sao ", " são "],
  ["Geracao", "Geração"],
  ["geracao", "geração"],
  ["Secoes", "Seções"],
  ["cabecalhos", "cabeçalhos"],
  [" as chaves", " às chaves"],
  ["Variaveis", "Variáveis"],
  ["variaveis", "variáveis"],
  ["Variavel", "Variável"],
  ["variavel", "variável"],
  ["Validacao", "Validação"],
  ["validacao", "validação"],
  ["Acao", "Ação"],
  ["operacoes", "operações"],
  ["sensiveis", "sensíveis"],
  ["Papeis", "Papéis"],
  ["papeis", "papéis"],
  ["conversao", "conversão"],
  ["Instalacao", "Instalação"],
  ["instalacao", "instalação"],
  ["copia", "cópia"],
  ["Opcoes", "Opções"],
  ["opcoes", "opções"],
  ["apos ", "após "],
  ["ja esta", "já está"],
  ["Voltar a lista", "Voltar à lista"],
  ["Alteracoes", "Alterações"],
  ["alteracoes", "alterações"],
  ["alteracao", "alteração"],
  ["incompativeis", "incompatíveis"],
  ["incompativel", "incompatível"],
  ["Valido", "Válido"],
  ["Invalido", "Inválido"],
  ["invalido", "inválido"],
  ["invalida", "inválida"],
  ["Configuracao", "Configuração"],
  ["duracao", "duração"],
  ["Numero", "Número"],
  ["numero", "número"],
  ["Minimo", "Mínimo"],
  ["E necessario", "É necessário"],
  ["Nome proprio", "Nome próprio"],
  ["nome proprio", "nome próprio"],
  ["edicao", "edição"],
  ["historicos", "históricos"],
  ["historico", "histórico"],
  ["exclusao", "exclusão"],
  ["passara", "passará"],
  ["deixara", "deixará"],
  ["geracoes", "gerações"],
  ["acessiveis", "acessíveis"],
  ["Atualizacao", "Atualização"],
  ["atualizacao", "atualização"],
  ["Revisao", "Revisão"],
  ["revisao", "revisão"],
  ["analise", "análise"],
  ["Recomecar", "Recomeçar"],
  ["Assistencia", "Assistência"],
  ["substituicao", "substituição"],
  ["anexavel", "anexável"],
  ["impossivel", "impossível"],
  ["So leitura", "Só leitura"],
];

function fixFrOverreach(obj) {
  // Replacements like active→activé break infinitives / adjectives.
  const walk = (node, key) => {
    if (typeof node === "string") return node;
    if (Array.isArray(node)) return node.map((v) => walk(v, key));
    if (node && typeof node === "object") {
      for (const [k, v] of Object.entries(node)) {
        if (typeof v === "string") {
          // Restore known bad substitutions
          if (k === "activate" || k === "enable" || v === "Activé") {
            if (["activate", "enable"].includes(k)) node[k] = "Activer";
          }
          if (k === "active" && v === "Activé") node[k] = "Actif";
          if (k === "platformOn" && (v === "activé" || v === "Activé")) node[k] = "activée";
          if (k === "smtpOn" && (v === "activé" || v === "Activé")) node[k] = "actif";
          if (k === "available" && v === "disponible") node[k] = "disponible";
          // Status ACTIVE label
          if (key === "statusLabel" && k === "ACTIVE" && (v === "Activé" || v === "activé")) {
            node[k] = "Actif";
          }
          if (k === "enabled" && v.includes("activé.")) node[k] = v.replace("activé.", "activé.");
          // "Activer" may have become "Activér" if we had active→activé then... we didn't.
          // Fix "pack d'abord" path: templateNeedsPack
          if (k === "templateNeedsPack" && v.includes("Activéz")) node[k] = "Activez le pack d'abord";
          if (k === "templateNeedsPack" && v.startsWith("Activé")) node[k] = "Activez le pack d'abord";
          // Verb Activer in actions
          if ((k === "activate" || k === "enable") && v === "Activé") node[k] = "Activer";
          // packs.enable / templates.activate
        } else {
          walk(v, k);
        }
      }
    }
    return node;
  };
  walk(obj, null);

  // Explicit safe restores via path
  const set = (parts, value) => {
    let cur = obj;
    for (let i = 0; i < parts.length - 1; i++) cur = cur[parts[i]];
    cur[parts[parts.length - 1]] = value;
  };

  set(["templates", "activate"], "Activer");
  set(["templates", "statusLabel", "ACTIVE"], "Actif");
  set(["users", "active"], "Actif");
  set(["users", "enable"], "Activer");
  set(["packs", "enable"], "Activer");
  set(["packs", "enabled"], "Pack activé.");
  set(["packs", "templateEnabled"], "Template activé.");
  set(["packs", "templateNeedsPack"], "Activez le pack d'abord");
  set(["packs", "import", "enablePack"], "Activer le pack après installation");
  set(["packs", "import", "enableTemplates"], "Activer les templates après installation");
  set(["settings", "platformOn"], "activée");
  set(["settings", "platformOff"], "désactivée");
  set(["settings", "smtpOn"], "actif");
  set(["settings", "available"], "disponible");
  set(["audit", "actionLabel", "TEMPLATE_ACTIVATED"], "Template activé");
  set(["audit", "actionLabel", "PACK_ENABLED"], "Pack activé");
  set(["audit", "actionLabel", "PACK_TEMPLATE_ENABLED"], "Template pack activé");
  set(["documents", "statusLabel", "COMPLETED"], "Terminé");
  set(["batches", "statusLabel", "COMPLETED"], "Terminé");
  set(["audit", "statusLabel", "SUCCESS"], "Succès");
  set(["packs", "import", "step", "success"], "Succès");
  // "Valide" as status should stay "Valide" (adjective) not "Validé"
  set(["packs", "import", "status", "VALID"], "Valide");
  set(["packs", "import", "status", "INVALID"], "Invalide");
  set(["packs", "import", "ready"], "Pack valide et prêt à installer.");
  set(["audit", "actionLabel", "PACK_VALIDATED"], "Pack validé");

  return obj;
}

function processFile(name, pairs, postFix) {
  const filePath = path.join(localesDir, name);
  const raw = fs.readFileSync(filePath);
  if (raw[0] === 0xef && raw[1] === 0xbb && raw[2] === 0xbf) {
    throw new Error(`${name}: unexpected UTF-8 BOM`);
  }
  let text = raw.toString("utf8");
  JSON.parse(text);
  text = applyMap(text, pairs);
  let data = JSON.parse(text);
  if (postFix) data = postFix(data);
  const out = `${JSON.stringify(data, null, 2)}\n`;
  fs.writeFileSync(filePath, out, { encoding: "utf8" });
  const accents = (out.match(/[àâäéèêëïîôùûüçœÀÂÄÉÈÊËÏÎÔÙÛÜÇŒãõáíóúÃÕÁÍÓÚ]/g) || []).length;
  console.log(`${name}: wrote UTF-8 (no BOM), accentChars=${accents}`);
}

processFile("fr.json", frPairs, fixFrOverreach);
processFile("pt.json", ptPairs, null);
