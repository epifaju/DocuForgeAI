package ai.docuforge.businesspack.update;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackChangeAnalyzerTest {

    private PackChangeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PackChangeAnalyzer();
    }

    @Test
    void detectsAddedUpdatedRemovedTemplatesAndVariables() {
        PackVersionSnapshot installed = new PackVersionSnapshot(
                "com.docuforge.pack.demo",
                "1.0.0",
                List.of(
                        new PackVersionSnapshot.TemplateSnapshot(
                                "QUOTE",
                                "Quote",
                                "1.0.0",
                                List.of(
                                        new PackVersionSnapshot.VariableSnapshot("client.name", "TEXT", true),
                                        new PackVersionSnapshot.VariableSnapshot("notes", "TEXT", false)
                                )
                        ),
                        new PackVersionSnapshot.TemplateSnapshot(
                                "INVOICE",
                                "Invoice",
                                "1.0.0",
                                List.of(new PackVersionSnapshot.VariableSnapshot("amount", "NUMBER", true))
                        )
                ),
                List.of(new PackVersionSnapshot.PromptSnapshot("ASSIST", "1.0.0", "aaa"))
        );
        PackVersionSnapshot candidate = new PackVersionSnapshot(
                "com.docuforge.pack.demo",
                "1.1.0",
                List.of(
                        new PackVersionSnapshot.TemplateSnapshot(
                                "QUOTE",
                                "Quote",
                                "1.1.0",
                                List.of(
                                        new PackVersionSnapshot.VariableSnapshot("client.name", "TEXT", true),
                                        new PackVersionSnapshot.VariableSnapshot("client.email", "EMAIL", true)
                                )
                        ),
                        new PackVersionSnapshot.TemplateSnapshot(
                                "ORDER",
                                "Order",
                                "1.0.0",
                                List.of()
                        )
                ),
                List.of(new PackVersionSnapshot.PromptSnapshot("ASSIST", "1.0.1", "bbb"))
        );

        PackChangeAnalysis analysis = analyzer.analyze(installed, candidate);

        assertThat(analysis.templatesAdded()).isEqualTo(1);
        assertThat(analysis.templatesRemoved()).isEqualTo(1);
        assertThat(analysis.templatesUpdated()).isEqualTo(1);
        assertThat(analysis.variablesAdded()).isEqualTo(1);
        assertThat(analysis.variablesRemoved()).isEqualTo(1);
        assertThat(analysis.requiredVariablesAdded()).isEqualTo(1);
        assertThat(analysis.promptsChanged()).isEqualTo(1);
        assertThat(analysis.breakingChanges())
                .extracting(PackValidationIssue::code)
                .contains(
                        "PACK_TEMPLATE_REMOVED",
                        "PACK_VARIABLE_REMOVED",
                        "PACK_REQUIRED_VARIABLE_ADDED"
                );
    }

    @Test
    void detectsTypeChangeAndOptionalToRequired() {
        PackVersionSnapshot installed = snapshot(
                "QUOTE",
                List.of(new PackVersionSnapshot.VariableSnapshot("qty", "TEXT", false))
        );
        PackVersionSnapshot candidate = snapshot(
                "QUOTE",
                List.of(new PackVersionSnapshot.VariableSnapshot("qty", "NUMBER", true))
        );

        PackChangeAnalysis analysis = analyzer.analyze(installed, candidate);
        assertThat(analysis.breakingChanges())
                .extracting(PackValidationIssue::code)
                .contains("PACK_VARIABLE_TYPE_CHANGED", "PACK_VARIABLE_OPTIONAL_TO_REQUIRED");
    }

    @Test
    void semverBreakingGuardOnMinor() {
        PackVersionSnapshot installed = snapshot(
                "OLD",
                List.of(new PackVersionSnapshot.VariableSnapshot("a", "TEXT", true))
        );
        PackVersionSnapshot candidate = snapshot("NEW", List.of());
        PackChangeAnalysis analysis = analyzer.analyze(installed, candidate);
        List<PackValidationIssue> withGuard = analyzer.withSemverBreakingGuard(analysis, "MINOR");
        assertThat(withGuard).extracting(PackValidationIssue::code)
                .contains("PACK_SEMVER_BREAKING_CHANGE");
    }

    @Test
    void integerAndNumberTypesAreEquivalent() {
        PackVersionSnapshot installed = snapshot(
                "QUOTE",
                List.of(new PackVersionSnapshot.VariableSnapshot("qty", "INTEGER", true))
        );
        PackVersionSnapshot candidate = snapshot(
                "QUOTE",
                List.of(new PackVersionSnapshot.VariableSnapshot("qty", "NUMBER", true))
        );
        PackChangeAnalysis analysis = analyzer.analyze(installed, candidate);
        assertThat(analysis.breakingChanges()).isEmpty();
        assertThat(analysis.templatesUpdated()).isZero();
    }

    private static PackVersionSnapshot snapshot(
            String templateCode,
            List<PackVersionSnapshot.VariableSnapshot> variables
    ) {
        return new PackVersionSnapshot(
                "com.docuforge.pack.demo",
                "1.0.0",
                List.of(new PackVersionSnapshot.TemplateSnapshot(templateCode, templateCode, "1.0.0", variables)),
                List.of()
        );
    }
}
