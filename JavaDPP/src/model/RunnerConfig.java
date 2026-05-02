package model;

import net.jcip.annotations.Immutable;

/** Immutable configuration snapshot passed to every {@link solutions.DiningRunner}. */
@Immutable
public class RunnerConfig {

  public final int numPhilosophers;
  public final int targetCycles;
  public final int starvationCycleThreshold;
  public final int progressPollIntervalMs;
  public final int noProgressPollLimit;

  /** Constructs a config with sensible defaults for the monitoring thresholds. */
  public RunnerConfig(int numPhilosophers, int targetCycles) {
    this(numPhilosophers, targetCycles, 10, 200, 30);
  }

  /**
   * Constructs a fully-specified config.
   *
   * @param numPhilosophers number of philosopher threads
   * @param targetCycles cycles each philosopher must complete
   * @param starvationCycleThreshold max cycle-gap before starvation is declared
   * @param progressPollIntervalMs polling interval for monitor daemons (ms)
   * @param noProgressPollLimit consecutive stagnant polls before deadlock/livelock is declared
   */
  public RunnerConfig(
      int numPhilosophers,
      int targetCycles,
      int starvationCycleThreshold,
      int progressPollIntervalMs,
      int noProgressPollLimit) {
    this.numPhilosophers = numPhilosophers;
    this.targetCycles = targetCycles;
    this.starvationCycleThreshold = starvationCycleThreshold;
    this.progressPollIntervalMs = progressPollIntervalMs;
    this.noProgressPollLimit = noProgressPollLimit;
  }
}
