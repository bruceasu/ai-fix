package me.asu.ai.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProblemKnowledgeRecord {
    public String timestamp;
    public String source;
    public String mode;
    public String status;
    public String problemType;
    public String classification;
    public String task;
    public String target;
    public String file;
    public String lineRange;
    public String inputSnippet;
    public String analysis;
    public String repairScheme;
    public String error;
    public Map<String, Object> metadata = new LinkedHashMap<>();
}
