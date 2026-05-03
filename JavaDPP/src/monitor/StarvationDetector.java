package monitor;

import java.util.List;
import model.Philosopher;
import model.RunnerConfig;
import net.jcip.annotations.ThreadSafe;
import solutions.DiningRunner;

/**
 * Daemon monitor that detects philosopher starvation by tracking cycle-count gaps.
 *
 * <p>A philosopher is considered starved if the gap between its cycle count and the cycle count
 * of the philosopher with the highest cycle count exceeds a threshold. The threshold is computed
 * as the maximum of the configured starvation cycle threshold and the starvation relative
 * fraction of the leader's cycle count.
 *
 * Formula: {@code threshold = max(starvationCycleThreshold, leader * starvationRelativeFraction)}
 *
 * starvationRelativeFraction is computed as {@code fraction * leader.getCyclesCompleted()}
 * where {@code fraction} is the configured starvation relative fraction.
 */
@ThreadSafe
public class StarvationDetector implements Runnable {

  private final DiningRunner runner;
  private final RunnerConfig config;

  /** Creates a starvation detector for the given runner and configuration. */
  public StarvationDetector(DiningRunner runner, RunnerConfig config) {
    this.runner = runner;
    this.config = config;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(config.progressPollIntervalMs);
        List<Philosopher> philosophers = runner.getPhilosophers();

        int maxCycles = 0;
        for (Philosopher p : philosophers) {
          maxCycles = Math.max(maxCycles, p.getCyclesCompleted());
        }

        int threshold = Math.max(config.starvationCycleThreshold,
            (int) (maxCycles * config.starvationRelativeFraction));
        for (Philosopher p : philosophers) {
          int gap = maxCycles - p.getCyclesCompleted();
          if (gap > threshold) {
            Philosopher leader = philosophers.stream()
                .max((a, b) -> a.getCyclesCompleted() - b.getCyclesCompleted())
                .orElse(p);
            String reason = String.format(
                "[ERROR] Starvation detected: P%d is %d cycles behind the leader "
                    + "(P%d: %d, P%d: %d). Terminating.",
                p.getPhilosopherId(), gap,
                leader.getPhilosopherId(), leader.getCyclesCompleted(),
                p.getPhilosopherId(), p.getCyclesCompleted());
            System.out.println(reason);
            runner.stop(reason);
            return;
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
