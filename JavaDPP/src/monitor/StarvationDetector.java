package monitor;

import java.util.List;
import model.Philosopher;
import model.RunnerConfig;
import net.jcip.annotations.ThreadSafe;
import solutions.DiningRunner;

/** Daemon monitor that detects philosopher starvation by tracking cycle-count gaps. */
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

        for (Philosopher p : philosophers) {
          int gap = maxCycles - p.getCyclesCompleted();
          if (gap > config.starvationCycleThreshold) {
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
