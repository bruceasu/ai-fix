package me.asu.ai.fix;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import me.asu.ai.model.MethodInfo;

public class FixTargetMatcher {

    private final FixTextSupport textSupport = new FixTextSupport();

    public List<MethodInfo> filterTargets(
            List<MethodInfo> index,
            String matchQuery,
            String methodName,
            String packageFilter,
            String classFilter,
            String fileFilter,
            String callFilter,
            String annotationFilter) {
        List<MethodInfo> targets = applyMatchQuery(index, matchQuery);

        if (methodName != null) {
            String finalMethodName = methodName;
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(m.methodName, finalMethodName))
                    .collect(Collectors.toList());
        }

        if (packageFilter != null) {
            String finalPackageFilter = packageFilter;
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(textSupport.inferPackageName(m), finalPackageFilter))
                    .collect(Collectors.toList());
        }

        if (classFilter != null) {
            String finalClassFilter = classFilter;
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(m.className, finalClassFilter))
                    .collect(Collectors.toList());
        }

        if (fileFilter != null) {
            String finalFileFilter = normalizePath(fileFilter);
            targets = targets.stream()
                    .filter(m -> containsIgnoreCase(normalizePath(m.file), finalFileFilter))
                    .collect(Collectors.toList());
        }

        if (callFilter != null) {
            String finalCallFilter = callFilter;
            targets = targets.stream()
                    .filter(m -> m.calls != null && m.calls.contains(finalCallFilter))
                    .collect(Collectors.toList());
        }

        if (annotationFilter != null) {
            String finalAnnotationFilter = annotationFilter;
            targets = targets.stream()
                    .filter(m -> m.annotations != null && m.annotations.contains(finalAnnotationFilter))
                    .collect(Collectors.toList());
        }

        return targets;
    }

    public List<MethodInfo> applyMatchQuery(List<MethodInfo> index, String matchQuery) {
        if (matchQuery == null || matchQuery.isBlank()) {
            return index;
        }
        List<String> terms = textSupport.splitTerms(matchQuery);
        return index.stream()
                .map(method -> new SearchHit(method, scoreMethod(method, terms)))
                .filter(hit -> hit.score > 0)
                .sorted(Comparator.comparingInt(SearchHit::score).reversed()
                        .thenComparing(hit -> textSupport.safe(hit.method.className), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(hit -> textSupport.safe(hit.method.methodName), String.CASE_INSENSITIVE_ORDER))
                .map(SearchHit::method)
                .collect(Collectors.toList());
    }

    public int scoreMethod(MethodInfo method, List<String> terms) {
        String packageName = textSupport.inferPackageName(method).toLowerCase(Locale.ROOT);
        String className = textSupport.safe(method.className).toLowerCase(Locale.ROOT);
        String methodName = textSupport.safe(method.methodName).toLowerCase(Locale.ROOT);
        String fileName = normalizePath(textSupport.safe(method.file)).toLowerCase(Locale.ROOT);
        String signature = textSupport.safe(method.signature).toLowerCase(Locale.ROOT);
        String annotations = joinLower(method.annotations);
        String calls = joinLower(method.calls);
        String haystack = String.join(" ", packageName, className, methodName, fileName, signature, annotations, calls);

        int score = 0;
        for (String term : terms) {
            if (!haystack.contains(term)) {
                return 0;
            }
            score += scoreTerm(term, packageName, className, methodName, fileName, signature, annotations, calls);
        }
        return score;
    }

    private int scoreTerm(
            String term,
            String packageName,
            String className,
            String methodName,
            String fileName,
            String signature,
            String annotations,
            String calls) {
        int score = 1;
        if (methodName.equals(term)) {
            score += 80;
        } else if (methodName.contains(term)) {
            score += 40;
        }
        if (className.equals(term)) {
            score += 60;
        } else if (className.contains(term)) {
            score += 30;
        }
        if (packageName.contains(term)) {
            score += 20;
        }
        if (fileName.contains(term)) {
            score += 20;
        }
        if (signature.contains(term)) {
            score += 10;
        }
        if (calls.contains(term)) {
            score += 8;
        }
        if (annotations.contains(term)) {
            score += 6;
        }
        return score;
    }

    private String joinLower(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private String normalizePath(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private boolean containsIgnoreCase(String source, String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return true;
        }
        if (source == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private record SearchHit(MethodInfo method, int score) {
    }
}
