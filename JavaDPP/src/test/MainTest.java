package test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class MainTest {

    private InputStream savedIn;
    private PrintStream savedOut;

    @BeforeEach
    void redirectStreams() {
        savedIn = System.in;
        savedOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream(), false, StandardCharsets.UTF_8));
        System.setIn(new ByteArrayInputStream("2\n1\n1\n".getBytes(StandardCharsets.UTF_8)));
    }

    @AfterEach
    void restoreStreams() {
        System.setIn(savedIn);
        System.setOut(savedOut);
    }

    @Test
    @Timeout(30)
    void mainMethodRunsWithoutException() {
        assertDoesNotThrow(() -> Main.main(new String[]{}));
    }

    @Test
    void defaultConstructorIsAccessible() {
        assertDoesNotThrow(Main::new);
    }
}
