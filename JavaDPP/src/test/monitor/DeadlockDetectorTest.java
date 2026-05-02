package test.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import model.Philosopher;
import model.RunnerConfig;
import monitor.DeadlockDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for {@link DeadlockDetector}. */
public class DeadlockDetectorTest {

  /** poll=10ms, limit=3 — fires after ~40ms of stagnation. */
  private static RunnerConfig fastConfig() {
    return new RunnerConfig(2, 100, 10, 10, 3);
  }

  /** A philosopher whose {@code run()} sleeps indefinitely — thread state = TIMED_WAITING. */
  private static Philosopher sleepingPhilosopher(int id) {
    Philosopher p = new Philosopher(id, 100) {
      @Override
      public void run() {
        try {
          Thread.sleep(60_000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
    p.setDaemon(true);
    return p;
  }

  @Test
  @Timeout(5)
  void firesOnProgressStallWithAllThreadsBlocked() throws InterruptedException {
    Philosopher p0 = sleepingPhilosopher(0);
    Philosopher p1 = sleepingPhilosopher(1);
    p0.start();
    p1.start();
    // Both are TIMED_WAITING and have 0 cycles → progress stall + all blocked

    FakeRunner runner = new FakeRunner(List.of(p0, p1));
    Thread t = detectorThread(new DeadlockDetector(runner, fastConfig()));
    t.start();

    waitForStop(runner, 2000);
    t.interrupt();
    t.join(1000);
    p0.interrupt();
    p0.join(1000);
    p1.interrupt();
    p1.join(1000);

    assertTrue(runner.isStopped(), "Deadlock (progress stall) should have been detected");
    assertNotNull(runner.getStopReason());
    assertTrue(
        runner.getStopReason().contains("Deadlock") || runner.getStopReason().contains("deadlock"),
        "Reason should mention deadlock: " + runner.getStopReason());
  }

  @Test
  @Timeout(5)
  void doesNotFireWhenProgressIsMade() throws InterruptedException {
    Philosopher advancing = new Philosopher(0, 100) {
      private int tick = 0;

      @Override
      public int getCyclesCompleted() {
        return ++tick;
      }
    };

    FakeRunner runner = new FakeRunner(List.of(advancing));
    Thread t = detectorThread(new DeadlockDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(200);
    assertFalse(runner.isStopped(), "Should not fire when cycles keep increasing");
    t.interrupt();
    t.join(1000);
  }

  @Test
  @Timeout(5)
  void doesNotFireWhenThreadsAreRunnable() throws InterruptedException {
    // NEW state threads have 0 cycles but are not BLOCKED/WAITING → allBlocked=false
    Philosopher p0 = new Philosopher(0, 100);
    Philosopher p1 = new Philosopher(1, 100);
    // p0 and p1 are in NEW state — counted as non-terminated but not blocked

    FakeRunner runner = new FakeRunner(List.of(p0, p1));
    Thread t = detectorThread(new DeadlockDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(200);
    assertFalse(runner.isStopped(), "Deadlock should not fire for NEW/RUNNABLE threads");
    t.interrupt();
    t.join(1000);
  }

  @Test
  @Timeout(5)
  void doesNotFireWhenAllTerminated() throws InterruptedException {
    // Base Philosopher.run() is a no-op → thread terminates immediately
    Philosopher p = new Philosopher(0, 100);
    p.setDaemon(true);
    p.start();
    p.join(2000); // wait for TERMINATED state

    FakeRunner runner = new FakeRunner(List.of(p));
    Thread t = detectorThread(new DeadlockDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(200);
    assertFalse(runner.isStopped(), "Should not fire when all threads are TERMINATED");
    t.interrupt();
    t.join(1000);
  }

  @Test
  @Timeout(5)
  void interruptExitsGracefully() throws InterruptedException {
    FakeRunner runner = new FakeRunner(List.of(new Philosopher(0, 5)));
    Thread t = detectorThread(new DeadlockDetector(runner, fastConfig()));
    t.start();
    Thread.sleep(30);
    t.interrupt();
    t.join(1000);
    assertFalse(t.isAlive());
  }

  @Test
  @Timeout(10)
  void firesOnJvmLockCycleDeadlock() throws InterruptedException {
    ReentrantLock lockA = new ReentrantLock();
    ReentrantLock lockB = new ReentrantLock();
    CountDownLatch bothHoldingFirstLock = new CountDownLatch(2);

    Philosopher p0 = new Philosopher(0, 100) {
      @Override
      public void run() {
        try {
          lockA.lockInterruptibly();
          try {
            bothHoldingFirstLock.countDown();
            bothHoldingFirstLock.await();
            lockB.lockInterruptibly(); // blocks here — creates circular wait with p1
            lockB.unlock();
          } finally {
            if (lockA.isHeldByCurrentThread()) lockA.unlock();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };

    Philosopher p1 = new Philosopher(1, 100) {
      @Override
      public void run() {
        try {
          lockB.lockInterruptibly();
          try {
            bothHoldingFirstLock.countDown();
            bothHoldingFirstLock.await();
            lockA.lockInterruptibly(); // blocks here — creates circular wait with p0
            lockA.unlock();
          } finally {
            if (lockB.isHeldByCurrentThread()) lockB.unlock();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };

    p0.setDaemon(true);
    p1.setDaemon(true);
    p0.start();
    p1.start();

    assertTrue(bothHoldingFirstLock.await(2, TimeUnit.SECONDS), "Threads should acquire first locks");
    // Give JVM time to register threads as waiting in the lock ownership graph
    Thread.sleep(50);

    FakeRunner runner = new FakeRunner(List.of(p0, p1));
    Thread t = detectorThread(new DeadlockDetector(runner, fastConfig()));
    t.start();

    waitForStop(runner, 3000);
    t.interrupt();
    t.join(1000);

    // Resolve the deadlock by interrupting both threads so they release their locks
    p0.interrupt();
    p1.interrupt();
    p0.join(2000);
    p1.join(2000);

    assertTrue(runner.isStopped(), "Deadlock (lock cycle) should have been detected");
    assertNotNull(runner.getStopReason());
    assertTrue(runner.getStopReason().contains("lock cycle"),
        "Reason should mention lock cycle: " + runner.getStopReason());
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
