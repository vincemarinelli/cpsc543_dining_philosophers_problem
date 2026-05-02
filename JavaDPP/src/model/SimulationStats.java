package model;

import java.util.List;
import net.jcip.annotations.ThreadSafe;

/** Computes and prints per-philosopher timing stats and Jain's Fairness Index. */
@ThreadSafe
public class SimulationStats {

  /**
   * Prints a formatted simulation report to stdout.
   *
   * @param philosophers the list of philosophers whose stats to report
   * @param wallClockMs total wall-clock elapsed time in milliseconds
   */
  public static void print(List<Philosopher> philosophers, long wallClockMs) {
    System.out.println("\n=== Simulation Report ===");
    System.out.printf("Total wall-clock runtime: %.3fs%n%n", wallClockMs / 1000.0);

    System.out.printf(
        "%-12s %-8s %-12s %-16s %-16s %-16s%n",
        "Philosopher", "Cycles", "Avg Cycle", "Eating", "Hungry", "Thinking");

    for (Philosopher p : philosophers) {
      long total = p.getTotalEatingMs() + p.getTotalHungryMs() + p.getTotalThinkingMs();
      if (total == 0) {
        total = 1;
      }
      int cycles = Math.max(p.getCyclesCompleted(), 1);
      long avgCycle =
          (p.getTotalEatingMs() + p.getTotalHungryMs() + p.getTotalThinkingMs()) / cycles;

      System.out.printf(
          "%-12s %-8d %-12s %-16s %-16s %-16s%n",
          "P" + p.getPhilosopherId(),
          p.getCyclesCompleted(),
          avgCycle + "ms",
          fmtMs(p.getTotalEatingMs(), total),
          fmtMs(p.getTotalHungryMs(), total),
          fmtMs(p.getTotalThinkingMs(), total));
    }

    double jfi = jainFairnessIndex(philosophers);
    System.out.printf("%nFairness Index (Jain's, on eating time): %.4f%n", jfi);
  }

  private static String fmtMs(long ms, long total) {
    int pct = (int) Math.round(100.0 * ms / total);
    return ms + "ms (" + pct + "%)";
  }

  /**
   * Computes Jain's Fairness Index over {@code totalEatingMs} for each philosopher.
   * Returns 1.0 (perfectly fair) when all philosophers eat equally.
   *
   * @param philosophers the list of philosophers to evaluate
   * @return fairness index in the range [1/N, 1.0]
   */
  public static double jainFairnessIndex(List<Philosopher> philosophers) {
    double sumX = 0;
    double sumX2 = 0;
    int n = philosophers.size();
    for (Philosopher p : philosophers) {
      double x = p.getTotalEatingMs();
      sumX += x;
      sumX2 += x * x;
    }
    if (sumX2 == 0) {
      return 1.0;
    }
    return (sumX * sumX) / (n * sumX2);
  }
}
