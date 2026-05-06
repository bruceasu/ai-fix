package me.asu.ai.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import me.asu.ai.model.ProjectSummary;

public class ContextSurgerySupport {

    public static ProjectSummary performSurgery(ProjectSummary summary, String query) {
        if (query == null || query.isBlank()) {
            return summary;
        }

        String lowerQuery = query.toLowerCase();
        Set<String> keywords = extractKeywords(lowerQuery);

        ProjectSummary slim = new ProjectSummary();
        slim.projectName = summary.projectName;
        slim.projectRoot = summary.projectRoot;
        slim.primaryLanguage = summary.primaryLanguage;
        
        // Filter components based on keywords
        slim.controllers = filterByKeywords(summary.controllers, keywords);
        slim.services = filterByKeywords(summary.services, keywords);
        slim.repositories = filterByKeywords(summary.repositories, keywords);
        slim.configs = filterByKeywords(summary.configs, keywords);
        slim.topClasses = filterByKeywords(summary.topClasses, keywords);

        // Find relevant dependencies
        Set<String> relevantClasses = new HashSet<>();
        slim.controllers.forEach(c -> relevantClasses.add(c.className));
        slim.services.forEach(c -> relevantClasses.add(c.className));
        slim.repositories.forEach(c -> relevantClasses.add(c.className));
        
        slim.dependencies = summary.dependencies.stream()
                .filter(d -> relevantClasses.contains(d.source) || relevantClasses.contains(d.target))
                .collect(Collectors.toList());

        slim.architecturalLayers = summary.architecturalLayers;
        slim.externalIntegrations = summary.externalIntegrations;
        
        return slim;
    }

    private static Set<String> extractKeywords(String query) {
        Set<String> keywords = new HashSet<>();
        String[] parts = query.split("\\s+");
        for (String part : parts) {
            if (part.length() > 2) {
                keywords.add(part);
            }
        }
        return keywords;
    }

    private static List<ProjectSummary.ComponentSummary> filterByKeywords(
            List<ProjectSummary.ComponentSummary> original, 
            Set<String> keywords) {
        return original.stream()
                .filter(c -> matches(c, keywords))
                .limit(10)
                .collect(Collectors.toList());
    }

    private static boolean matches(ProjectSummary.ComponentSummary component, Set<String> keywords) {
        String content = (component.className + " " + component.packageName + " " + String.join(" ", component.annotations)).toLowerCase();
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
