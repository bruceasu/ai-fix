package me.asu.ai.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import me.asu.ai.config.AppConfig;
import me.asu.ai.tool.ToolExecutor;

public class SkillOrchestrator {

    private final SkillCatalogService skillCatalogService;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SkillOrchestrator(
            SkillCatalogService skillCatalogService,
            ToolExecutor toolExecutor,
            AppConfig config) {
        this.skillCatalogService = skillCatalogService;
        this.toolExecutor = toolExecutor;
    }

    public SkillExecutionResult execute(
            String skillName,
            String inputJson,
            boolean confirmed,
            String provider,
            String model) {
        return execute(skillName, inputJson, confirmed, provider, model, null);
    }

    public SkillExecutionResult execute(
            String skillName,
            String inputJson,
            boolean confirmed,
            String provider,
            String model,
            String emitYamlPath) {
        try {
            SkillDefinition skill = resolveSkillDefinition(skillName, provider, model, emitYamlPath);
            SkillPlan plan = new SkillPlanner().plan(skill, inputJson);
            SkillExecutor.ExecutionOutcome outcome = new SkillExecutor(toolExecutor, provider, model)
                    .execute(plan, confirmed);
            if (!outcome.success()) {
                return SkillExecutionResult.failure(skillName, outcome.error());
            }
            return new SkillEvaluator().evaluate(plan, outcome.context());
        } catch (Exception e) {
            return SkillExecutionResult.failure(skillName, e.getMessage());
        }
    }

    private SkillDefinition resolveSkillDefinition(
            String skillName,
            String provider,
            String model,
            String emitYamlPath) throws Exception {
        try {
            return skillCatalogService.getRequired(skillName);
        } catch (IllegalArgumentException ignored) {
            Path markdownPath = skillCatalogService.findMarkdownOnlySkillDocument(skillName)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillName));
            SkillDefinition definition = SkillMarkdownSupport.buildRuntimeSkillDefinition(
                    markdownPath,
                    skillName,
                    toolExecutor,
                    provider,
                    model,
                    true);
            if (emitYamlPath != null && !emitYamlPath.isBlank()) {
                Path outputPath = Path.of(emitYamlPath).toAbsolutePath().normalize();
                if (outputPath.getParent() != null) {
                    Files.createDirectories(outputPath.getParent());
                }
                yamlMapper.writeValue(outputPath.toFile(), SkillMarkdownSupport.toPortableSkillMap(definition));
            }
            return definition;
        }
    }
}
