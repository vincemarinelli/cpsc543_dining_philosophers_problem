package test.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import model.RunnerConfig;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RunnerConfig}. */
public class RunnerConfigTest {

  @Test
  void defaultsApplied() {
    RunnerConfig c = new RunnerConfig(5, 10);
    assertEquals(5, c.numPhilosophers);
    assertEquals(10, c.targetCycles);
    assertEquals(10, c.starvationCycleThreshold);
    assertEquals(200, c.progressPollIntervalMs);
    assertEquals(30, c.noProgressPollLimit);
  }

  @Test
  void fullConstructorUsesProvidedValues() {
    RunnerConfig c = new RunnerConfig(3, 5, 4, 50, 8);
    assertEquals(3, c.numPhilosophers);
    assertEquals(5, c.targetCycles);
    assertEquals(4, c.starvationCycleThreshold);
    assertEquals(50, c.progressPollIntervalMs);
    assertEquals(8, c.noProgressPollLimit);
  }
}
