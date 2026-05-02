package monitor;

import java.util.List;
import model.Philosopher;
import model.RunnerConfig;
import net.jcip.annotations.ThreadSafe;
import solutions.DiningRunner;

/**
 * Daemon monitor that detects livelock: no progress in cycle count while at least one thread
 * remains {@code RUNNABLE}.
 */
@ThreadSafe
public class LivelockDetector implements Runnable {

  private final DiningRunner runner;
  private final RunnerConfig config;

  /** Creates a livelock detector for the given runner and configuration. */
  public LivelockDetector(DiningRunner runner, RunnerConfig config) {
    this.runner = runner;
    this.config = config;
  }

  @Override
  public void run() {
    try {
      int noProgressCount = 0;
      int lastTotal = -1;

      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(config.progressPollIntervalMs);
        List<Philosopher> philosophers = runner.getPhilosophers();

        int total = 0;
        for (Philosopher p : philosophers) {
          total += p.getCyclesCompleted();
        }

        if (total == lastTotal) {
          noProgressCount++;
        } else {
          noProgressCount = 0;
          lastTotal = total;
        }

        if (noProgressCount >= config.noProgressPollLimit) {
          // Check if any thread is RUNNABLE (not blocked/waiting)
          boolean anyRunnable = false;
          for (Philosopher p : philosophers) {
            Thread.State ts = p.getState();
            if (ts == Thread.State.RUNNABLE || ts == Thread.State.NEW) {
              anyRunnable = true;
              break;
            }
          }

          if (anyRunnable) {
            String reason = String.format(
                "[ERROR] Livelock detected: 0 cycles completed across %d "
                    + "consecutive polls with active threads. Terminating.",
                config.noProgressPollLimit);
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
