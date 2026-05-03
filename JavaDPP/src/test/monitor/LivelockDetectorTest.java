package test.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import model.Philosopher;
import model.RunnerConfig;
import monitor.LivelockDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for {@link LivelockDetector}. */
public class LivelockDetectorTest {

  /** poll=10ms, limit=3 — fires after ~40ms of stagnation. */
  private static RunnerConfig fastConfig() {
    return new RunnerConfig(2, 100, 10, 0.0, 10, 3);
  }

  @Test
  @Timeout(5)
  void firesWhenActiveThreadsMakeNoProgress() throws InterruptedException {
    // Philosophers in NEW state (not started) → anyRunnable=true; cycles=0 forever
    Philosopher p0 = new Philosopher(0, 100);
    Philosopher p1 = new Philosopher(1, 100);

    FakeRunner runner = new FakeRunner(List.of(p0, p1));
    Thread t = detectorThread(new LivelockDetector(runner, fastConfig()));
    t.start();

    waitForStop(runner, 2000);
    t.interrupt();
    t.join(1000);

    assertTrue(runner.isStopped(), "Livelock should have been detected");
    assertNotNull(runner.getStopReason());
    assertTrue(runner.getStopReason().contains("Livelock"));
  }

  @Test
  @Timeout(5)
  void doesNotFireWhenProgressIsMade() throws InterruptedException {
    // Philosopher increments its cycle count between polls — detector should stay quiet
    Philosopher advancing = new Philosopher(0, 100) {
      private int tick = 0;

      @Override
      public int getCyclesCompleted() {
        return ++tick;
      }
    };

    FakeRunner runner = new FakeRunner(List.of(advancing));
    Thread t = detectorThread(new LivelockDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(200);
    assertFalse(runner.isStopped(), "Should not fire when cycles keep increasing");
    t.interrupt();
    t.join(1000);
  }

  @Test
  @Timeout(5)
  void doesNotFireWhenThreadsAreBlocked() throws InterruptedException {
    // Blocked threads → anyRunnable=false → livelock check skipped even with no progress
    Philosopher sleeping = new Philosopher(0, 100) {
      @Override
      public void run() {
        try {
          Thread.sleep(60_000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
    sleeping.setDaemon(true);
    sleeping.start(); // TIMED_WAITING — not RUNNABLE

    FakeRunner runner = new FakeRunner(List.of(sleeping));
    Thread t = detectorThread(new LivelockDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(200);
    assertFalse(runner.isStopped(), "Livelock should not fire for TIMED_WAITING thread");
    t.interrupt();
    t.join(1000);
    sleeping.interrupt();
    sleeping.join(1000);
  }

  @Test
  @Timeout(5)
  void interruptExitsGracefully() throws InterruptedException {
    FakeRunner runner = new FakeRunner(List.of(new Philosopher(0, 5)));
    Thread t = detectorThread(new LivelockDetector(runner, fastConfig()));
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
