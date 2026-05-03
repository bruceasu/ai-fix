package me.asu.ai.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;

class IndexCliMultiLanguageTest {

    @Test
    void shouldIndexGoFunctions() throws Exception {
        Path root = Files.createTempDirectory("go-index");
        Files.writeString(root.resolve("go.mod"), "module example.com/demo", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("main.go"), """
                package main

                func main() {}

                func helper() {}
                """, StandardCharsets.UTF_8);

        List<MethodInfo> methods = IndexCli.build(root);

        assertEquals(2, methods.size());
        assertEquals("go", methods.get(0).language);
        assertFalse(methods.get(0).symbolName.isBlank());
    }

    @Test
    void shouldIndexPythonFunctions() throws Exception {
        Path root = Files.createTempDirectory("py-index");
        Files.writeString(root.resolve("app.py"), """
                def main():
                    pass

                def helper():
                    pass
                """, StandardCharsets.UTF_8);

        List<MethodInfo> methods = IndexCli.build(root);

        assertEquals(2, methods.size());
        assertEquals("python", methods.get(0).language);
        assertFalse(methods.get(0).symbolName.isBlank());
    }
}
