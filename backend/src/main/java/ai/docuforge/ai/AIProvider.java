package ai.docuforge.ai;

/**
 * AI gateway boundary (PRD §11, §38).
 */
public interface AIProvider {

    AIResponse generate(AIRequest request);
}
