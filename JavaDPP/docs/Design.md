# Design Document — Dining Philosophers CLI Application

## Overview

A Java CLI application that demonstrates four classic solutions to the Dining Philosophers Problem. The user selects a solution strategy, the number of philosophers, and a target cycle count. A full cycle is one complete pass through `THINKING → HUNGRY → EATING → THINKING`. The simulation ends when every philosopher has completed the target number of cycles, or when a monitor fires early (starvation, livelock, or deadlock). At completion, per-philosopher timing breakdowns are printed. **Fairness is measured by per-philosopher eating time**: a fair solution distributes chopstick access evenly, so philosophers should spend approximately equal total time eating. This is quantified with Jain's Fairness Index applied to `totalEatingMs` across all philosophers. Wall-clock runtime is reported as context but is not itself a fairness measure.

---

## Package Structure

```
src/
├── Main.java                          # Entry point — delegates to Controller
├── controller/
│   └── Controller.java                # CLI prompts, wires up and runs simulation
├── model/
│   ├── Philosopher.java               # Philosopher thread + state machine + timing
│   ├── Chopstick.java                 # Shared resource (lock wrapper)
│   ├── PhilosopherState.java          # Enum: THINKING, HUNGRY, EATING
│   └── SimulationStats.java          # Computes and formats end-of-run report
├── solutions/
│   ├── DiningRunner.java              # Interface implemented by all four algorithms
│   ├── ResourceHierarchyAlgorithm.java
│   ├── ArbitratorAlgorithm.java
│   ├── ChandyMisraAlgorithm.java
│   └── MonitorAlgorithm.java
└── monitor/
    ├── StarvationDetector.java        # Detects if a philosopher is falling behind in cycles
    ├── LivelockDetector.java          # Detects active threads with no cycle completions
    └── DeadlockDetector.java          # Detects blocked threads with no cycle completions
```

---

## CLI Flow

```
Welcome to the Dining Philosophers Simulator

Enter number of philosophers (minimum 2): 5
Enter number of cycles per philosopher: 20
Select solution:
  1. Resource Hierarchy (Dijkstra)
  2. Arbitrator / Waiter
  3. Chandy/Misra (Message Passing)
  4. Monitor / Condition Variables
Choice: 4

Running Monitor / Condition Variables — 5 philosophers, 20 cycles each...

[  cy 1] P0: THINKING | P1: EATING   | P2: THINKING | P3: EATING   | P4: THINKING
[  cy 1] P0: HUNGRY   | P1: EATING   | P2: THINKING | P3: EATING   | P4: THINKING
...
[  cy 20] P0: THINKING | P1: THINKING | P2: THINKING | P3: THINKING | P4: THINKING

All philosophers completed 20 cycles.

=== Simulation Report ===
Total wall-clock runtime: 4.821s

Philosopher  Cycles  Avg Cycle   Eating       Hungry       Thinking
P0           20      241ms       982ms (20%)   873ms (18%)  2966ms (62%)
P1           20      238ms       964ms (20%)   901ms (19%)  2936ms (61%)
P2           20      244ms       991ms (21%)   887ms (18%)  2943ms (61%)
P3           20      240ms       978ms (20%)   856ms (18%)  2987ms (62%)
P4           20      237ms       955ms (20%)   862ms (18%)  3004ms (62%)

Fairness Index (Jain's, on eating time): 0.9994
```

### Inputs

| Prompt | Type | Constraints |
|---|---|---|
| Number of philosophers | `int` | ≥ 2 |
| Cycles per philosopher | `int` | > 0 |
| Solution type | `int` | 1–4 |

---

## Component Design

![Class Diagram](../drawio/images/class-diagram.png)

*Source: [drawio/class-diagram.drawio](../drawio/class-diagram.drawio)*

### `DiningRunner` (interface)

All four algorithm classes implement this interface. `Controller` holds a reference to this type only.

```java
public interface DiningRunner {
    void initialize(int numPhilosophers, RunnerConfig config);
    void start();
    void stop(String reason);         // idempotent; first caller wins
    List<Philosopher> getPhilosophers();
}
```

### `RunnerConfig`

```java
public class RunnerConfig {
    int numPhilosophers;
    int targetCycles;                     // cycles each philosopher must complete
    int starvationCycleThreshold;         // default: 10 — absolute floor on the cycle-gap before
                                          //   starvation fires
    double starvationRelativeFraction;    // default: 0.20 — fraction of leader cycles used as
                                          //   the relative threshold; effective threshold =
                                          //   max(starvationCycleThreshold, floor(leader × fraction))
    int progressPollIntervalMs;           // default: 200ms — polling rate for all monitors
    int noProgressPollLimit;              // default: 30 — consecutive polls with no new cycle
                                          //   completions before livelock/deadlock fires
}
```

**Why the threshold is adaptive:** Using `max(absoluteFloor, floor(leaderCycles × fraction))` keeps detection meaningful across all scales. The absolute floor (10) catches real starvation early in a run before the relative term grows; the relative fraction (0.20) prevents false positives at large N, where random [100 ms, 500 ms] think/eat variance can temporarily produce cycle-count gaps that exceed any small fixed threshold even in starvation-free algorithms.

### `Philosopher` (Thread)

Each philosopher is a `Thread` with an internal state machine and per-state timing accumulators.

```
THINKING → HUNGRY → (waiting for chopsticks) → EATING → THINKING
```

```java
public class Philosopher extends Thread {
    private final int id;
    private final int targetCycles;

    // State (readable by monitors)
    private volatile PhilosopherState state;
    private final AtomicInteger cyclesCompleted = new AtomicInteger(0); // incremented when EATING → THINKING

    // Per-state timing accumulators (ms), written by owning thread only
    private volatile long totalThinkingMs;
    private volatile long totalHungryMs;
    private volatile long totalEatingMs;

    // Timestamp of last state entry (used to compute durations on transition)
    private long stateEnteredAt;
}
```

On each state transition the philosopher records `System.currentTimeMillis() - stateEnteredAt` into the appropriate accumulator and resets `stateEnteredAt`. `cyclesCompleted` is incremented on the `EATING → THINKING` transition. All accumulator writes are by the owning thread; reads by monitors are safe via `volatile`.

### `Chopstick`

```java
public class Chopstick {
    private final int id;
    private final ReentrantLock lock = new ReentrantLock();
    // acquire() / release() delegates
}
```

---

## Solution Designs

### 1. `ResourceHierarchyAlgorithm`

- Each chopstick is numbered 0 through N-1.
- Each philosopher always acquires the lower-numbered chopstick before the higher-numbered one.
- Breaks circular-wait, eliminating deadlock.
- Uses `ReentrantLock`; no special primitives.
- Risk: starvation is possible (no fairness guarantee).

```
Philosopher i acquires: min(i, (i+1)%N) first, then max(i, (i+1)%N)
```

![Resource Hierarchy Activity Diagram](../drawio/images/activity-resource-hierarchy.png)

*Source: [drawio/activity-resource-hierarchy.drawio](../drawio/activity-resource-hierarchy.drawio)*

**Citation:** Dijkstra, E. W. (1968). Cooperating sequential processes. In F. Genuys (Ed.), *Programming Languages*. Academic Press, pp. 43–112.

### 2. `ArbitratorAlgorithm`

- A central `Waiter` controls access. A philosopher requests permission before picking up either chopstick; the waiter grants only when both adjacent chopsticks are free.
- Implemented with a `synchronized` method on the waiter; waiting philosophers call `wait()` and the waiter calls `notifyAll()` on each release.
- Eliminates deadlock; reduces parallelism to at most ⌊N/2⌋ eating simultaneously.

![Arbitrator Activity Diagram](../drawio/images/activity-arbitrator.png)

*Source: [drawio/activity-arbitrator.drawio](../drawio/activity-arbitrator.drawio)*

**Citation:** Silberschatz, A., Galvin, P. B., & Gagne, G. (2018). *Operating System Concepts* (10th ed.). Wiley.

### 3. `ChandyMisraAlgorithm`

- Each chopstick is either *clean* or *dirty* and owned by exactly one philosopher.
- Initially all chopsticks are dirty, held by the lower-numbered neighbor.
- A philosopher wanting a held chopstick sends a request via `LinkedBlockingQueue`.
- A holder with a dirty requested chopstick cleans and sends it; a holder with a clean chopstick defers until after eating.
- All coordination is through message passing — no shared locks on chopstick objects.
- Guarantees freedom from both deadlock and starvation.

```
Per adjacent pair (i, j where i < j):
  LinkedBlockingQueue<Message> channel_i_to_j
  LinkedBlockingQueue<Message> channel_j_to_i
```

![Chandy/Misra Activity Diagram](../drawio/images/activity-chandy-misra.png)

*Source: [drawio/activity-chandy-misra.drawio](../drawio/activity-chandy-misra.drawio)*

**Citation:** Chandy, K. M., & Misra, J. (1984). The drinking philosophers problem. *ACM Transactions on Programming Languages and Systems*, 6(4), 632–646. https://doi.org/10.1145/1780.1804

### 4. `MonitorAlgorithm`

- Global `state[]` array tracks `THINKING`, `HUNGRY`, or `EATING` for each philosopher.
- A philosopher can only enter `EATING` when both neighbors are not `EATING`.
- Single `ReentrantLock` + one `Condition` per philosopher; chopstick release signals both neighbors' conditions.

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition[] self = new Condition[N];
private final PhilosopherState[] state = new PhilosopherState[N];
```

![Monitor Activity Diagram](../drawio/images/activity-monitor.png)

*Source: [drawio/activity-monitor.drawio](../drawio/activity-monitor.drawio)*

**Citation:** Hoare, C. A. R. (1974). Monitors: An operating system structuring concept. *Communications of the ACM*, 17(10), 549–557. https://doi.org/10.1145/355620.361161

---

## Monitoring

All three monitors run as daemon threads polling at `progressPollIntervalMs`. Each holds a reference to `DiningRunner` and calls `runner.stop(reason)` on detection. `stop()` is idempotent via `AtomicBoolean` — the first monitor to fire wins.

### `StarvationDetector`

Starvation is defined as one philosopher falling too far behind the current leader in cycle completions. Detection is cycle-count-relative and independent of wall-clock speed.

- At each poll, compute `maxCycles = max(philosopher.getCyclesCompleted())`.
- Compute `threshold = max(starvationCycleThreshold, floor(maxCycles × starvationRelativeFraction))`.
- If `maxCycles - philosopher[i].getCyclesCompleted() > threshold` for any `i`, starvation is declared.

The adaptive threshold prevents false positives at large N: at small cycle counts the absolute floor (10) dominates; once the leader advances, the relative term (20% of leader cycles) takes over, scaling the allowed gap with run progress so that random timing variance in starvation-free algorithms does not trigger premature termination.

```
[ERROR] Starvation detected: P2 is 25 cycles behind the leader (P0: 120, P2: 95). Terminating.
```

### `LivelockDetector`

**Why snapshot comparison is insufficient:** If philosophers cycle rapidly through `THINKING → HUNGRY → THINKING` between polls, consecutive state snapshots will look different — the detector would never fire. The accurate signal is not "did state change?" but "did any philosopher complete a cycle?"

- Maintain a rolling count of `totalCycles = sum of philosopher.getCyclesCompleted()`.
- If `totalCycles` is unchanged for `noProgressPollLimit` consecutive polls *and* at least one philosopher thread is `RUNNABLE`, that confirms the system is active but not progressing → livelock.

```
[ERROR] Livelock detected: 0 cycles completed across 30 consecutive polls with active threads. Terminating.
```

### `DeadlockDetector`

Uses the same progress metric as `LivelockDetector` but checks for blocked/waiting threads rather than active ones.

**Mechanism 1 — `ThreadMXBean.findDeadlockedThreads()`**

- Inspects JVM lock state for circular waits on `synchronized` monitors and `ReentrantLock` ownable synchronizers.
- Fires immediately and accurately for `ResourceHierarchyAlgorithm`, `ArbitratorAlgorithm`, and `MonitorAlgorithm`.
- Does **not** cover `ChandyMisraAlgorithm` — threads waiting on `BlockingQueue.take()` are in `WAITING` state, not blocked on a lock, so the JVM does not recognize them as deadlocked.

**Mechanism 2 — Progress stagnation + thread-state check**

- If `totalCycles` is unchanged for `noProgressPollLimit` consecutive polls *and* all philosopher threads are in `BLOCKED` or `WAITING` state (none `RUNNABLE`), the system has stalled with no active threads → deadlock.
- Catches Solution 3 and acts as a general fallback.

```
[ERROR] Deadlock detected (lock cycle): threads P0, P2, P4 in circular wait. Terminating.
[ERROR] Deadlock detected (progress stall): 0 cycles in 30 polls, all threads blocked. Terminating.
```

### Livelock vs. Deadlock Discrimination

Both share the symptom of zero cycle completions over `noProgressPollLimit` polls. Thread state is the discriminator:

| Condition | Cycles stagnant for N polls | Any thread `RUNNABLE`? | `findDeadlockedThreads()` fires? |
|---|---|---|---|
| Normal operation | No | Yes | No |
| Livelock | Yes | Yes | No |
| Deadlock (lock-based) | Yes | No | Yes |
| Deadlock (queue-based) | Yes | No | No |

---

## Termination and Reporting

**Normal termination:** The main thread waits on all philosopher threads (via `join()`). Each philosopher's `run()` loop exits naturally when `cyclesCompleted == targetCycles`. The main thread then asks `SimulationStats` to print the report.

**Monitor-triggered termination:** Any monitor calls `runner.stop(reason)`, which sets `stopped` via `AtomicBoolean.compareAndSet(false, true)`, interrupts all philosopher threads to unblock waiting threads, then joins them. The interrupted termination reason is printed before the stats report.

### `SimulationStats`

Computed from accumulated per-philosopher state timing and cycle counts after all threads have joined:

- **Cycles completed** — should equal `targetCycles` for all philosophers in a clean run.
- **Average cycle time** — `totalRuntimeMs / cyclesCompleted` per philosopher.
- **Time in each state** — `totalEatingMs`, `totalHungryMs`, `totalThinkingMs` (ms and % of total).
- **Fairness Index** — Jain's Fairness Index applied to `totalEatingMs` across all philosophers (see §Fairness Metric for full details).

```
F = (Σ eatingTime_i)²  /  (N × Σ eatingTime_i²)
```

F = 1.0 indicates perfectly equal eating time; F approaches 1/N as one philosopher monopolizes the chopsticks.

---

## Fairness Metric

### Why eating time is the right variable

A fair chopstick-allocation algorithm gives each philosopher an equal share of access to shared resources. Philosophers have identical roles and equal claim on the chopsticks, so in a fair system each should accumulate approximately the same total time eating across the simulation. Think-time and hungry-wait-time are informational but are not fairness measures: think-time is self-imposed, and hungry-wait-time is an indirect consequence of allocation (captured more cleanly by eating time). Per-philosopher `totalEatingMs` is therefore the primary fairness variable.

### Jain's Fairness Index

Jain's Fairness Index (JFI) is a well-established, dimensionless scalar in [1/N, 1] that measures how equitably a resource is shared among N users [1][2]:

```
F(x₁, x₂, …, xₙ) = (Σᵢ xᵢ)²  /  (N × Σᵢ xᵢ²)
```

Applied here, each `xᵢ = totalEatingMs` for philosopher `i`.

**Properties relevant to this application:**

| Property | Meaning |
|---|---|
| F = 1.0 | All philosophers ate for exactly the same duration — perfectly fair |
| F = 1/N | One philosopher received all eating time — completely unfair |
| Scale-independent | Doubling every `xᵢ` leaves F unchanged; fast vs. slow solutions are comparable |
| Population-independent | F can be compared across runs with different N |
| Sensitive to outliers | A single starving philosopher pulls F sharply toward 1/N, making starvation visible even when other philosophers are well-balanced |

**Interpretation guidance for a 5-philosopher run:**

| F range | Interpretation |
|---|---|
| 0.99 – 1.00 | Excellent fairness |
| 0.95 – 0.99 | Good; minor imbalance |
| 0.90 – 0.95 | Moderate imbalance; one or more philosophers notably disadvantaged |
| < 0.90 | Poor fairness; starvation likely present or imminent |

Because F ≥ 1/N = 0.20 for N=5, a value near 0.20 would indicate near-total starvation of all but one philosopher.

### Comparison across solutions

Running each solution with the same N and `targetCycles` and comparing F directly is valid because JFI is scale-independent — differences in raw runtime between solutions do not bias the score.

### References

[1] Jain, R., Chiu, D. M., & Hawe, W. R. (1984). *A Quantitative Measure of Fairness and Discrimination for Resource Allocation in Shared Computer Systems* (DEC Technical Report TR-301). Digital Equipment Corporation Eastern Research Laboratory. https://www.cse.wustl.edu/~jain/papers/ftp/fairness.pdf

[2] Jain, R. (1991). *The Art of Computer Systems Performance Analysis: Techniques for Experimental Design, Measurement, Simulation, and Modeling*. Wiley-Interscience. (Chapter 33, "Comparing Alternatives.")

---

## Output Format

A status line is printed whenever any philosopher changes state. The cycle counter in the prefix reflects the minimum `cyclesCompleted` across all philosophers (the "slowest" philosopher's progress):

```
[  cy 7] P0: THINKING | P1: EATING   | P2: HUNGRY   | P3: THINKING | P4: EATING
```

State labels are padded to fixed width so columns align across lines.

---

## Thread Safety Summary

| Component | Mechanism |
|---|---|
| `Philosopher.state` | `volatile`, written only by owning thread |
| `Philosopher.cyclesCompleted` | `AtomicInteger`, incremented via `incrementAndGet()` on EATING → THINKING |
| `Philosopher.totalEatingMs` etc. | `volatile long`, written only by owning thread |
| `Chopstick` locking | `ReentrantLock` (solutions 1, 2, 4) or `BlockingQueue` messages (solution 3) |
| `MonitorAlgorithm` state array | guarded by single `ReentrantLock` |
| `Waiter` in `ArbitratorAlgorithm` | `synchronized` method |
| `stopped` flag | `AtomicBoolean` in each algorithm |
| `runner.stop()` | idempotent via `AtomicBoolean`; first caller wins |

---

## Assumptions and Constraints

- Think and eat durations are randomly sampled from configurable ranges (defaults: 100–500ms each).
- Minimum philosophers: 2. The classic problem uses 5; all solutions generalize to N.
- All thresholds are configurable but have sensible defaults.
- The four algorithms are self-contained — they share `Philosopher`, `Chopstick`, and `RunnerConfig` but do not share implementation logic.
- The `LivelockDetector` and `DeadlockDetector` share the `totalCycles` progress metric but run as separate daemon threads to keep each concern isolated and independently tunable.
