package test.solutions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import model.Philosopher;
import model.RunnerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import solutions.ChandyMisraAlgorithm;

/** Integration tests for {@link ChandyMisraAlgorithm}. */
public class ChandyMisraAlgorithmTest {

  private ChandyMisraAlgorithm algo;
  private RunnerConfig oneRound;

  @BeforeEach
  void setUp() {
    algo = new ChandyMisraAlgorithm();
    // Use 5 philosophers — the canonical problem size; avoids N=2 inbox-aliasing edge case
    oneRound = new RunnerConfig(5, 1);
  }

  @Test
  void initializeCreatesCorrectNumberOfPhilosophers() {
    algo.initialize(5, oneRound);
    assertEquals(5, algo.getPhilosophers().size());
  }

  @Test
  void getPhilosophersReturnsUnmodifiableList() {
    algo.initialize(5, oneRound);
    assertThrows(UnsupportedOperationException.class,
        () -> algo.getPhilosophers().add(null));
  }

  @Test
  @Timeout(20)
  void runsToCompletion() throws InterruptedException {
    algo.initialize(5, oneRound);
    algo.start();
    for (Philosopher p : algo.getPhilosophers()) {
      p.join();
    }
    for (Philosopher p : algo.getPhilosophers()) {
      assertEquals(1, p.getCyclesCompleted(), "P" + p.getPhilosopherId() + " did not complete");
    }
  }

  @Test
  @Timeout(10)
  void stopTerminatesRunningPhilosophers() throws InterruptedException {
    RunnerConfig longRun = new RunnerConfig(5, 1000);
    algo.initialize(5, longRun);
    algo.start();
    Thread.sleep(80);
    algo.stop("test");
    for (Philosopher p : algo.getPhilosophers()) {
      p.join(4000);
      assertFalse(p.isAlive(), "P" + p.getPhilosopherId() + " still alive after stop");
    }
  }

  @Test
  void stopIsIdempotent() {
    algo.initialize(5, oneRound);
    assertDoesNotThrow(() -> {
      algo.stop("first");
      algo.stop("second");
    });
  }

  @Test
  @Timeout(20)
  void runsMultipleCycles() throws InterruptedException {
    RunnerConfig twoRounds = new RunnerConfig(5, 2);
    algo.initialize(5, twoRounds);
    algo.start();
    for (Philosopher p : algo.getPhilosophers()) {
      p.join();
    }
    for (Philosopher p : algo.getPhilosophers()) {
      assertEquals(2, p.getCyclesCompleted());
    }
  }
}
