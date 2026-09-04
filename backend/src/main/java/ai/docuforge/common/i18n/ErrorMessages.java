package ai.docuforge.common.i18n;

import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves API error messages for FR/PT. Accepts either an {@code error.*} key
 * or a legacy French reason string (mapped to a key).
 */
@Component
public class ErrorMessages {

    private final MessageSource messageSource;

    /** Exact French strings currently thrown by services → message keys. */
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("Societe introuvable", "error.company.not_found"),
            Map.entry("Société introuvable", "error.company.not_found"),
            Map.entry("Utilisateur introuvable", "error.user.not_found"),
            Map.entry("Document introuvable", "error.document.not_found"),
            Map.entry("Template introuvable", "error.template.not_found"),
            Map.entry("Version introuvable", "error.template_version.not_found"),
            Map.entry("Version de template introuvable", "error.template_version.not_found"),
            Map.entry("Batch introuvable", "error.batch.not_found"),
            Map.entry("Identifiants invalides", "error.auth.invalid_credentials"),
            Map.entry("Refresh token invalide", "error.auth.invalid_refresh"),
            Map.entry("Refresh token invalide ou révoqué", "error.auth.invalid_refresh_revoked"),
            Map.entry("Compte désactivé", "error.auth.account_disabled"),
            Map.entry("Token type invalide", "error.auth.invalid_token_type"),
            Map.entry("Token invalide", "error.auth.invalid_token"),
            Map.entry("Token expirÃ©", "error.auth.token_expired"),
            Map.entry("Token expiré", "error.auth.token_expired"),
            Map.entry("Authentification requise.", "error.auth.required"),
            Map.entry("Acces refuse.", "error.forbidden"),
            Map.entry("Les donnees fournies sont invalides.", "error.validation"),
            Map.entry("Les donnees du formulaire sont invalides.", "error.form.invalid"),
            Map.entry("Une erreur interne est survenue.", "error.internal"),
            Map.entry("Trop de tentatives de connexion. Reessayez plus tard.", "error.rate_limit.login"),
            Map.entry("Limite d'appels IA atteinte. Reessayez plus tard.", "error.rate_limit.ai"),
            Map.entry("Le nom de la societe est requis.", "error.settings.company_name_required"),
            Map.entry("Adresse expediteur invalide.", "error.settings.email_from_invalid"),
            Map.entry("Envoi email desactive.", "error.email.disabled"),
            Map.entry("Expediteur SMTP non configure.", "error.email.from_missing"),
            Map.entry("Envoi email echoue.", "error.email.failed"),
            Map.entry("Texte source requis pour cette operation.", "error.ai.text_required"),
            Map.entry("IA desactivee (AI_ENABLED=false).", "error.ai.disabled"),
            Map.entry("Archive ZIP indisponible.", "error.batch.zip_missing"),
            Map.entry("Rapport d'erreurs indisponible.", "error.batch.errors_missing"),
            Map.entry("Lecture du fichier impossible.", "error.file.read_failed"),
            Map.entry("Échec de stockage du DOCX.", "error.template.store_failed"),
            Map.entry("Fichier DOCX introuvable.", "error.document.docx_missing"),
            Map.entry("Fichier PDF introuvable.", "error.document.pdf_missing"),
            Map.entry("Lecture du template impossible.", "error.template.read_failed"),
            Map.entry("Impossible d'allouer une reference document unique.", "error.document.ref_alloc"),
            Map.entry("Donnees JSON invalides.", "error.json.invalid"),
            Map.entry("Fichier CSV vide.", "error.csv.empty"),
            Map.entry("CSV sans en-tetes.", "error.csv.no_headers"),
            Map.entry("CSV sans lignes de donnees.", "error.csv.no_rows"),
            Map.entry("CSV illisible.", "error.csv.unreadable"),
            Map.entry("Fichier CSV requis.", "error.csv.required"),
            Map.entry("Seuls les fichiers .csv sont acceptes.", "error.csv.extension"),
            Map.entry("Lecture du CSV impossible.", "error.csv.read_failed"),
            Map.entry("Mapping JSON invalide.", "error.csv.mapping_invalid"),
            Map.entry("Seuls les templates ACTIVE acceptent un batch.", "error.batch.template_not_active"),
            Map.entry("La version ne correspond pas au template.", "error.template.version_mismatch"),
            Map.entry("Le template n'a pas de version courante.", "error.template.no_current_version"),
            Map.entry("Un utilisateur avec cet email existe deja.", "error.user.email_exists"),
            Map.entry("Vous ne pouvez pas vous desactiver vous-meme.", "error.user.self_disable"),
            Map.entry("Au moins un role est requis.", "error.user.role_required"),
            Map.entry("Un template avec ce code existe déjà.", "error.template.code_exists"),
            Map.entry("Un template archivé ne peut pas être modifié.", "error.template.archived_readonly"),
            Map.entry(
                    "Seuls les templates DRAFT peuvent être supprimés. Utilisez l'archivage.",
                    "error.template.delete_draft_only"
            ),
            Map.entry(
                    "Ce template a des documents générés et ne peut pas être supprimé.",
                    "error.template.has_documents"
            ),
            Map.entry("Impossible d'ajouter une version à un template archivé.", "error.template.version_archived"),
            Map.entry("Fichier DOCX requis.", "error.template.docx_required"),
            Map.entry("Le fichier n'est pas un DOCX valide.", "error.template.docx_invalid"),
            Map.entry("Impossible d'activer un template sans version DOCX.", "error.template.activate_no_version"),
            Map.entry(
                    "Le fichier DOCX de la version courante est introuvable.",
                    "error.template.current_docx_missing"
            ),
            Map.entry("Seuls les fichiers .docx sont acceptés.", "error.template.docx_extension"),
            Map.entry(
                    "Toutes les variables detectees doivent etre fournies dans la mise a jour.",
                    "error.template.variables_incomplete"
            ),
            Map.entry("Le fichier DOCX du template est introuvable.", "error.template.docx_missing"),
            Map.entry("Seuls les templates ACTIVE peuvent generer un document.", "error.document.template_not_active"),
            Map.entry("Nom de fichier manquant.", "error.storage.filename_missing"),
            Map.entry("Nom de fichier trop long.", "error.storage.filename_too_long"),
            Map.entry("Nom de fichier non autorisé.", "error.storage.filename_forbidden"),
            Map.entry("Extension de fichier manquante.", "error.storage.extension_missing"),
            Map.entry("Clé de stockage invalide.", "error.storage.key_invalid"),
            Map.entry("Clé de stockage non autorisée.", "error.storage.key_forbidden"),
            Map.entry("Chemin hors zone de stockage.", "error.storage.path_outside"),
            Map.entry("CatÃ©gorie de stockage manquante.", "error.storage.category_missing"),
            Map.entry("Catégorie de stockage manquante.", "error.storage.category_missing"),
            Map.entry("Contenu fichier manquant.", "error.storage.content_missing"),
            Map.entry("Taille de fichier invalide.", "error.storage.size_invalid"),
            Map.entry("Fichier introuvable.", "error.storage.file_not_found"),
            Map.entry("Type MIME manquant.", "error.storage.mime_missing"),
            Map.entry("Champ non declare dans le schema.", "error.form.unknown_field"),
            Map.entry("Champ obligatoire.", "error.form.required"),
            Map.entry("Email invalide.", "error.form.email"),
            Map.entry("Telephone invalide.", "error.form.phone"),
            Map.entry("Booleen invalide.", "error.form.boolean"),
            Map.entry("Valeur non autorisee.", "error.form.not_allowed"),
            Map.entry("Type de champ non supporté.", "error.form.unsupported_type"),
            Map.entry("Format invalide.", "error.form.format"),
            Map.entry("Pattern de validation invalide.", "error.form.pattern_invalid"),
            Map.entry("Nombre invalide.", "error.form.number"),
            Map.entry("Nombre entier requis.", "error.form.integer"),
            Map.entry("Montant invalide.", "error.form.amount"),
            Map.entry("Date ISO invalide (yyyy-MM-dd).", "error.form.date"),
            Map.entry("Date-heure ISO invalide.", "error.form.datetime"),
            Map.entry("Selection multiple invalide.", "error.form.multiselect")
    );

    public ErrorMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String localize(String reasonOrKey) {
        return localize(reasonOrKey, LocaleContextHolder.getLocale());
    }

    public String localize(String reasonOrKey, Locale locale) {
        Locale effective = normalize(locale);
        if (reasonOrKey == null || reasonOrKey.isBlank()) {
            return msg("error.internal", effective);
        }
        if (reasonOrKey.startsWith("error.")) {
            int pipe = reasonOrKey.indexOf('|');
            if (pipe > 0) {
                String key = reasonOrKey.substring(0, pipe);
                Object[] args = reasonOrKey.substring(pipe + 1).split("\\|", -1);
                return msg(key, effective, args);
            }
            return msg(reasonOrKey, effective);
        }
        String key = LEGACY.get(reasonOrKey);
        if (key != null) {
            return msg(key, effective);
        }
        // Parameterized legacy French field messages (batch reports / older clients).
        String prefixed = localizeLegacyPrefixed(reasonOrKey, effective);
        if (prefixed != null) {
            return prefixed;
        }
        return reasonOrKey;
    }

    private String localizeLegacyPrefixed(String reason, Locale locale) {
        if (reason.startsWith("Longueur minimale: ")) {
            return msg("error.form.min_length", locale, reason.substring("Longueur minimale: ".length()));
        }
        if (reason.startsWith("Longueur maximale: ")) {
            return msg("error.form.max_length", locale, reason.substring("Longueur maximale: ".length()));
        }
        if (reason.startsWith("Valeur minimale: ")) {
            return msg("error.form.min", locale, reason.substring("Valeur minimale: ".length()));
        }
        if (reason.startsWith("Valeur maximale: ")) {
            return msg("error.form.max", locale, reason.substring("Valeur maximale: ".length()));
        }
        if (reason.startsWith("Montant doit etre >= ")) {
            return msg("error.form.amount_min", locale, reason.substring("Montant doit etre >= ".length()));
        }
        if (reason.startsWith("Valeur non autorisee: ")) {
            return msg("error.form.not_allowed_value", locale, reason.substring("Valeur non autorisee: ".length()));
        }
        return null;
    }

    public String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, key, normalize(locale));
    }

    public String msg(String key, Object... args) {
        return msg(key, LocaleContextHolder.getLocale(), args);
    }

    private static Locale normalize(Locale locale) {
        if (locale == null) {
            return Locale.FRENCH;
        }
        String lang = locale.getLanguage();
        if ("pt".equalsIgnoreCase(lang)) {
            return Locale.forLanguageTag("pt");
        }
        return Locale.FRENCH;
    }
}
