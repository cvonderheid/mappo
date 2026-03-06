package com.mappo.tooling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ReleaseIngestFromRepoCommand {

    private static final TypeReference<List<LinkedHashMap<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpSupport httpSupport = new HttpSupport(objectMapper);

    int run(List<String> rawArgs) {
        for (String arg : rawArgs) {
            if (Arguments.isHelpFlag(arg)) {
                printUsage();
                return 0;
            }
        }
        Arguments args = new Arguments(rawArgs);
        Options options = parseOptions(args);

        String manifestRaw = loadManifestPayload(options);
        List<Map<String, Object>> releaseRequests = parseManifest(manifestRaw);
        Set<String> existingKeys = options.dryRun() ? Set.of() : fetchExistingReleaseKeys(options);

        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (Map<String, Object> payload : releaseRequests) {
            String key = releaseKey(payload);
            if (!options.allowDuplicates() && existingKeys.contains(key)) {
                skipped += 1;
                System.out.printf(
                    "release-ingest-from-repo: skipped existing %s (%s)%n",
                    payload.get("sourceVersion"),
                    payload.get("sourceRef")
                );
                continue;
            }

            if (options.dryRun()) {
                created += 1;
                System.out.printf(
                    "release-ingest-from-repo: dry-run create %s (%s)%n",
                    payload.get("sourceVersion"),
                    payload.get("sourceRef")
                );
                continue;
            }

            HttpSupport.HttpResult result = httpSupport.postJson(
                options.apiBaseUrl() + "/api/v1/releases",
                payload,
                apiHeaders(options.apiBearerToken()),
                30
            );
            if (!result.isSuccess()) {
                failed += 1;
                System.err.printf(
                    "release-ingest-from-repo: API POST /api/v1/releases failed (HTTP %d): %s%n",
                    result.statusCode(),
                    result.body()
                );
                continue;
            }

            Map<String, Object> responsePayload = readMap(result.body());
            created += 1;
            existingKeys = new LinkedHashSet<>(existingKeys);
            existingKeys.add(key);
            System.out.printf(
                "release-ingest-from-repo: created %s :: %s%n",
                firstNonBlank(stringValue(responsePayload.get("id")), "(unknown-id)"),
                payload.get("sourceVersion")
            );
        }

        System.out.printf(
            "release-ingest-from-repo: manifest_releases=%d created=%d skipped=%d failed=%d dry_run=%s%n",
            releaseRequests.size(),
            created,
            skipped,
            failed,
            options.dryRun() ? "true" : "false"
        );
        return failed > 0 ? 1 : 0;
    }

    private Options parseOptions(Arguments args) {
        String apiBaseUrl = System.getenv().getOrDefault("MAPPO_API_BASE_URL", "http://localhost:8010").replaceAll("/+$", "");
        String apiBearerToken = System.getenv().getOrDefault("MAPPO_API_BEARER_TOKEN", "");
        String manifestFile = "";
        String manifestUrl = "";
        String githubRepo = "";
        String githubPath = "";
        String githubRef = "main";
        String githubToken = System.getenv().getOrDefault("GITHUB_TOKEN", "");
        String adoOrg = "";
        String adoProject = "";
        String adoRepository = "";
        String adoPath = "";
        String adoRef = "main";
        String adoToken = System.getenv().getOrDefault("AZURE_DEVOPS_EXT_PAT", "");
        boolean allowDuplicates = false;
        boolean dryRun = false;

        while (args.hasNext()) {
            String arg = args.next();
            switch (arg) {
                case "--api-base-url" -> apiBaseUrl = args.nextRequired("--api-base-url").replaceAll("/+$", "");
                case "--api-bearer-token" -> apiBearerToken = args.nextRequired("--api-bearer-token");
                case "--manifest-file" -> manifestFile = args.nextRequired("--manifest-file");
                case "--manifest-url" -> manifestUrl = args.nextRequired("--manifest-url");
                case "--github-repo" -> githubRepo = args.nextRequired("--github-repo");
                case "--github-path" -> githubPath = args.nextRequired("--github-path");
                case "--github-ref" -> githubRef = args.nextRequired("--github-ref");
                case "--github-token" -> githubToken = args.nextRequired("--github-token");
                case "--ado-org" -> adoOrg = args.nextRequired("--ado-org");
                case "--ado-project" -> adoProject = args.nextRequired("--ado-project");
                case "--ado-repository" -> adoRepository = args.nextRequired("--ado-repository");
                case "--ado-path" -> adoPath = args.nextRequired("--ado-path");
                case "--ado-ref" -> adoRef = args.nextRequired("--ado-ref");
                case "--ado-token" -> adoToken = args.nextRequired("--ado-token");
                case "--allow-duplicates" -> allowDuplicates = true;
                case "--dry-run" -> dryRun = true;
                default -> throw new ToolingException("release-ingest-from-repo: unknown argument: " + arg, 2);
            }
        }

        return new Options(
            apiBaseUrl,
            apiBearerToken,
            manifestFile,
            manifestUrl,
            githubRepo,
            githubPath,
            githubRef,
            githubToken,
            adoOrg,
            adoProject,
            adoRepository,
            adoPath,
            adoRef,
            adoToken,
            allowDuplicates,
            dryRun
        );
    }

    private String loadManifestPayload(Options options) {
        int sourceCount = 0;
        if (!options.manifestFile().isBlank()) {
            sourceCount += 1;
        }
        if (!options.manifestUrl().isBlank()) {
            sourceCount += 1;
        }
        if (!options.githubRepo().isBlank() || !options.githubPath().isBlank()) {
            if (options.githubRepo().isBlank() || options.githubPath().isBlank()) {
                throw new ToolingException("release-ingest-from-repo: github source requires both --github-repo and --github-path", 2);
            }
            sourceCount += 1;
        }
        if (!options.adoOrg().isBlank() || !options.adoProject().isBlank() || !options.adoRepository().isBlank() || !options.adoPath().isBlank()) {
            List<String> missing = new ArrayList<>();
            if (options.adoOrg().isBlank()) {
                missing.add("--ado-org");
            }
            if (options.adoProject().isBlank()) {
                missing.add("--ado-project");
            }
            if (options.adoRepository().isBlank()) {
                missing.add("--ado-repository");
            }
            if (options.adoPath().isBlank()) {
                missing.add("--ado-path");
            }
            if (!missing.isEmpty()) {
                throw new ToolingException("release-ingest-from-repo: ado source missing required options: " + String.join(", ", missing), 2);
            }
            sourceCount += 1;
        }
        if (sourceCount != 1) {
            throw new ToolingException(
                "release-ingest-from-repo: exactly one source is required: --manifest-file, --manifest-url, GitHub source, or Azure DevOps source",
                2
            );
        }

        if (!options.manifestFile().isBlank()) {
            Path path = resolvePath(options.manifestFile());
            if (!java.nio.file.Files.exists(path)) {
                throw new ToolingException("release-ingest-from-repo: manifest file not found: " + path, 2);
            }
            return FileSupport.readText(path);
        }
        if (!options.githubRepo().isBlank()) {
            String safePath = options.githubPath().replaceFirst("^/+", "");
            String url = "https://raw.githubusercontent.com/%s/%s/%s".formatted(options.githubRepo(), options.githubRef(), safePath);
            Map<String, String> headers = new LinkedHashMap<>();
            if (!options.githubToken().isBlank()) {
                headers.put("Authorization", "Bearer " + options.githubToken());
            }
            HttpSupport.HttpResult result = httpSupport.get(url, headers, 30);
            if (!result.isSuccess()) {
                throw new ToolingException(
                    "release-ingest-from-repo: GitHub manifest fetch failed (HTTP %d): %s".formatted(result.statusCode(), result.body()),
                    1
                );
            }
            return result.body();
        }
        if (!options.adoOrg().isBlank()) {
            String query = "path=%s&versionDescriptor.versionType=branch&versionDescriptor.version=%s&download=true&api-version=7.1"
                .formatted(encodePath(options.adoPath()), encode(options.adoRef()));
            String url = "https://dev.azure.com/%s/%s/_apis/git/repositories/%s/items?%s"
                .formatted(options.adoOrg(), options.adoProject(), options.adoRepository(), query);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            if (!options.adoToken().isBlank()) {
                headers.put("Authorization", HttpSupport.basicAuthToken("", options.adoToken()));
            }
            HttpSupport.HttpResult result = httpSupport.get(url, headers, 30);
            if (!result.isSuccess()) {
                throw new ToolingException(
                    "release-ingest-from-repo: ADO manifest fetch failed (HTTP %d): %s".formatted(result.statusCode(), result.body()),
                    1
                );
            }
            return result.body();
        }

        HttpSupport.HttpResult result = httpSupport.get(options.manifestUrl(), Map.of(), 30);
        if (!result.isSuccess()) {
            throw new ToolingException(
                "release-ingest-from-repo: manifest URL fetch failed (HTTP %d): %s".formatted(result.statusCode(), result.body()),
                1
            );
        }
        return result.body();
    }

    private List<Map<String, Object>> parseManifest(String rawPayload) {
        Object parsed;
        try {
            parsed = objectMapper.readValue(rawPayload, Object.class);
        } catch (Exception exception) {
            throw new ToolingException("release-ingest-from-repo: manifest is not valid JSON: " + exception.getMessage(), 2);
        }

        Object releasesPayload;
        if (parsed instanceof List<?> list) {
            releasesPayload = list;
        } else if (parsed instanceof Map<?, ?> map) {
            releasesPayload = map.get("releases");
            if (releasesPayload == null) {
                throw new ToolingException("release-ingest-from-repo: manifest object must include a 'releases' array", 2);
            }
        } else {
            throw new ToolingException("release-ingest-from-repo: manifest must be a JSON array or an object with a 'releases' array", 2);
        }

        if (!(releasesPayload instanceof List<?> releases)) {
            throw new ToolingException("release-ingest-from-repo: 'releases' must be an array", 2);
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int index = 0; index < releases.size(); index++) {
            Object item = releases.get(index);
            if (!(item instanceof Map<?, ?> source)) {
                throw new ToolingException("release-ingest-from-repo: release row #%d is not an object".formatted(index + 1), 2);
            }
            String sourceRef = firstNonBlank(stringValue(source.get("source_ref")), stringValue(source.get("sourceRef")), stringValue(source.get("template_spec_id")));
            String sourceVersion = firstNonBlank(stringValue(source.get("source_version")), stringValue(source.get("sourceVersion")), stringValue(source.get("template_spec_version")));
            if (sourceRef.isBlank() || sourceVersion.isBlank()) {
                throw new ToolingException("release-ingest-from-repo: release row #%d missing required fields: source_ref and source_version".formatted(index + 1), 2);
            }

            Object executionSettings = firstNonNull(source.get("execution_settings"), source.get("executionSettings"), source.get("deployment_mode_settings"));
            Object parameterDefaults = firstNonNull(source.get("parameter_defaults"), source.get("parameterDefaults"));
            Object verificationHints = firstNonNull(source.get("verification_hints"), source.get("verificationHints"));
            if (executionSettings == null) {
                executionSettings = Map.of();
            }
            if (parameterDefaults == null) {
                parameterDefaults = Map.of();
            }
            if (verificationHints == null) {
                verificationHints = List.of();
            }
            if (!(executionSettings instanceof Map<?, ?>)) {
                throw new ToolingException("release-ingest-from-repo: release row #%d has non-object execution_settings".formatted(index + 1), 2);
            }
            if (!(parameterDefaults instanceof Map<?, ?>)) {
                throw new ToolingException("release-ingest-from-repo: release row #%d has non-object parameter_defaults".formatted(index + 1), 2);
            }
            if (!(verificationHints instanceof List<?>)) {
                throw new ToolingException("release-ingest-from-repo: release row #%d has non-array verification_hints".formatted(index + 1), 2);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceRef", sourceRef);
            payload.put("sourceVersion", sourceVersion);
            payload.put(
                "sourceType",
                firstNonBlank(
                    stringValue(source.get("source_type")),
                    stringValue(source.get("sourceType")),
                    stringValue(source.get("deployment_mode")),
                    "template_spec"
                )
            );
            String sourceVersionRef = firstNonBlank(
                stringValue(source.get("source_version_ref")),
                stringValue(source.get("sourceVersionRef")),
                stringValue(source.get("template_spec_version_id"))
            );
            if (!sourceVersionRef.isBlank()) {
                payload.put("sourceVersionRef", sourceVersionRef);
            }
            payload.put(
                "deploymentScope",
                firstNonBlank(
                    stringValue(source.get("deployment_scope")),
                    stringValue(source.get("deploymentScope")),
                    "resource_group"
                )
            );
            payload.put("executionSettings", sanitizeMap((Map<?, ?>) executionSettings));
            payload.put("parameterDefaults", sanitizeStringMap((Map<?, ?>) parameterDefaults));
            payload.put("releaseNotes", firstNonBlank(stringValue(source.get("release_notes")), stringValue(source.get("releaseNotes"))));
            payload.put("verificationHints", sanitizeStringList((List<?>) verificationHints));
            normalized.add(payload);
        }
        return normalized;
    }

    private Set<String> fetchExistingReleaseKeys(Options options) {
        HttpSupport.HttpResult result = httpSupport.get(options.apiBaseUrl() + "/api/v1/releases", apiHeaders(options.apiBearerToken()), 30);
        if (!result.isSuccess()) {
            throw new ToolingException(
                "release-ingest-from-repo: API GET /api/v1/releases failed (HTTP %d): %s".formatted(result.statusCode(), result.body()),
                1
            );
        }
        List<LinkedHashMap<String, Object>> rows;
        try {
            rows = objectMapper.readValue(result.body(), LIST_OF_MAPS);
        } catch (Exception exception) {
            throw new ToolingException("release-ingest-from-repo: GET /api/v1/releases returned unexpected payload", 1);
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String sourceRef = firstNonBlank(stringValue(row.get("sourceRef")), stringValue(row.get("template_spec_id")));
            String sourceVersion = firstNonBlank(stringValue(row.get("sourceVersion")), stringValue(row.get("template_spec_version")));
            if (!sourceRef.isBlank() && !sourceVersion.isBlank()) {
                keys.add(sourceRef + "||" + sourceVersion);
            }
        }
        return keys;
    }

    private Map<String, String> apiHeaders(String apiBearerToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (!apiBearerToken.isBlank()) {
            headers.put("Authorization", "Bearer " + apiBearerToken);
        }
        return headers;
    }

    private Map<String, Object> readMap(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, MAP_TYPE);
        } catch (Exception exception) {
            throw new ToolingException("release-ingest-from-repo: API returned invalid JSON: " + exception.getMessage(), 1);
        }
    }

    private String releaseKey(Map<String, Object> payload) {
        return payload.get("sourceRef") + "||" + payload.get("sourceVersion");
    }

    private Path resolvePath(String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path : FileSupport.repoRoot().resolve(path).normalize();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("%2F", "/");
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = stringValue(key);
            if (!normalizedKey.isBlank()) {
                out.put(normalizedKey, value);
            }
        });
        return out;
    }

    private Map<String, String> sanitizeStringMap(Map<?, ?> source) {
        Map<String, String> out = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = stringValue(key);
            if (!normalizedKey.isBlank()) {
                out.put(normalizedKey, stringValue(value));
            }
        });
        return out;
    }

    private List<String> sanitizeStringList(List<?> source) {
        List<String> out = new ArrayList<>();
        for (Object value : source) {
            String normalized = stringValue(value);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void printUsage() {
        System.out.println("""
            usage: release-ingest-from-repo [options]
              --manifest-file <path>
              --manifest-url <url>
              --github-repo <owner/repo> --github-path <path> [--github-ref <ref>]
              --ado-org <org> --ado-project <project> --ado-repository <repo> --ado-path <path> [--ado-ref <branch>]
              --api-base-url <url>
              --api-bearer-token <token>
              --github-token <token>
              --ado-token <token>
              --allow-duplicates
              --dry-run
            """);
    }

    private record Options(
        String apiBaseUrl,
        String apiBearerToken,
        String manifestFile,
        String manifestUrl,
        String githubRepo,
        String githubPath,
        String githubRef,
        String githubToken,
        String adoOrg,
        String adoProject,
        String adoRepository,
        String adoPath,
        String adoRef,
        String adoToken,
        boolean allowDuplicates,
        boolean dryRun
    ) {
    }
}
