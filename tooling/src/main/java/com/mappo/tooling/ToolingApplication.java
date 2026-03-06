package com.mappo.tooling;

import java.util.Arrays;
import java.util.List;

public final class ToolingApplication {

    private ToolingApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            throw new ToolingException("tooling command failed with exit code " + exitCode, exitCode);
        }
    }

    static int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 2;
        }

        String command = args[0];
        List<String> commandArgs = Arrays.asList(args).subList(1, args.length);

        int exitCode;
        try {
            exitCode = switch (command) {
                case "backend-file-size-check" -> new BackendFileSizeCheckCommand().run(commandArgs);
                case "check-no-demo-leak" -> new CheckNoDemoLeakCommand().run(commandArgs);
                case "docs-consistency-check" -> new DocsConsistencyCheckCommand().run(commandArgs);
                case "golden-principles-check" -> new GoldenPrinciplesCheckCommand().run(commandArgs);
                case "workflow-discipline-check" -> new WorkflowDisciplineCheckCommand().run(commandArgs);
                case "export-backend-openapi" -> new ExportBackendOpenApiCommand().run(commandArgs);
                case "marketplace-ingest-events" -> new MarketplaceIngestEventsCommand().run(commandArgs);
                case "marketplace-forwarder-replay" -> new MarketplaceForwarderReplayInventoryCommand().run(commandArgs);
                case "release-ingest-from-repo" -> new ReleaseIngestFromRepoCommand().run(commandArgs);
                default -> throw new ToolingException("unknown command: " + command, 2);
            };
        } catch (ToolingException exception) {
            System.err.println(exception.getMessage());
            exitCode = exception.exitCode();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            exitCode = 1;
        }
        return exitCode;
    }

    private static void printUsage() {
        System.err.println("""
            usage: ToolingApplication <command> [options]

            commands:
              backend-file-size-check
              check-no-demo-leak
              docs-consistency-check
              golden-principles-check
              workflow-discipline-check
              export-backend-openapi
              marketplace-ingest-events
              marketplace-forwarder-replay
              release-ingest-from-repo
            """);
    }
}
