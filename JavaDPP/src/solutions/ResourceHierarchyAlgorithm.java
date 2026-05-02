package solutions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import model.Chopstick;
import model.Philosopher;
import model.PhilosopherState;
import model.RunnerConfig;
import net.jcip.annotations.ThreadSafe;

/**
 * Deadlock-free dining philosophers via resource hierarchy (Dijkstra).
 *
 * <p>Each philosopher always acquires the lower-numbered chopstick first, breaking the circular
 * wait condition.
 */
@ThreadSafe
public class ResourceHierarchyAlgorithm implements DiningRunner {

  private List<Philosopher> philosophers;
  private List<Chopstick> chopsticks;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  @Override
  public void initialize(int numPhilosophers, RunnerConfig config) {
    stopped.set(false);
    chopsticks = new ArrayList<>();
    for (int i = 0; i < numPhilosophers; i++) {
      chopsticks.add(new Chopstick(i));
    }

    philosophers = new ArrayList<>();
    for (int i = 0; i < numPhilosophers; i++) {
      final int id = i;
      final int n = numPhilosophers;
      final Chopstick first = chopsticks.get(Math.min(id, (id + 1) % n));
      final Chopstick second = chopsticks.get(Math.max(id, (id + 1) % n));

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

              first.acquire();
              try {
                second.acquire();
                try {
                  if (stopped.get()) {
                    break;
                  }
                  transitionTo(PhilosopherState.EATING);
                  Thread.sleep(Philosopher.randomSleepMs());
                } finally {
                  second.release();
                }
              } finally {
                first.release();
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
    }
  }

  @Override
  public List<Philosopher> getPhilosophers() {
    return Collections.unmodifiableList(philosophers);
  }
}
