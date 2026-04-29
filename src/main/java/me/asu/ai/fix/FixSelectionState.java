package me.asu.ai.fix;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import me.asu.ai.model.MethodInfo;

public class FixSelectionState {

    private final int pageSize;
    private final Deque<SearchState> history = new LinkedList<>();
    private List<MethodInfo> currentTargets;
    private String currentQuery;
    private int currentPage;

    public FixSelectionState(List<MethodInfo> targets, int pageSize, String matchQuery) {
        this.currentTargets = new ArrayList<>(targets);
        this.pageSize = pageSize;
        this.currentQuery = matchQuery == null ? "" : matchQuery.trim();
        this.currentPage = 0;
    }

    public List<MethodInfo> currentTargets() {
        return currentTargets;
    }

    public String currentQuery() {
        return currentQuery;
    }

    public int currentPage() {
        return currentPage;
    }

    public int totalPages() {
        return Math.max(1, (currentTargets.size() + pageSize - 1) / pageSize);
    }

    public void clampPage() {
        if (currentPage < 0) {
            currentPage = 0;
        } else if (currentPage >= totalPages()) {
            currentPage = totalPages() - 1;
        }
    }

    public int pageStart() {
        return currentPage * pageSize;
    }

    public int pageEnd() {
        return Math.min(pageStart() + pageSize, currentTargets.size());
    }

    public List<MethodInfo> visibleTargets() {
        return currentTargets.subList(pageStart(), pageEnd());
    }

    public void nextPage() {
        if (currentPage + 1 < totalPages()) {
            currentPage++;
        }
    }

    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    public boolean isFirstPage() {
        return currentPage <= 0;
    }

    public boolean isLastPage() {
        return currentPage + 1 >= totalPages();
    }

    public boolean jumpToPage(int page) {
        if (page < 1 || page > totalPages()) {
            return false;
        }
        currentPage = page - 1;
        return true;
    }

    public void refine(List<MethodInfo> refinedTargets, String query) {
        history.addLast(new SearchState(new ArrayList<>(currentTargets), currentQuery, currentPage));
        currentTargets = refinedTargets;
        currentQuery = query;
        currentPage = 0;
    }

    public boolean canGoBack() {
        return !history.isEmpty();
    }

    public void goBack() {
        SearchState previous = history.removeLast();
        currentTargets = new ArrayList<>(previous.targets());
        currentQuery = previous.query();
        currentPage = previous.page();
    }

    private record SearchState(List<MethodInfo> targets, String query, int page) {
    }
}
