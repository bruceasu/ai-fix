package me.asu.ai.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AppConfig {

    public static final String DEFAULT_PROVIDER = "openai";
    public static final String DEFAULT_MODEL = "gpt-4.1";
    public static final String DEFAULT_GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    public static final String APP_DIR_NAME = "ai-fix";

    private final Properties properties = new Properties();
    private final List<String> loadedSources = new ArrayList<>();

    private AppConfig() {
    }

    public static AppConfig load() {
        return load(null);
    }

    public static AppConfig load(String explicitConfigPath) {
        AppConfig config = new AppConfig();
        config.loadFromProgramDirectory();
        config.loadFromUserConfigFile();
        config.loadFromExplicitConfigFile(explicitConfigPath);
        config.loadFromEnvironment();
        return config;
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public String getRequired(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + key);
        }
        return value;
    }

    public List<String> getLoadedSources() {
        return List.copyOf(loadedSources);
    }

    public void printDebugSummary() {
        System.out.println("Config debug:");
        System.out.println("- user.home=" + System.getProperty("user.home"));
        System.out.println("- loaded sources=" + (loadedSources.isEmpty() ? "(none)" : String.join(", ", loadedSources)));
        System.out.println("- app.home=" + getAppHomeDirectory());
        System.out.println("- tools.dir=" + getToolsDirectory());
        System.out.println("- skills.dir=" + getSkillsDirectory());
        System.out.println("- sessions.dir=" + getSessionsDirectory());
        System.out.println("- knowledge.dir=" + getKnowledgeDirectory());
        System.out.println("- provider=" + get("provider", DEFAULT_PROVIDER));
        System.out.println("- model=" + get("model", DEFAULT_MODEL));
        System.out.println("- prompt.template.file=" + safeValue(get("prompt.template.file")));
        System.out.println("- prompt.fix-template.file=" + safeValue(get("prompt.fix-template.file")));
        System.out.println("- openai.base.url=" + safeValue(get("openai.base.url")));
        System.out.println("- groq.base.url=" + safeValue(get("groq.base.url")));
        System.out.println("- ollama.base.url=" + safeValue(get("ollama.base.url")));
        System.out.println("- openai.api.key=" + maskSecret(get("openai.api.key")));
        System.out.println("- groq.api.key=" + maskSecret(get("groq.api.key")));
    }

    public String getFileContent(String key) {
        String pathValue = get(key);
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }

        Path path = resolveConfigPath(pathValue);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Warning: failed to read prompt template file for key '" + key
                    + "', fallback to built-in template. Path: " + path);
            return null;
        }
    }

    private void loadFromUserConfigFile() {
        Path appHome = getAppHomeDirectory();
        loadPropertiesFile(appHome.resolve("ai-fix.properties").toFile());
        loadPropertiesFile(Paths.get(System.getProperty("user.home"), ".config", "ai-fix.properties").toFile());
    }

    private void loadFromExplicitConfigFile(String explicitConfigPath) {
        if (explicitConfigPath == null || explicitConfigPath.isBlank()) {
            return;
        }
        loadPropertiesFile(Paths.get(explicitConfigPath).toAbsolutePath().normalize().toFile());
    }

    private void loadFromEnvironment() {
        putIfPresent("provider", "AI_FIX_PROVIDER");
        putIfPresent("model", "AI_FIX_MODEL");
        putIfPresent("app.home", "AI_FIX_HOME");
        putIfPresent("tools.dir", "AI_FIX_TOOLS_DIR");
        putIfPresent("skills.dir", "AI_FIX_SKILLS_DIR");
        putIfPresent("sessions.dir", "AI_FIX_SESSIONS_DIR");
        putIfPresent("knowledge.dir", "AI_FIX_KNOWLEDGE_DIR");
        putIfPresent("prompt.template.file", "AI_FIX_PROMPT_TEMPLATE_FILE");
        putIfPresent("prompt.fix-template.file", "AI_FIX_PROMPT_FIX_TEMPLATE_FILE");
        putIfPresent("openai.api.key", "OPENAI_API_KEY");
        putIfPresent("openai.base.url", "OPENAI_BASE_URL");
        putIfPresent("groq.api.key", "GROQ_API_KEY");
        putIfPresent("groq.base.url", "GROQ_BASE_URL");
        putIfPresent("model", "GROQ_MODEL");
        putIfPresent("model", "GROQ_MODE");
        putIfPresent("ollama.base.url", "OLLAMA_BASE_URL");
    }

    private void loadFromProgramDirectory() {
        File jarDir = resolveProgramDirectory();
        loadPropertiesFile(new File(jarDir, "ai-fix.properties"));
    }

    private File resolveProgramDirectory() {
        try {
            Path jarPath = Paths.get(
                    AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return jarPath.toFile().isDirectory() ? jarPath.toFile() : jarPath.getParent().toFile();
        } catch (URISyntaxException e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    private void loadPropertiesFile(File file) {
        if (!file.isFile()) {
            return;
        }

        Properties loaded = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            loaded.load(input);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config file: " + file.getAbsolutePath(), e);
        }

        for (String key : loaded.stringPropertyNames()) {
            properties.setProperty(key, loaded.getProperty(key));
        }
        loadedSources.add(file.getAbsolutePath());
    }

    private void putIfPresent(String propertyKey, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            properties.setProperty(propertyKey, value);
            loadedSources.add("env:" + envName);
        }
    }

    private Path resolveConfigPath(String pathValue) {
        Path path = Paths.get(pathValue);
        if (path.isAbsolute()) {
            return path;
        }
        return getAppHomeDirectory().resolve(path).normalize();
    }

    public Path getAppHomeDirectory() {
        String configured = get("app.home");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".config", APP_DIR_NAME)
                .toAbsolutePath()
                .normalize();
    }

    public Path getToolsDirectory() {
        String configured = get("tools.dir");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return getAppHomeDirectory().resolve("tools").normalize();
    }

    public Path getSkillsDirectory() {
        String configured = get("skills.dir");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return getAppHomeDirectory().resolve("skills").normalize();
    }

    public Path getSessionsDirectory() {
        String configured = get("sessions.dir");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return getAppHomeDirectory().resolve("sessions").normalize();
    }

    public Path getKnowledgeDirectory() {
        String configured = get("knowledge.dir");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return getAppHomeDirectory().resolve("knowledge").normalize();
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "(missing)";
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "(missing)" : value;
    }
}
