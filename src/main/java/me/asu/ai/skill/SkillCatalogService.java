package me.asu.ai.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import me.asu.ai.config.AppConfig;

public class SkillCatalogService {

    private static final List<String> BUILTIN_SKILL_NAMES = List.of(
            "summarize-and-echo",
            "analyze-java-error",
            "search-doc-and-explain",
            "analyze-test-failure",
            "project-summary-and-explain",
            "review-changed-files",
            "explain-file",
            "explain-symbol",
            "find-entry-points",
            "explain-project-structure",
            "review-method-optimization");
    private static final List<String> SUPPORTED_FILENAMES = List.of("skill.json", "skill.yaml", "skill.yml");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final AppConfig config;

    public SkillCatalogService() {
        this(AppConfig.load());
    }

    public SkillCatalogService(AppConfig config) {
        this.config = config;
    }

    public List<SkillDefinition> all() {
        Map<String, SkillDefinition> merged = new LinkedHashMap<>();
        loadBuiltinSkills().forEach(skill -> merged.put(skill.name, skill));
        loadFilesystemSkills().forEach(skill -> {
            if (merged.containsKey(skill.name)) {
                return;
            }
            merged.put(skill.name, skill);
        });
        return List.copyOf(merged.values());
    }

    public SkillDefinition getRequired(String name) {
        return all().stream()
                .filter(skill -> skill.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + name));
    }

    public Optional<Path> findMarkdownOnlySkillDocument(String name) {
        Path skillsDir = config.getSkillsDirectory();
        if (!Files.isDirectory(skillsDir)) {
            return Optional.empty();
        }
        Path skillDir = skillsDir.resolve(name);
        if (!Files.isDirectory(skillDir)) {
            return Optional.empty();
        }
        boolean hasStructuredDefinition = SUPPORTED_FILENAMES.stream()
                .map(skillDir::resolve)
                .anyMatch(Files::isRegularFile);
        if (hasStructuredDefinition) {
            return Optional.empty();
        }
        for (String fileName : SkillMarkdownSupport.DOCUMENT_FILENAMES) {
            Path candidate = skillDir.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private List<SkillDefinition> loadBuiltinSkills() {
        return BUILTIN_SKILL_NAMES.stream()
                .map(this::loadClasspathSkill)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<SkillDefinition> loadFilesystemSkills() {
        Path skillsDir = config.getSkillsDirectory();
        if (!Files.isDirectory(skillsDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(skillsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .filter(path -> SUPPORTED_FILENAMES.contains(path.getFileName().toString()))
                    .map(this::loadFilesystemSkill)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan skills directory: " + skillsDir, e);
        }
    }

    private SkillDefinition loadClasspathSkill(String name) {
        for (String fileName : SUPPORTED_FILENAMES) {
            String resource = "skills/" + name + "/" + fileName;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    continue;
                }
                SkillDefinition definition = readDefinition(input, resource);
                definition.skillHome = "classpath:" + resource;
                attachClasspathMarkdown(definition, name);
                return definition;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load skill definition: " + resource, e);
            }
        }
        return null;
    }

    private SkillDefinition loadFilesystemSkill(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            SkillDefinition definition = readDefinition(input, path.getFileName().toString());
            definition.skillHome = path.toAbsolutePath().normalize().toString();
            attachFilesystemMarkdown(definition, path.getParent());
            return definition;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load skill definition: " + path, e);
        }
    }

    private void attachClasspathMarkdown(SkillDefinition definition, String skillName) throws IOException {
        for (String fileName : SkillMarkdownSupport.DOCUMENT_FILENAMES) {
            String resource = "skills/" + skillName + "/" + fileName;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    continue;
                }
                definition.markdownHome = "classpath:" + resource;
                definition.markdownDescription = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return;
            }
        }
    }

    private void attachFilesystemMarkdown(SkillDefinition definition, Path skillDir) throws IOException {
        if (skillDir == null || !Files.isDirectory(skillDir)) {
            return;
        }
        for (String fileName : SkillMarkdownSupport.DOCUMENT_FILENAMES) {
            Path candidate = skillDir.resolve(fileName);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            definition.markdownHome = candidate.toAbsolutePath().normalize().toString();
            definition.markdownDescription = Files.readString(candidate, java.nio.charset.StandardCharsets.UTF_8);
            return;
        }
    }

    private SkillDefinition readDefinition(InputStream input, String sourceName) throws IOException {
        return selectMapper(sourceName).readValue(input, SkillDefinition.class);
    }

    private ObjectMapper selectMapper(String sourceName) {
        return sourceName.endsWith(".yaml") || sourceName.endsWith(".yml") ? yamlMapper : jsonMapper;
    }
}
