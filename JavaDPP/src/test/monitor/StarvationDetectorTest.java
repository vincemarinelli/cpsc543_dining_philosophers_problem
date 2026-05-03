package test.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import model.Philosopher;
import model.PhilosopherState;
import model.RunnerConfig;
import monitor.StarvationDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for {@link StarvationDetector}. */
public class StarvationDetectorTest {

  /** Fast config: 10 ms poll, absolute threshold 3, fraction 0.0 (disables relative), limit 30. */
  private static RunnerConfig fastConfig() {
    return new RunnerConfig(2, 100, 3, 0.0, 10, 30);
  }

  private static Philosopher philosopherWithCycles(int id, int cycles) {
    Philosopher p = new Philosopher(id, 100);
    for (int i = 0; i < cycles; i++) {
      p.transitionTo(PhilosopherState.HUNGRY);
      p.transitionTo(PhilosopherState.EATING);
      p.transitionTo(PhilosopherState.THINKING);
    }
    return p;
  }

  @Test
  @Timeout(5)
  void firesWhenPhilosopherFallsBehind() throws InterruptedException {
    // leader has threshold+1 cycles more than the laggard
    Philosopher leader = philosopherWithCycles(0, 4); // 4 cycles
    Philosopher laggard = philosopherWithCycles(1, 0); // 0 cycles → gap = 4 > threshold 3

    FakeRunner runner = new FakeRunner(List.of(leader, laggard));
    Thread t = detectorThread(new StarvationDetector(runner, fastConfig()));
    t.start();

    waitForStop(runner, 3000);
    t.interrupt();
    t.join(1000);

    assertTrue(runner.isStopped(), "Starvation should have been detected");
    assertNotNull(runner.getStopReason());
    assertTrue(runner.getStopReason().contains("Starvation"));
  }

  @Test
  @Timeout(5)
  void doesNotFireWhenGapIsBelowThreshold() throws InterruptedException {
    // gap = 2, threshold = 3 → no starvation
    Philosopher leader = philosopherWithCycles(0, 2);
    Philosopher follower = philosopherWithCycles(1, 0);

    FakeRunner runner = new FakeRunner(List.of(leader, follower));
    Thread t = detectorThread(new StarvationDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(200); // give it several poll intervals
    assertFalse(runner.isStopped(), "Should not fire for gap=2 with threshold=3");
    t.interrupt();
    t.join(1000);
  }

  @Test
  @Timeout(5)
  void interruptExitsGracefully() throws InterruptedException {
    FakeRunner runner = new FakeRunner(List.of(new Philosopher(0, 5)));
    Thread t = detectorThread(new StarvationDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(30);
    t.interrupt();
    t.join(1000);
    assertFalse(t.isAlive());
  }

  private static Thread detectorThread(Runnable r) {
    Thread t = new Thread(r);
    t.setDaemon(true);
    return t;
  }

  private static void waitForStop(FakeRunner runner, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (!runner.isStopped() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }
}
