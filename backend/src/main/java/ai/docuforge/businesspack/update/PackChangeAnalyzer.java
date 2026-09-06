package ai.docuforge.businesspack.update;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Compares installed vs candidate pack content for update preview (PRD §§91–93).
 */
@Component
public class PackChangeAnalyzer {

    public PackChangeAnalysis analyze(PackVersionSnapshot installed, PackVersionSnapshot candidate) {
        List<PackChangeItem> changes = new ArrayList<>();
        List<PackValidationIssue> breaking = new ArrayList<>();

        Map<String, PackVersionSnapshot.TemplateSnapshot> oldTemplates = indexTemplates(installed);
        Map<String, PackVersionSnapshot.TemplateSnapshot> newTemplates = indexTemplates(candidate);

        Set<String> allCodes = new LinkedHashSet<>();
        allCodes.addAll(oldTemplates.keySet());
        allCodes.addAll(newTemplates.keySet());

        int templatesAdded = 0;
        int templatesUpdated = 0;
        int templatesRemoved = 0;
        int variablesAdded = 0;
        int variablesRemoved = 0;
        int requiredVariablesAdded = 0;

        for (String code : allCodes) {
            PackVersionSnapshot.TemplateSnapshot oldTpl = oldTemplates.get(code);
            PackVersionSnapshot.TemplateSnapshot newTpl = newTemplates.get(code);
            if (oldTpl == null && newTpl != null) {
                templatesAdded++;
                changes.add(new PackChangeItem("TEMPLATE_ADDED", code, null, null, newTpl.name()));
                continue;
            }
            if (oldTpl != null && newTpl == null) {
                templatesRemoved++;
                changes.add(new PackChangeItem("TEMPLATE_REMOVED", code, null, oldTpl.name(), null));
                breaking.add(PackValidationIssue.warning(
                                "PACK_TEMPLATE_REMOVED",
                                "error.pack.update.template_removed")
                        .withTemplateCode(code));
                continue;
            }
            if (oldTpl != null && newTpl != null) {
                boolean templateTouched = !Objects.equals(nullToEmpty(oldTpl.version()), nullToEmpty(newTpl.version()))
                        || !Objects.equals(nullToEmpty(oldTpl.name()), nullToEmpty(newTpl.name()));
                VariableDiff varDiff = diffVariables(oldTpl.variables(), newTpl.variables(), code, changes, breaking);
                variablesAdded += varDiff.added();
                variablesRemoved += varDiff.removed();
                requiredVariablesAdded += varDiff.requiredAdded();
                if (templateTouched || varDiff.changed()) {
                    templatesUpdated++;
                    changes.add(new PackChangeItem("TEMPLATE_UPDATED", code, null, oldTpl.version(), newTpl.version()));
                }
            }
        }

        int promptsChanged = diffPrompts(installed.prompts(), candidate.prompts(), changes);

        return new PackChangeAnalysis(
                templatesAdded,
                templatesUpdated,
                templatesRemoved,
                variablesAdded,
                variablesRemoved,
                requiredVariablesAdded,
                promptsChanged,
                List.copyOf(changes),
                List.copyOf(breaking)
        );
    }

    public List<PackValidationIssue> withSemverBreakingGuard(
            PackChangeAnalysis analysis,
            String updateKind
    ) {
        List<PackValidationIssue> issues = new ArrayList<>(analysis.breakingChanges());
        if (!analysis.breakingChanges().isEmpty()
                && ("MINOR".equals(updateKind) || "PATCH".equals(updateKind))) {
            issues.add(new PackValidationIssue(
                    PackValidationSeverity.WARNING,
                    "PACK_SEMVER_BREAKING_CHANGE",
                    "error.pack.update.semver_breaking",
                    null,
                    null,
                    null
            ));
        }
        return List.copyOf(issues);
    }

    private static VariableDiff diffVariables(
            List<PackVersionSnapshot.VariableSnapshot> oldVars,
            List<PackVersionSnapshot.VariableSnapshot> newVars,
            String templateCode,
            List<PackChangeItem> changes,
            List<PackValidationIssue> breaking
    ) {
        Map<String, PackVersionSnapshot.VariableSnapshot> oldMap = indexVariables(oldVars);
        Map<String, PackVersionSnapshot.VariableSnapshot> newMap = indexVariables(newVars);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(oldMap.keySet());
        keys.addAll(newMap.keySet());

        int added = 0;
        int removed = 0;
        int requiredAdded = 0;
        boolean changed = false;

        for (String key : keys) {
            PackVersionSnapshot.VariableSnapshot oldVar = oldMap.get(key);
            PackVersionSnapshot.VariableSnapshot newVar = newMap.get(key);
            if (oldVar == null && newVar != null) {
                added++;
                changed = true;
                changes.add(new PackChangeItem("VARIABLE_ADDED", templateCode, key, null, newVar.type()));
                if (newVar.required()) {
                    requiredAdded++;
                    changes.add(new PackChangeItem(
                            "REQUIRED_VARIABLE_ADDED", templateCode, key, null, "required"));
                    breaking.add(PackValidationIssue.warning(
                                    "PACK_REQUIRED_VARIABLE_ADDED",
                                    "error.pack.update.required_variable_added")
                            .withTemplateCode(templateCode)
                            .withVariable(key));
                }
                continue;
            }
            if (oldVar != null && newVar == null) {
                removed++;
                changed = true;
                changes.add(new PackChangeItem("VARIABLE_REMOVED", templateCode, key, oldVar.type(), null));
                breaking.add(PackValidationIssue.warning(
                                "PACK_VARIABLE_REMOVED",
                                "error.pack.update.variable_removed")
                        .withTemplateCode(templateCode)
                        .withVariable(key));
                continue;
            }
            if (oldVar != null && newVar != null) {
                if (!normalizeType(oldVar.type()).equals(normalizeType(newVar.type()))) {
                    changed = true;
                    changes.add(new PackChangeItem(
                            "VARIABLE_TYPE_CHANGED", templateCode, key, oldVar.type(), newVar.type()));
                    breaking.add(PackValidationIssue.warning(
                                    "PACK_VARIABLE_TYPE_CHANGED",
                                    "error.pack.update.variable_type_changed")
                            .withTemplateCode(templateCode)
                            .withVariable(key));
                }
                if (!oldVar.required() && newVar.required()) {
                    changed = true;
                    requiredAdded++;
                    changes.add(new PackChangeItem(
                            "VARIABLE_OPTIONAL_TO_REQUIRED", templateCode, key, "optional", "required"));
                    breaking.add(PackValidationIssue.warning(
                                    "PACK_VARIABLE_OPTIONAL_TO_REQUIRED",
                                    "error.pack.update.optional_to_required")
                            .withTemplateCode(templateCode)
                            .withVariable(key));
                }
            }
        }
        return new VariableDiff(added, removed, requiredAdded, changed);
    }

    private static int diffPrompts(
            List<PackVersionSnapshot.PromptSnapshot> oldPrompts,
            List<PackVersionSnapshot.PromptSnapshot> newPrompts,
            List<PackChangeItem> changes
    ) {
        Map<String, PackVersionSnapshot.PromptSnapshot> oldMap = indexPrompts(oldPrompts);
        Map<String, PackVersionSnapshot.PromptSnapshot> newMap = indexPrompts(newPrompts);
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(oldMap.keySet());
        codes.addAll(newMap.keySet());
        int changed = 0;
        for (String code : codes) {
            PackVersionSnapshot.PromptSnapshot oldP = oldMap.get(code);
            PackVersionSnapshot.PromptSnapshot newP = newMap.get(code);
            if (oldP == null && newP != null) {
                changed++;
                changes.add(new PackChangeItem("PROMPT_ADDED", code, null, null, newP.version()));
            } else if (oldP != null && newP == null) {
                changed++;
                changes.add(new PackChangeItem("PROMPT_REMOVED", code, null, oldP.version(), null));
            } else if (oldP != null && newP != null) {
                if (!Objects.equals(normalizeChecksum(oldP.checksum()), normalizeChecksum(newP.checksum()))
                        || !Objects.equals(nullToEmpty(oldP.version()), nullToEmpty(newP.version()))) {
                    changed++;
                    changes.add(new PackChangeItem(
                            "PROMPT_CHANGED", code, null, oldP.version(), newP.version()));
                }
            }
        }
        return changed;
    }

    private static Map<String, PackVersionSnapshot.TemplateSnapshot> indexTemplates(PackVersionSnapshot snap) {
        Map<String, PackVersionSnapshot.TemplateSnapshot> map = new LinkedHashMap<>();
        if (snap == null || snap.templates() == null) {
            return map;
        }
        for (PackVersionSnapshot.TemplateSnapshot t : snap.templates()) {
            if (t != null && StringUtils.hasText(t.code())) {
                map.put(t.code().trim(), t);
            }
        }
        return map;
    }

    private static Map<String, PackVersionSnapshot.VariableSnapshot> indexVariables(
            List<PackVersionSnapshot.VariableSnapshot> vars
    ) {
        Map<String, PackVersionSnapshot.VariableSnapshot> map = new LinkedHashMap<>();
        if (vars == null) {
            return map;
        }
        for (PackVersionSnapshot.VariableSnapshot v : vars) {
            if (v != null && StringUtils.hasText(v.key())) {
                map.put(v.key().trim(), v);
            }
        }
        return map;
    }

    private static Map<String, PackVersionSnapshot.PromptSnapshot> indexPrompts(
            List<PackVersionSnapshot.PromptSnapshot> prompts
    ) {
        Map<String, PackVersionSnapshot.PromptSnapshot> map = new LinkedHashMap<>();
        if (prompts == null) {
            return map;
        }
        for (PackVersionSnapshot.PromptSnapshot p : prompts) {
            if (p != null && StringUtils.hasText(p.code())) {
                map.put(p.code().trim(), p);
            }
        }
        return map;
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String value = type.trim().toUpperCase(Locale.ROOT);
        if ("INTEGER".equals(value)) {
            return "NUMBER";
        }
        return value;
    }

    private static String normalizeChecksum(String checksum) {
        if (!StringUtils.hasText(checksum)) {
            return "";
        }
        String value = checksum.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("sha256:")) {
            return value.substring("sha256:".length());
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record VariableDiff(int added, int removed, int requiredAdded, boolean changed) {
    }
}
