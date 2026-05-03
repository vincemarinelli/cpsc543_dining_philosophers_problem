package model;

import net.jcip.annotations.Immutable;

/** Immutable configuration snapshot passed to every {@link solutions.DiningRunner}. */
@Immutable
public class RunnerConfig {

  public final int numPhilosophers;
  public final int targetCycles;
  public final int starvationCycleThreshold;
  public final double starvationRelativeFraction;
  public final int progressPollIntervalMs;
  public final int noProgressPollLimit;

  /** Constructs a config with sensible defaults for the monitoring thresholds. */
  public RunnerConfig(int numPhilosophers, int targetCycles) {
    this(numPhilosophers, targetCycles, 10, 0.20, 200, 30);
  }

  /**
   * Constructs a fully-specified config.
   *
   * @param numPhilosophers number of philosopher threads
   * @param targetCycles cycles each philosopher must complete
   * @param starvationCycleThreshold absolute floor for the starvation cycle-gap threshold
   * @param starvationRelativeFraction fraction of the leader's cycle count used as the relative
   *     threshold; effective threshold = max(starvationCycleThreshold, leader * fraction)
   * @param progressPollIntervalMs polling interval for monitor daemons (ms)
   * @param noProgressPollLimit consecutive stagnant polls before deadlock/livelock is declared
   */
  public RunnerConfig(
      int numPhilosophers,
      int targetCycles,
      int starvationCycleThreshold,
      double starvationRelativeFraction,
      int progressPollIntervalMs,
      int noProgressPollLimit) {
    this.numPhilosophers = numPhilosophers;
    this.targetCycles = targetCycles;
    this.starvationCycleThreshold = starvationCycleThreshold;
    this.starvationRelativeFraction = starvationRelativeFraction;
    this.progressPollIntervalMs = progressPollIntervalMs;
    this.noProgressPollLimit = noProgressPollLimit;
  }
}
