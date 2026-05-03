package solutions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import model.Chopstick;
import model.Philosopher;
import model.PhilosopherState;
import model.RunnerConfig;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * Deadlock-free dining philosophers via a central arbitrator (waiter).
 *
 * <p>A single {@link Waiter} object serialises chopstick grants; at most ⌊N/2⌋ philosophers eat
 * simultaneously.
 */
@ThreadSafe
public class ArbitratorAlgorithm implements DiningRunner {

  private List<Philosopher> philosophers;
  private Waiter waiter;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  @ThreadSafe
  private static class Waiter {

    @GuardedBy("this")
    private final boolean[] chopstickInUse;

    Waiter(int n) {
      chopstickInUse = new boolean[n];
    }

    synchronized void requestChopsticks(int left, int right) throws InterruptedException {
      while (chopstickInUse[left] || chopstickInUse[right]) {
        wait();
      }
      chopstickInUse[left] = true;
      chopstickInUse[right] = true;
    }

    synchronized void releaseChopsticks(int left, int right) {
      chopstickInUse[left] = false;
      chopstickInUse[right] = false;
      notifyAll();
    }
  }

  @Override
  public void initialize(int numPhilosophers, RunnerConfig config) {
    stopped.set(false);
    waiter = new Waiter(numPhilosophers);

    philosophers = new ArrayList<>();
    for (int i = 0; i < numPhilosophers; i++) {
      final int id = i;
      final int n = numPhilosophers;
      final int left = id;
      final int right = (id + 1) % n;

      Philosopher p = new Philosopher(id, config.targetCycles) {
        @Override
        public void run() {
          try {
            while (!isDone() && !stopped.get()) {
              transitionTo(PhilosopherState.THINKING);
              Thread.sleep(Philosopher.randomSleepMs());

              if (stopped.get()) {
                break;
              }
              transitionTo(PhilosopherState.HUNGRY);

              waiter.requestChopsticks(left, right);
              try {
                if (stopped.get()) {
                  waiter.releaseChopsticks(left, right);
                  break;
                }
                transitionTo(PhilosopherState.EATING);
                Thread.sleep(Philosopher.randomSleepMs());
              } finally {
                waiter.releaseChopsticks(left, right);
              }
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      };
      philosophers.add(p);
    }
  }

  @Override
  public void start() {
    for (Philosopher p : philosophers) {
      p.start();
    }
  }

  @Override
  public void stop(String reason) {
    if (stopped.compareAndSet(false, true)) {
      for (Philosopher p : philosophers) {
        p.interrupt();
      }
      synchronized (waiter) {
        waiter.notifyAll();
      }
    }
  }

  @Override
  public List<Philosopher> getPhilosophers() {
    return Collections.unmodifiableList(philosophers);
  }
}
