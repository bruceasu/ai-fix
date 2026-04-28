
package me.asu.ai.model;

import java.util.List;

public class MethodInfo {
    public String projectRoot;
    public String file;
    public String className;
    public String methodName;
    public String signature;
    public List<String> annotations;
    public List<String> calls;
    public int beginLine;
    public int endLine;
}
