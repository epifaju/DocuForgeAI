import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const dir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../src/i18n/locales");

function walk(o, out = []) {
  if (typeof o === "string") out.push(o);
  else if (o && typeof o === "object") Object.values(o).forEach((v) => walk(v, out));
  return out;
}

const asciiStems =
  /\b(activite|generes?|genere|Termine|Succes|Echecs?|Parametres|Deconnexion|Creer|Creation|societe|donnees|metier|Francais|Precedent|Telecharg|Reussis|enregistree|previsual|Piece|Apercu|Requete|evenement|caracteres|Confidentialite|Expediteur|desinstalle|desactive|detecte|independante|retention|Irreversible|facon|Reecrire|Resumer|oublie|schema|premiere|demarrer|Prenom|Editeur|Personnalise|Selectionnez|duree|Telephone|inchange|preparer|deposez|apres|deja|etre)\b/i;

for (const file of ["fr.json", "pt.json"]) {
  const text = fs.readFileSync(path.join(dir, file), "utf8");
  const data = JSON.parse(text);
  const vals = walk(data);
  console.log("---", file, "accentChars=", (text.match(/[àâäéèêëïîôùûüçœÀÂÄÉÈÊËÏÎÔÙÛÜÇŒãõáíóúÃÕÁÍÓÚ]/g) || []).length);
  for (const s of vals) {
    if (asciiStems.test(s) || /Glissez-deposez|deposez/.test(s)) console.log("  ", s);
  }
}
