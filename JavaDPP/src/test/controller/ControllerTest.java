package test.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import controller.Controller;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration tests for {@link Controller}. */
public class ControllerTest {

  private InputStream savedIn;
  private PrintStream savedOut;

  @BeforeEach
  void redirectStreams() {
    savedIn = System.in;
    savedOut = System.out;
    System.setOut(new PrintStream(OutputStream.nullOutputStream(), false, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStreams() {
    System.setIn(savedIn);
    System.setOut(savedOut);
  }

  private static void feedInput(String text) {
    System.setIn(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @Timeout(30)
  void runWithResourceHierarchy() {
    feedInput("2\n1\n1\n");
    assertDoesNotThrow(() -> new Controller().run());
  }

  @Test
  @Timeout(30)
  void runWithArbitrator() {
    feedInput("2\n1\n2\n");
    assertDoesNotThrow(() -> new Controller().run());
  }

  @Test
  @Timeout(30)
  void runWithChandyMisra() {
    feedInput("3\n1\n3\n");
    assertDoesNotThrow(() -> new Controller().run());
  }

  @Test
  @Timeout(30)
  void runWithMonitor() {
    feedInput("2\n1\n4\n");
    assertDoesNotThrow(() -> new Controller().run());
  }

  @Test
  @Timeout(30)
  void invalidInputRetriesUntilValid() {
    // "abc" is invalid → promptInt retries; "2", "1", "1" are the valid values
    feedInput("abc\n2\n1\n1\n");
    assertDoesNotThrow(() -> new Controller().run());
  }

  @Test
  @Timeout(30)
  void outOfRangeInputRetriesUntilValid() {
    // "1" is below minimum of 2 for numPhilosophers; "2" succeeds
    feedInput("1\n2\n1\n1\n");
    assertDoesNotThrow(() -> new Controller().run());
  }
}
