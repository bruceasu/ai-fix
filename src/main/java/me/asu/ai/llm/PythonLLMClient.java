package me.asu.ai.llm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple LLMClient implementation that invokes the Python `run.py` script via ProcessBuilder.
 *
 * Usage: construct with the python command and optional provider/model; generate() will call
 * the script and return the raw text output.
 */
public class PythonLLMClient implements LLMClient {

    private final String pythonCmd;
    private final Path scriptPath;
    private final String provider;
    private final String model;
    private final Duration timeout;

    public PythonLLMClient(String pythonCmd, Path scriptPath, String provider, String model) {
        this.pythonCmd = pythonCmd == null || pythonCmd.isBlank() ? "python" : pythonCmd;
        this.scriptPath = scriptPath;
        this.provider = provider == null ? "" : provider;
        this.model = model == null ? "" : model;
        this.timeout = Duration.ofSeconds(60);
        if (this.scriptPath == null || !this.scriptPath.toFile().exists()) {
            throw new IllegalStateException("LLM run script not found at: " + this.scriptPath);
        }
    }

    public PythonLLMClient(Path projectRoot) {
        this("python", projectRoot.resolve("workspace").resolve("tools").resolve("llm").resolve("run.py"), "", "");
    }

    @Override
    public String generate(String prompt) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(pythonCmd);
            cmd.add(scriptPath.toString());
            cmd.add("--prompt");
            cmd.add(prompt == null ? "" : prompt);
            cmd.add("--format");
            cmd.add("text");
            if (provider != null && !provider.isBlank()) {
                cmd.add("--provider");
                cmd.add(provider);
            }
            if (model != null && !model.isBlank()) {
                cmd.add("--model");
                cmd.add(model);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            // Inherit current env; caller can set env vars like OPENAI_API_KEY externally
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output = readStream(p.getInputStream());

            boolean finished = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IllegalStateException("LLM process timed out");
            }

            int code = p.exitValue();
            if (code != 0) {
                throw new IllegalStateException("LLM process failed (exit=" + code + "): " + output);
            }
            return output == null ? "" : output.trim();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run Python LLM client: " + e.getMessage(), e);
        }
    }

    private static String readStream(InputStream in) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
