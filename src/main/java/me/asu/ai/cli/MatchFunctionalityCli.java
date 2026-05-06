package me.asu.ai.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.util.JacksonUtils;
import me.asu.ai.util.Utils;

public class MatchFunctionalityCli {

    public static void main(String[] args) {
        String searchTerm = parseSearchTerm(args);
        if (searchTerm == null || searchTerm.isBlank()) {
            System.err.println("Usage: java me.asu.ai.cli.MatchFunctionality <search-term> or --match <term>");
            return;
        }

        Path indexPath = Utils.findFileUpwards("index.json");
        if (indexPath == null) {
            System.err.println("index.json not found in current or any parent directory.");
            return;
        }

        List<MethodInfo> matches = search(searchTerm, indexPath);
        if (matches.isEmpty()) {
            System.out.println("No matches found for: " + searchTerm);
            return;
        }

        for (MethodInfo method : matches) {
            System.out.println("File: " + method.file);
            System.out.println("Method: " + method.methodName);
            System.out.println("Signature: " + method.signature);
            System.out.println("Begin Line: " + method.beginLine);
            System.out.println("End Line: " + method.endLine);
            System.out.println("\nMethod " + method.methodName + " in file " + method.file + " is implemented as follows:\n");

            try (BufferedReader reader = new BufferedReader(new FileReader(method.file))) {
                int currentLine = 1;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (currentLine >= method.beginLine && currentLine <= method.endLine) {
                        System.out.println(line);
                    }
                    if (currentLine > method.endLine) break;
                    currentLine++;
                }
            } catch (IOException e) {
                System.err.println("Error reading file " + method.file + ": " + e.getMessage());
            }
            System.out.println("\n" + "=".repeat(80) + "\n");
        }
    }

    private static String parseSearchTerm(String[] args) {
        if (args.length == 0) return null;
                
        // Fallback: join all non-option arguments
        List<String> terms = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                terms.add(arg);
            }
        }
        return terms.isEmpty() ? null : String.join(" ", terms);
    }

    public static List<MethodInfo> search(String searchTerm, Path indexPath) {
        try {
            List<MethodInfo> index = JacksonUtils.deserialize(
                    new FileReader(indexPath.toFile()),
                    new TypeReference<List<MethodInfo>>() { });
            
            String[] keywords = searchTerm.toLowerCase().split("\\s+");
            
            return index.stream()
                    .filter(m -> {
                        String fullName = (safeText(m.packageName) + "." 
                                + safeText(m.className) + "." 
                                + safeText(m.methodName)).toLowerCase();
                        
                        for (String keyword : keywords) {
                            if (!fullName.contains(keyword)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error reading index: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }
}
