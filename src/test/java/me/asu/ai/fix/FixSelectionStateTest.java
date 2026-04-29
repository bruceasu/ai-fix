package me.asu.ai.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;

class FixSelectionStateTest {

    @Test
    void paginationShouldExposeVisibleTargetsAndSupportPageNavigation() {
        List<MethodInfo> methods = methods(25);
        FixSelectionState state = new FixSelectionState(methods, 10, "retry");

        assertEquals(3, state.totalPages());
        assertEquals(0, state.currentPage());
        assertEquals(0, state.pageStart());
        assertEquals(10, state.pageEnd());
        assertEquals(10, state.visibleTargets().size());
        assertEquals("method1", state.visibleTargets().get(0).methodName);

        state.nextPage();

        assertEquals(1, state.currentPage());
        assertEquals(10, state.pageStart());
        assertEquals(20, state.pageEnd());
        assertEquals("method11", state.visibleTargets().get(0).methodName);

        assertTrue(state.jumpToPage(3));
        assertEquals(2, state.currentPage());
        assertEquals(20, state.pageStart());
        assertEquals(25, state.pageEnd());
        assertEquals(5, state.visibleTargets().size());
        assertEquals("method21", state.visibleTargets().get(0).methodName);
        assertTrue(state.isLastPage());

        state.previousPage();

        assertEquals(1, state.currentPage());
        assertFalse(state.isFirstPage());
    }

    @Test
    void jumpToPageShouldRejectOutOfRangeValues() {
        FixSelectionState state = new FixSelectionState(methods(3), 2, "");

        assertFalse(state.jumpToPage(0));
        assertFalse(state.jumpToPage(3));
        assertEquals(0, state.currentPage());
    }

    @Test
    void refineAndGoBackShouldRestorePreviousTargetsQueryAndPage() {
        List<MethodInfo> methods = methods(12);
        FixSelectionState state = new FixSelectionState(methods, 5, "main");
        state.jumpToPage(2);

        List<MethodInfo> refined = methods.subList(2, 5);
        state.refine(refined, "service");

        assertEquals("service", state.currentQuery());
        assertEquals(0, state.currentPage());
        assertIterableEquals(refined, state.currentTargets());
        assertTrue(state.canGoBack());

        state.goBack();

        assertEquals("main", state.currentQuery());
        assertEquals(1, state.currentPage());
        assertIterableEquals(methods, state.currentTargets());
        assertFalse(state.canGoBack());
    }

    @Test
    void clampPageShouldBringInvalidPageBackIntoRange() {
        FixSelectionState state = new FixSelectionState(methods(6), 2, "");
        state.jumpToPage(3);
        state.nextPage();
        state.nextPage();

        state.clampPage();

        assertEquals(2, state.currentPage());
        assertEquals(4, state.pageStart());
        assertEquals(6, state.pageEnd());
    }

    private List<MethodInfo> methods(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> method(i, "method" + i))
                .toList();
    }

    private MethodInfo method(int index, String methodName) {
        MethodInfo method = new MethodInfo();
        method.projectRoot = ".";
        method.file = "src/main/java/me/asu/ai/fix/FixSelectionState.java";
        method.className = "FixSelectionState";
        method.methodName = methodName;
        method.signature = methodName + "()";
        method.beginLine = index;
        method.endLine = index + 1;
        return method;
    }
}
