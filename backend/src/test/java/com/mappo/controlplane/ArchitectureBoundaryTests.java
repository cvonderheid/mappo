package com.mappo.controlplane;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTests {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java/com/mappo/controlplane");
    private static final List<String> CORE_PACKAGE_DIRECTORIES = List.of(
        "api",
        "application",
        "config",
        "domain",
        "infrastructure",
        "model",
        "persistence",
        "service"
    );

    @Test
    void corePackagesDoNotImportProviderImplementations() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String packageDirectory : CORE_PACKAGE_DIRECTORIES) {
            Path directory = MAIN_SOURCE_ROOT.resolve(packageDirectory);
            if (!Files.exists(directory)) {
                continue;
            }
            try (var paths = Files.walk(directory)) {
                paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectForbiddenIntegrationImports(path, violations));
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Core packages must depend on application/domain ports, not provider implementations:\n"
                + String.join("\n", violations)
        );
    }

    @Test
    void handWrittenSourcesDoNotUseGeneratedJooqPackage() throws IOException {
        Path generatedPackageDirectory = MAIN_SOURCE_ROOT.resolve("jooq");
        if (!Files.exists(generatedPackageDirectory)) {
            return;
        }

        List<String> handWrittenFiles;
        try (var paths = Files.walk(generatedPackageDirectory)) {
            handWrittenFiles = paths
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toString)
                .sorted()
                .toList();
        }

        assertTrue(
            handWrittenFiles.isEmpty(),
            "Generated jOOQ package must not contain hand-written sources:\n"
                + String.join("\n", handWrittenFiles)
        );
    }

    private void collectForbiddenIntegrationImports(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index += 1) {
                String line = lines.get(index);
                if (line.startsWith("import com.mappo.controlplane.integrations.")) {
                    violations.add(path + ":" + (index + 1) + " " + line.trim());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed reading " + path, exception);
        }
    }
}
