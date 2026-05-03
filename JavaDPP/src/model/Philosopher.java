package model;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.jcip.annotations.ThreadSafe;

/**
 * Abstract base for a dining philosopher thread.
 *
 * <p>Volatile fields provide safe visibility to monitor daemon threads without locking.
 * {@code stateEnteredAt} is thread-confined: written and read only by the owning philosopher
 * thread.
 */
@ThreadSafe
public class Philosopher extends Thread {

  private final int id;
  private final int targetCycles;

  // volatile provides visibility to monitor threads without requiring a lock
  private volatile PhilosopherState state = PhilosopherState.THINKING;
  private final AtomicInteger cyclesCompleted = new AtomicInteger(0);

  private volatile long totalThinkingMs = 0;
  private volatile long totalHungryMs = 0;
  private volatile long totalEatingMs = 0;

  // thread-confined: written and read only by the owning philosopher thread
  private long stateEnteredAt;

  /** Minimum per-state sleep duration in milliseconds. */
  public static final int MIN_SLEEP_MS = 100;

  /** Maximum per-state sleep duration in milliseconds. */
  public static final int MAX_SLEEP_MS = 500;

  /**
   * Creates a philosopher with the given id and target cycle count.
   *
   * @param id philosopher index (0-based)
   * @param targetCycles number of eat-think cycles to complete before terminating
   */
  public Philosopher(int id, int targetCycles) {
    super("P" + id);
    this.id = id;
    this.targetCycles = targetCycles;
    this.stateEnteredAt = System.currentTimeMillis();
  }

  /** Returns this philosopher's 0-based index. */
  public int getPhilosopherId() {
    return id;
  }

  /** Returns the number of eat-think cycles this philosopher must complete. */
  public int getTargetCycles() {
    return targetCycles;
  }

  /** Returns the current lifecycle state. */
  public PhilosopherState getPhilosopherState() {
    return state;
  }

  /** Returns the number of eat-think cycles completed so far. */
  public int getCyclesCompleted() {
    return cyclesCompleted.get();
  }

  /** Returns total milliseconds spent thinking across all cycles. */
  public long getTotalThinkingMs() {
    return totalThinkingMs;
  }

  /** Returns total milliseconds spent hungry (waiting for forks) across all cycles. */
  public long getTotalHungryMs() {
    return totalHungryMs;
  }

  /** Returns total milliseconds spent eating across all cycles. */
  public long getTotalEatingMs() {
    return totalEatingMs;
  }

  /**
   * Transitions to the next state, accumulating time spent in the current state.
   * Increments {@code cyclesCompleted} when transitioning from EATING to THINKING.
   *
   * @param next the state to transition into
   */
  public void transitionTo(PhilosopherState next) {
    long now = System.currentTimeMillis();
    long elapsed = now - stateEnteredAt;
    switch (state) {
      case THINKING:
        totalThinkingMs += elapsed;
        break;
      case HUNGRY:
        totalHungryMs += elapsed;
        break;
      case EATING:
        totalEatingMs += elapsed;
        break;
      default:
        break;
    }
    if (state == PhilosopherState.EATING && next == PhilosopherState.THINKING) {
      cyclesCompleted.incrementAndGet();
    }
    state = next;
    stateEnteredAt = now;
  }

  /** Returns {@code true} once this philosopher has completed all target cycles. */
  public boolean isDone() {
    return cyclesCompleted.get() >= targetCycles;
  }

  /** Returns a random sleep duration between {@code MIN_SLEEP_MS} and {@code MAX_SLEEP_MS} ms. */
  public static int randomSleepMs() {
    return ThreadLocalRandom.current().nextInt(MIN_SLEEP_MS, MAX_SLEEP_MS + 1);
  }

  @Override
  public void run() {
    // subclasses override with the algorithm-specific loop
  }
}
