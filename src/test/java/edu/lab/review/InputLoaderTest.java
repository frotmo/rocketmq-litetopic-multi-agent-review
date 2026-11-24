package edu.lab.review;

import edu.lab.review.analysis.InputLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputLoaderTest {
    @Test
    void loadsLocalFile() throws Exception {
        InputLoader.LoadedInput input = InputLoader.load("samples/InsecureOrderService.java", null, null, null);
        assertEquals("file", input.type());
        assertEquals("java", input.language());
        assertTrue(input.code().contains("class OrderController"));
    }

    @Test
    void loadsInlineSnippet() throws Exception {
        InputLoader.LoadedInput input = InputLoader.load(null, null, "class A {}", null);
        assertEquals("snippet", input.type());
        assertEquals("java", input.language());
    }
}