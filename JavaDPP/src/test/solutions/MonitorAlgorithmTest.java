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
import solutions.MonitorAlgorithm;

/** Integration tests for {@link MonitorAlgorithm}. */
public class MonitorAlgorithmTest {

  private MonitorAlgorithm algo;
  private RunnerConfig oneRound;

  @BeforeEach
  void setUp() {
    algo = new MonitorAlgorithm();
    oneRound = new RunnerConfig(3, 1);
  }

  @Test
  void initializeCreatesCorrectNumberOfPhilosophers() {
    algo.initialize(3, oneRound);
    assertEquals(3, algo.getPhilosophers().size());
  }

  @Test
  void getPhilosophersReturnsUnmodifiableList() {
    algo.initialize(3, oneRound);
    assertThrows(UnsupportedOperationException.class,
        () -> algo.getPhilosophers().add(null));
  }

  @Test
  @Timeout(15)
  void runsToCompletion() throws InterruptedException {
    algo.initialize(3, oneRound);
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
    RunnerConfig longRun = new RunnerConfig(3, 1000);
    algo.initialize(3, longRun);
    algo.start();
    Thread.sleep(80);
    algo.stop("test");
    for (Philosopher p : algo.getPhilosophers()) {
      p.join(3000);
      assertFalse(p.isAlive(), "P" + p.getPhilosopherId() + " still alive after stop");
    }
  }

  @Test
  void stopIsIdempotent() {
    algo.initialize(3, oneRound);
    assertDoesNotThrow(() -> {
      algo.stop("first");
      algo.stop("second");
    });
  }

  @Test
  @Timeout(15)
  void stopBeforeStartDoesNotHang() throws InterruptedException {
    algo.initialize(3, oneRound);
    algo.stop("preemptive");
    // start after stop: philosophers observe stopped=true and exit immediately
    algo.start();
    for (Philosopher p : algo.getPhilosophers()) {
      p.join(3000);
      assertFalse(p.isAlive());
    }
  }
}
