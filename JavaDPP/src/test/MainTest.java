package test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Smoke tests for the Main entry point, accessed via reflection to avoid default-package rules. */
public class MainTest {

  private InputStream savedIn;
  private PrintStream savedOut;

  @BeforeEach
  void redirectStreams() {
    savedIn = System.in;
    savedOut = System.out;
    System.setOut(
        new PrintStream(OutputStream.nullOutputStream(), false, StandardCharsets.UTF_8));
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
    assertDoesNotThrow(() -> {
      Class<?> cls = Class.forName("Main");
      Method m = cls.getMethod("main", String[].class);
      m.invoke(null, (Object) new String[]{});
    });
  }

  @Test
  void defaultConstructorIsAccessible() {
    assertDoesNotThrow(() -> Class.forName("Main").getDeclaredConstructor().newInstance());
  }
}
