package me.asu.ai.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectSummary {
    public String projectRoot;
    public String projectName;
    public String primaryLanguage;
    public boolean mavenProject;
    public boolean gradleProject;
    public List<String> buildFiles = new ArrayList<>();
    public int sourceFileCount;
    public int javaFileCount;
    public int goFileCount;
    public int pythonFileCount;
    public int packageCount;
    public int classCount;
    public int interfaceCount;
    public int enumCount;
    public int methodCount;
    public int functionCount;
    public int controllerCount;
    public int serviceCount;
    public int repositoryCount;
    public int configCount;
    public int componentCount;
    public List<String> entryPoints = new ArrayList<>();
    public List<String> packages = new ArrayList<>();
    public List<String> externalIntegrations = new ArrayList<>();
    public List<ComponentSummary> topClasses = new ArrayList<>();
    public List<ComponentSummary> controllers = new ArrayList<>();
    public List<ComponentSummary> services = new ArrayList<>();
    public List<ComponentSummary> repositories = new ArrayList<>();
    public List<ComponentSummary> configs = new ArrayList<>();

    public static class ComponentSummary {
        public String file;
        public String packageName;
        public String className;
        public String kind;
        public List<String> annotations = new ArrayList<>();
        public int methodCount;
    }
}
