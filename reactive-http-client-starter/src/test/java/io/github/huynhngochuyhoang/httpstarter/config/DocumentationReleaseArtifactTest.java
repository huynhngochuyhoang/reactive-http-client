package io.github.huynhngochuyhoang.httpstarter.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationReleaseArtifactTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[[^]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern PROJECT_VERSION_SNIPPET = Pattern.compile(
            "<groupId>io\\.github\\.huynhngochuyhoang</groupId>\\s*"
                    + "<artifactId>reactive-http-client-[^<]+</artifactId>\\s*"
                    + "<version>([^<]+)</version>",
            Pattern.DOTALL);

    @Test
    void localMarkdownLinksResolve() throws IOException {
        Path root = projectRoot();
        List<String> brokenLinks = new ArrayList<>();

        try (Stream<Path> files = markdownFiles(root)) {
            for (Path markdown : files.toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(markdown));
                while (matcher.find()) {
                    String target = matcher.group(1);
                    if (isExternal(target)) {
                        continue;
                    }
                    String[] parts = target.split("#", 2);
                    String pathOnly = parts[0];
                    String anchor = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                    Path resolved = pathOnly.isBlank()
                            ? markdown
                            : markdown.getParent()
                                    .resolve(URLDecoder.decode(pathOnly, StandardCharsets.UTF_8))
                                    .normalize();
                    if (!Files.exists(resolved)) {
                        brokenLinks.add(root.relativize(markdown) + " -> " + target);
                        continue;
                    }
                    if (!anchor.isBlank() && !markdownAnchors(resolved).contains(anchor)) {
                        brokenLinks.add(root.relativize(markdown) + " -> " + target + " (missing anchor)");
                    }
                }
            }
        }

        assertThat(brokenLinks).as("broken local Markdown links").isEmpty();
    }

    @Test
    void readmeAndQuickStartVersionsMatchProjectVersion() throws Exception {
        String projectVersion = projectVersion(projectRoot().resolve("pom.xml"));

        assertVersionSnippets(projectRoot().resolve("README.md"), projectVersion);
        assertVersionSnippets(projectRoot().resolve("docs/01-quick-start.md"), projectVersion);
    }

    @Test
    void apiCompatibilityBaselineGuardIsDynamicAndProfileScoped() throws IOException {
        String pom = Files.readString(projectRoot().resolve("pom.xml"));
        int profileStart = pom.indexOf("<id>api-compatibility</id>");
        int profileEnd = pom.indexOf("</profile>", profileStart);

        assertThat(pom)
                .doesNotContain("api-compatibility-baseline-current-version-guard")
                .doesNotContain("ERROR-api.compatibility.baseline.version")
                .doesNotContain("<name>api.compatibility.baseline.version</name>");
        assertThat(profileStart).as("api-compatibility profile").isNotNegative();
        assertThat(profileEnd).as("api-compatibility profile end").isGreaterThan(profileStart);
        assertThat(pom.substring(profileStart, profileEnd))
                .contains("<id>reject-current-api-baseline</id>")
                .doesNotContain("<inherited>false</inherited>")
                .contains("<phase>validate</phase>")
                .contains("<equals arg1=\"${api.compatibility.baseline.version}\" arg2=\"${project.version}\"/>");
    }

    @Test
    void generatedConfigurationReferenceMatchesMetadata() throws IOException {
        Path reference = projectRoot().resolve("docs/configuration-properties.md");

        assertThat(Files.readString(reference))
                .isEqualTo(configurationReferenceMarkdown(metadata()));
    }

    private static Stream<Path> markdownFiles(Path root) throws IOException {
        return Stream.of(
                        Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")),
                        Files.walk(root.resolve("docs")),
                        Files.walk(root.resolve("roadmaps")))
                .flatMap(stream -> stream)
                .filter(path -> path.toString().endsWith(".md"));
    }

    private static Set<String> markdownAnchors(Path markdown) throws IOException {
        Set<String> anchors = new HashSet<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (String line : Files.readAllLines(markdown)) {
            if (!line.startsWith("#")) {
                continue;
            }
            String heading = line.replaceFirst("^#{1,6}\\s+", "").replaceFirst("\\s+#*$", "");
            if (heading.isBlank()) {
                continue;
            }
            String anchor = markdownAnchor(heading);
            int duplicate = occurrences.merge(anchor, 1, Integer::sum);
            anchors.add(duplicate == 1 ? anchor : anchor + "-" + (duplicate - 1));
        }
        return anchors;
    }

    private static String markdownAnchor(String heading) {
        String normalized = heading.toLowerCase(Locale.ROOT);
        StringBuilder anchor = new StringBuilder();
        boolean previousDash = false;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                anchor.append(current);
                previousDash = false;
            }
            else if (Character.isWhitespace(current) || current == '-') {
                if (!previousDash && anchor.length() > 0) {
                    anchor.append('-');
                    previousDash = true;
                }
            }
        }
        while (anchor.length() > 0 && anchor.charAt(anchor.length() - 1) == '-') {
            anchor.setLength(anchor.length() - 1);
        }
        return anchor.toString();
    }

    private static boolean isExternal(String target) {
        return target.startsWith("http://")
                || target.startsWith("https://")
                || target.startsWith("mailto:");
    }

    private static void assertVersionSnippets(Path markdown, String projectVersion) throws IOException {
        Matcher matcher = PROJECT_VERSION_SNIPPET.matcher(Files.readString(markdown));
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }

        assertThat(versions)
                .as("%s reactive-http-client dependency snippets", markdown.getFileName())
                .isNotEmpty()
                .containsOnly(projectVersion);
    }

    private static String projectVersion(Path pom) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(pom.toFile());
        return document.getElementsByTagName("version").item(0).getTextContent();
    }

    private static JsonNode metadata() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).as("configuration metadata resource").isNotNull();
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static String configurationReferenceMarkdown(JsonNode metadata) throws IOException {
        StringWriter out = new StringWriter();
        out.append("# Configuration Properties\n\n");
        out.append("> Generated from `reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`.\n");
        out.append("> `DocumentationReleaseArtifactTest` fails when this file drifts from metadata.\n\n");
        out.append("| Property | Type | Default | Description | Deprecated |\n");
        out.append("|---|---|---|---|---|\n");

        List<JsonNode> properties = new ArrayList<>();
        metadata.path("properties").forEach(properties::add);
        properties.sort(Comparator.comparing(property -> property.path("name").asText()));

        for (JsonNode property : properties) {
            out.append("| `").append(escapeCell(property.path("name").asText())).append("` ");
            out.append("| `").append(escapeCell(property.path("type").asText())).append("` ");
            out.append("| ").append(escapeCell(defaultValue(property))).append(" ");
            out.append("| ").append(escapeCell(property.path("description").asText())).append(" ");
            out.append("| ").append(escapeCell(deprecation(property))).append(" |\n");
        }
        return out.toString();
    }

    private static String defaultValue(JsonNode property) throws IOException {
        if (!property.has("defaultValue")) {
            return "";
        }
        return "`" + OBJECT_MAPPER.writeValueAsString(property.get("defaultValue")) + "`";
    }

    private static String deprecation(JsonNode property) {
        JsonNode deprecation = property.path("deprecation");
        if (deprecation.isMissingNode()) {
            return "";
        }
        String replacement = deprecation.path("replacement").asText("");
        if (replacement.isBlank()) {
            return deprecation.path("level").asText("warning");
        }
        return deprecation.path("level").asText("warning") + "; replacement: `" + replacement + "`";
    }

    private static String escapeCell(String value) {
        return value
                .replace("\r", "")
                .replace("\n", " ")
                .replace("|", "\\|");
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("README.md")) && Files.isDirectory(cwd.resolve("docs"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
