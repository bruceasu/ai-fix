package me.asu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectIndexSupportTest {

    @Test
    void shouldReturnCandidatesWhenSymbolIsAmbiguous(@TempDir Path tempDir) throws Exception {
        Path indexFile = tempDir.resolve("index.json");
        ObjectMapper mapper = new ObjectMapper();

        MethodInfo first = new MethodInfo();
        first.projectRoot = tempDir.toString();
        first.file = "a.java";
        first.containerName = "OrderService";
        first.symbolName = "createOrder";
        first.beginLine = 10;
        first.endLine = 20;

        MethodInfo second = new MethodInfo();
        second.projectRoot = tempDir.toString();
        second.file = "b.java";
        second.containerName = "LegacyOrderService";
        second.symbolName = "createOrder";
        second.beginLine = 30;
        second.endLine = 40;

        mapper.writeValue(indexFile.toFile(), List.of(first, second));

        String output = new ProjectIndexSupport().explainSymbolInput(indexFile.toString(), "createOrder", "", 5);

        assertTrue(output.contains("[candidates]"));
        assertTrue(output.contains("OrderService#createOrder"));
        assertTrue(output.contains("LegacyOrderService#createOrder"));
    }
}
