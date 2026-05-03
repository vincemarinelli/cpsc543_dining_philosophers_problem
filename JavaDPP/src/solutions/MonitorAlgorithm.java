package solutions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import model.Philosopher;
import model.PhilosopherState;
import model.RunnerConfig;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * Deadlock- and starvation-free dining philosophers using a monitor pattern.
 *
 * <p>A single {@link ReentrantLock} with one {@link Condition} per philosopher
 * serialises all state changes; {@link #test} wakes a philosopher only when both
 * neighbours are not eating.
 */
@ThreadSafe
public class MonitorAlgorithm implements DiningRunner {

  private List<Philosopher> philosophers;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private ReentrantLock lock;
  @GuardedBy("lock") private Condition[] self;
  @GuardedBy("lock") private PhilosopherState[] state;
  private int numPhil;

  @Override
  public void initialize(int numPhilosophers, RunnerConfig config) {
    stopped.set(false);
    numPhil = numPhilosophers;
    lock = new ReentrantLock();
    self = new Condition[numPhil];
    state = new PhilosopherState[numPhil];
    for (int i = 0; i < numPhil; i++) {
      self[i] = lock.newCondition();
      state[i] = PhilosopherState.THINKING;
    }

    philosophers = new ArrayList<>();
    for (int i = 0; i < numPhil; i++) {
      final int id = i;
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

              pickUp(id);
              if (stopped.get()) {
                putDown(id);
                break;
              }

              transitionTo(PhilosopherState.EATING);
              Thread.sleep(Philosopher.randomSleepMs());

              putDown(id);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Ensure lock is not held if interrupted inside pickUp
            if (lock.isHeldByCurrentThread()) {
              lock.unlock();
            }
          }
        }
      };
      philosophers.add(p);
    }
  }

  @GuardedBy("lock")
  private void pickUp(int i) throws InterruptedException {
    lock.lockInterruptibly();
    try {
      state[i] = PhilosopherState.HUNGRY;
      test(i);
      while (state[i] != PhilosopherState.EATING) {
        self[i].await();
        if (stopped.get()) {
          return;
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @GuardedBy("lock")
  private void putDown(int i) {
    lock.lock();
    try {
      state[i] = PhilosopherState.THINKING;
      test((i - 1 + numPhil) % numPhil);
      test((i + 1) % numPhil);
    } finally {
      lock.unlock();
    }
  }

  @GuardedBy("lock")
  private void test(int i) {
    int left = (i - 1 + numPhil) % numPhil;
    int right = (i + 1) % numPhil;
    if (state[i] == PhilosopherState.HUNGRY
        && state[left] != PhilosopherState.EATING
        && state[right] != PhilosopherState.EATING) {
      state[i] = PhilosopherState.EATING;
      self[i].signal();
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
      // Wake all awaiting conditions so threads can observe stopped flag
      lock.lock();
      try {
        for (Condition c : self) {
          c.signalAll();
        }
      } finally {
        lock.unlock();
      }
    }
  }

  @Override
  public List<Philosopher> getPhilosophers() {
    return Collections.unmodifiableList(philosophers);
  }
}
