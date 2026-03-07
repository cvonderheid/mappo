package com.mappo.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AzureScriptSupportCommandTests {

    @TempDir
    Path tempDir;

    @Test
    void exportDbEnvWritesExpectedShellExports() throws Exception {
        Path envFile = tempDir.resolve("mappo-db.env");

        String stdout = runCommand(
            "export-db-env",
            "--outputs-json",
            """
            {
              "controlPlanePostgresEnabled": true,
              "controlPlanePostgresHost": "pg.example.com",
              "controlPlanePostgresPort": 5432,
              "controlPlanePostgresDatabase": "mappo",
              "controlPlanePostgresConnectionUsername": "mappo_user",
              "controlPlanePostgresPassword": "secret-value",
              "controlPlaneDatabaseUrl": "jdbc:postgresql://pg.example.com:5432/mappo"
            }
            """,
            "--env-file",
            envFile.toString()
        );

        String content = Files.readString(envFile);
        assertTrue(stdout.contains("iac-export-db-env: wrote"));
        assertTrue(content.contains("export MAPPO_DATABASE_URL='jdbc:postgresql://pg.example.com:5432/mappo'"));
        assertTrue(content.contains("export MAPPO_DB_HOST='pg.example.com'"));
        assertTrue(content.contains("export MAPPO_DB_PASSWORD='secret-value'"));
        assertTrue(content.contains("export MAPPO_DB_SSLMODE='require'"));
    }

    @Test
    void inventoryResourceGroupScopesUsesMetadataThenManagedAppIds() {
        Path inventoryFile = tempDir.resolve("inventory.json");
        FileSupport.writeText(
            inventoryFile,
            """
            [
              {
                "subscription_id": "sub-a",
                "managed_app_id": "/subscriptions/sub-a/resourceGroups/rg-from-id/providers/Microsoft.App/containerApps/app-a",
                "metadata": {
                  "managed_resource_group_id": "/subscriptions/sub-a/resourceGroups/rg-managed"
                }
              },
              {
                "subscription_id": "sub-a",
                "managed_app_id": "/subscriptions/sub-a/resourceGroups/rg-fallback/providers/Microsoft.App/containerApps/app-b",
                "metadata": {}
              },
              {
                "subscription_id": "sub-b",
                "managed_app_id": "/subscriptions/sub-b/resourceGroups/rg-other/providers/Microsoft.App/containerApps/app-c"
              }
            ]
            """
        );

        String stdout = runCommand(
            "inventory-rg-scopes",
            "--inventory-file",
            inventoryFile.toString(),
            "--subscription-id",
            "sub-a"
        );

        assertEquals(
            List.of(
                "/subscriptions/sub-a/resourceGroups/rg-managed",
                "/subscriptions/sub-a/resourceGroups/rg-fallback"
            ),
            stdout.lines().filter(line -> !line.isBlank()).toList()
        );
    }

    @Test
    void easyAuthRedirectUrisDeduplicatesAndPreservesOrder() {
        String stdout = runCommand(
            "easyauth-redirect-uris",
            "--existing-json",
            """
            [
              "https://app.example.com/.auth/login/aad/callback",
              "https://app.example.com/custom"
            ]
            """,
            "--callback-url",
            "https://app.example.com/.auth/login/aad/callback",
            "--extra-redirect-uris",
            "https://app.example.com/custom,https://app.example.com/extra"
        );

        assertEquals(
            List.of(
                "https://app.example.com/.auth/login/aad/callback",
                "https://app.example.com/custom",
                "https://app.example.com/extra"
            ),
            stdout.lines().filter(line -> !line.isBlank()).toList()
        );
    }

    @Test
    void jobExecutionNameFallsBackToIdTail() {
        String stdout = runCommand(
            "job-execution-name",
            "--json",
            """
            {
              "id": "/subscriptions/sub-a/resourceGroups/rg-a/providers/Microsoft.App/jobs/job-a/executions/execution-123"
            }
            """
        );

        assertEquals("execution-123", stdout.trim());
    }

    private String runCommand(String... args) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            int exitCode = new AzureScriptSupportCommand().run(List.of(args));
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
