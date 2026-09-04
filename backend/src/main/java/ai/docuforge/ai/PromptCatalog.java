package ai.docuforge.ai;

import ai.docuforge.ai.AiOperation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptCatalog {

    public static final String PROMPT_VERSION = "v1-fr";

    private final Map<AiOperation, String> templates = new EnumMap<>(AiOperation.class);

    public PromptCatalog() {
        templates.put(AiOperation.REWRITE, load("prompts/rewrite-professional-fr.txt"));
        templates.put(AiOperation.FORMALIZE, load("prompts/formalize-fr.txt"));
        templates.put(AiOperation.SUMMARIZE, load("prompts/summarize-fr.txt"));
        templates.put(AiOperation.GENERATE_PARAGRAPH, load("prompts/generate-paragraph-fr.txt"));
    }

    public String render(AiOperation operation, String text, String instruction, String context) {
        String template = templates.get(operation);
        if (template == null) {
            throw new IllegalArgumentException("Prompt inconnu: " + operation);
        }
        String rendered = template;
        rendered = applyOptionalBlock(rendered, "instruction", instruction);
        rendered = applyOptionalBlock(rendered, "context", context);
        rendered = applyOptionalBlock(rendered, "text", text);
        rendered = rendered.replace("{{instruction}}", nullToEmpty(instruction));
        rendered = rendered.replace("{{context}}", nullToEmpty(context));
        rendered = rendered.replace("{{text}}", nullToEmpty(text));
        return rendered.trim();
    }

    public String promptVersion() {
        return PROMPT_VERSION;
    }

    private static String applyOptionalBlock(String template, String name, String value) {
        String start = "{% if " + name + " %}";
        String end = "{% endif %}";
        int from;
        while ((from = template.indexOf(start)) >= 0) {
            int to = template.indexOf(end, from);
            if (to < 0) {
                break;
            }
            String block = template.substring(from + start.length(), to);
            String replacement = (value != null && !value.isBlank()) ? block : "";
            template = template.substring(0, from) + replacement + template.substring(to + end.length());
        }
        return template;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String load(String classpath) {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Prompt introuvable: " + classpath, ex);
        }
    }
}
