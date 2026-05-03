
package me.asu.ai.model;

import java.util.List;

public class MethodInfo {
    public String projectRoot;
    public String language;
    public String file;
    public String packageName;
    public String className;
    public String containerName;
    public String symbolName;
    public String symbolType;
    public String methodName;
    public String signature;
    public List<String> annotations;
    public List<String> calls;
    public int beginLine;
    public int endLine;
}
