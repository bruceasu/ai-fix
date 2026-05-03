package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class IndexCliLanguageOptionTest {

    @Test
    void findSourceRootShouldSkipLanguageOption() throws Exception {
        Method method = IndexCli.class.getDeclaredMethod("findSourceRoot", String[].class);
        method.setAccessible(true);
        String result = (String) method.invoke(null, (Object) new String[]{"--language", "python", "demo"});
        assertEquals("demo", result);
    }
}
