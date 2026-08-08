package co.com.pragma;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

class QualityArchitectureTest {
    private static final Map<String, String> FORBIDDEN_SOURCE = Map.of(
            ".block(", "blocking Reactor call",
            ".subscribe(", "manual Reactor subscription",
            "javax.", "legacy javax dependency",
            "@RestController", "annotated REST controller",
            "@ControllerAdvice", "annotated controller advice",
            "@Autowired", "field or method injection");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("co.com.pragma");

    @Test
    void preservesInwardDependencies() {
        ArchRule domainDependencies = noClasses()
                .that().resideInAnyPackage("co.com.pragma.model..", "co.com.pragma.usecase..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "io.r2dbc..",
                        "software.amazon.awssdk..",
                        "co.com.pragma.api..",
                        "co.com.pragma.r2dbc..");
        ArchRule adapterDependencies = noClasses()
                .that().resideInAPackage("co.com.pragma.r2dbc..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "co.com.pragma.api..", "co.com.pragma.metrics..");

        domainDependencies.check(classes);
        adapterDependencies.check(classes);
        slices().matching("co.com.pragma.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void excludesImperativePersistenceAndAnnotatedHttp() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "org.springframework.data.jpa..",
                "org.hibernate..",
                "java.sql..")
                .check(classes);
    }

    @Test
    void excludesForbiddenProductionSourcePatterns() throws IOException {
        Path root = Path.of(System.getProperty("project.rootDir"));
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(this::isProductionJava).toList()) {
                String source = Files.readString(path);
                FORBIDDEN_SOURCE.forEach((pattern, description) -> {
                    if (source.contains(pattern)) {
                        violations.add(root.relativize(path) + ": " + description);
                    }
                });
                if (source.contains("reactor.core.publisher") && source.contains("java.util.stream")) {
                    violations.add(root.relativize(path) + ": native Stream API in reactive code");
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    private boolean isProductionJava(Path path) {
        String value = path.toString();
        return value.endsWith(".java") && value.contains("/src/main/java/");
    }
}
