package monitor;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.stream.Collectors;
import model.Philosopher;
import model.RunnerConfig;
import net.jcip.annotations.ThreadSafe;
import solutions.DiningRunner;

/**
 * Daemon monitor that detects deadlock via two mechanisms. Two mechanisms are used to
 * distinguish between deadlock and progress stall. This is because deadlock detection is
 * only useful for lock-based algorithms, while progress stall detection is only useful for
 * message-passing algorithms.
 *
 * <ol>
 *   <li>JVM lock-cycle detection ({@link ThreadMXBean#findDeadlockedThreads()}) — covers
 *       lock-based algorithms.
 *   <li>Progress stagnation with all non-terminated threads blocked/waiting — covers
 *       message-passing algorithms where queue-blocking is invisible to the JVM.
 * </ol>
 */
@ThreadSafe
public class DeadlockDetector implements Runnable {

  private final DiningRunner runner;
  private final RunnerConfig config;
  private final ThreadMXBean tmx = ManagementFactory.getThreadMXBean();

  /** Creates a deadlock detector for the given runner and configuration. */
  public DeadlockDetector(DiningRunner runner, RunnerConfig config) {
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

        // Mechanism 1: JVM lock-cycle detection
        long[] deadlocked = tmx.findDeadlockedThreads();
        if (deadlocked != null && deadlocked.length > 0) {
          List<Philosopher> locked = runner.getPhilosophers().stream()
              .filter(p -> {
                for (long tid : deadlocked) {
                  if (p.getId() == tid) {
                    return true;
                  }
                }
                return false;
              })
              .collect(Collectors.toList());
          String names = locked.stream()
              .map(p -> "P" + p.getPhilosopherId())
              .collect(Collectors.joining(", "));
          String reason = String.format(
              "[ERROR] Deadlock detected (lock cycle): threads %s in circular wait. Terminating.",
              names.isEmpty() ? "(unknown)" : names);
          System.out.println(reason);
          runner.stop(reason);
          return;
        }

        // Mechanism 2: progress stagnation + all threads blocked/waiting
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
          boolean allBlocked = true;
          boolean anyNonTerminated = false;
          for (Philosopher p : philosophers) {
            Thread.State ts = p.getState();
            if (ts == Thread.State.TERMINATED) {
              continue;
            }
            anyNonTerminated = true;
            if (ts != Thread.State.BLOCKED
                && ts != Thread.State.WAITING
                && ts != Thread.State.TIMED_WAITING) {
              allBlocked = false;
              break;
            }
          }
          if (allBlocked && anyNonTerminated) {
            String reason = String.format(
                "[ERROR] Deadlock detected (progress stall): 0 cycles in %d polls, "
                    + "all threads blocked. Terminating.",
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
