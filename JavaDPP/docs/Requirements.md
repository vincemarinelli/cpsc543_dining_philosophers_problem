# Requirements — Dining Philosophers CLI Simulator

## 1. Purpose

This document specifies the functional and non-functional requirements for a Java CLI application that simulates the Dining Philosophers Problem [3]. The application demonstrates four classic concurrency solutions, monitors for hazard conditions at runtime, and reports per-philosopher timing statistics with a fairness metric. It is intended as an educational tool for illustrating how different synchronization strategies address the core hazards of concurrent resource allocation: deadlock, starvation, livelock, and race conditions [3].

---

## 2. Glossary

| Term | Definition |
|---|---|
| Philosopher | A concurrent thread that repeatedly cycles through THINKING, HUNGRY, and EATING states |
| Chopstick | A shared exclusive resource; a philosopher requires both adjacent chopsticks to eat |
| Cycle | One complete THINKING → HUNGRY → EATING → THINKING sequence per philosopher |
| Deadlock | Every philosopher holds one chopstick and waits indefinitely for the other; no progress is possible [3] |
| Starvation | A philosopher never succeeds in eating while others continue cycling [3] |
| Livelock | Philosophers remain active (threads RUNNABLE) but no cycles are completed [2] |
| Race Condition | Two philosophers simultaneously believe they hold the same chopstick due to absent synchronization |
| Fairness | Equality of chopstick access, quantified by Jain's Fairness Index applied to total eating time |

---

## 3. Functional Requirements

### 3.1 Problem Model

**FR-1** The application shall simulate N philosophers (N ≥ 2) seated around a circular table with exactly N chopsticks, one placed between each pair of adjacent philosophers.

**FR-2** Each philosopher shall run as a dedicated `Thread`. The number of philosopher threads shall equal the configured number of philosophers.

**FR-3** Each chopstick shall model an exclusive shared resource. A chopstick acquired by one philosopher shall not be simultaneously acquirable by any other philosopher.

**FR-4** A philosopher shall only eat when it holds both its left chopstick (index `i`) and its right chopstick (index `(i + 1) % N`) simultaneously.

**FR-5** A philosopher shall acquire at most one chopstick at a time (two-phase acquisition is permitted; simultaneous atomic acquisition of both is not required for lock-based algorithms).

### 3.2 Philosopher State Machine

**FR-6** Each philosopher shall progress through exactly three states in order:

```
THINKING → HUNGRY → EATING → THINKING → …
```

**FR-7** From `THINKING`, a philosopher shall sleep for a random duration uniformly drawn from [100 ms, 500 ms] before transitioning to `HUNGRY`.

**FR-8** From `HUNGRY`, a philosopher shall wait to acquire both adjacent chopsticks. No sleep is added in this state; wait duration reflects chopstick availability.

**FR-9** From `EATING`, a philosopher shall sleep for a random duration uniformly drawn from [100 ms, 500 ms], then release both chopsticks and transition to `THINKING`.

**FR-10** A *cycle* is completed upon the `EATING → THINKING` transition. The simulation ends when every philosopher has completed `targetCycles` cycles, or a monitor terminates it early.

**FR-11** Per-philosopher timing accumulators (`totalThinkingMs`, `totalHungryMs`, `totalEatingMs`) shall be updated on every state transition by computing `System.currentTimeMillis() − stateEnteredAt` and adding it to the appropriate accumulator.

**FR-12** `cyclesCompleted` shall be incremented atomically (via `AtomicInteger.incrementAndGet()`) on each `EATING → THINKING` transition.

### 3.3 Algorithm Implementations

**FR-13** The application shall provide four algorithm implementations, each implementing the common `DiningRunner` interface (`initialize`, `start`, `stop`, `getPhilosophers`).

**FR-14 — Resource Hierarchy Algorithm [3]:** Each philosopher shall always acquire the lower-indexed of its two adjacent chopsticks before the higher-indexed one:

```
first  = chopstick[min(i, (i+1) % N)]
second = chopstick[max(i, (i+1) % N)]
```

This ordering breaks the circular-wait condition and guarantees deadlock freedom. Starvation is not prevented.

**FR-15 — Arbitrator Algorithm [2]:** A single central `Waiter` object shall serialize all chopstick grants. A philosopher shall submit a `requestChopsticks(left, right)` call; the waiter shall block (via `wait()`) until both adjacent chopsticks are simultaneously free, then mark them in-use before returning. On finishing eating, a philosopher calls `releaseChopsticks(left, right)`, which frees both and calls `notifyAll()`. At most ⌊N/2⌋ philosophers shall eat simultaneously. Deadlock is prevented; starvation is not.

**FR-16 — Chandy/Misra Algorithm [4]:** Chopstick ownership shall be tracked per-philosopher thread using dirty/clean state flags. Each philosopher shall have one `LinkedBlockingQueue<Message>` inbox. The following rules shall hold:

- Initially, the lower-indexed philosopher of each adjacent pair holds its shared chopstick, marked dirty.
- A philosopher that does not hold a needed chopstick shall send a `REQUEST` message to the neighbor's inbox.
- A philosopher that receives a `REQUEST` for a dirty chopstick it holds and is not eating shall immediately clean and send it via a `FORK` message.
- A philosopher that holds a clean chopstick or is eating defers the transfer until after eating.
- After eating, a philosopher marks held chopsticks dirty and sends any deferred `FORK` messages.
- While THINKING or EATING, a philosopher shall poll its inbox every 50 ms to process pending messages.

This protocol guarantees freedom from both deadlock and starvation [4].

**FR-17 — Monitor Algorithm [5]:** A single `ReentrantLock` shall protect a shared `state[]` array (`THINKING` / `HUNGRY` / `EATING` per philosopher) and one `Condition` per philosopher. The following rules shall hold:

- `pickUp(i)`: sets `state[i] = HUNGRY`, calls `test(i)`, and awaits `self[i]` if still not `EATING`.
- `test(i)`: if `state[i] == HUNGRY` and neither neighbor is `EATING`, sets `state[i] = EATING` and signals `self[i]`.
- `putDown(i)`: sets `state[i] = THINKING` and calls `test` on both neighbors, potentially waking them.

This guarantees freedom from both deadlock and starvation [5].

**FR-18** All four algorithms shall implement idempotent `stop(String reason)` using `AtomicBoolean.compareAndSet(false, true)`. The first caller sets the flag and interrupts all philosopher threads; subsequent calls are no-ops.

**FR-19** `Chopstick.acquire()` shall use `ReentrantLock.lockInterruptibly()` so that a philosopher blocked waiting on a lock can be unblocked by `Thread.interrupt()` when `stop()` is called.

### 3.4 CLI Interface

**FR-20** The application shall prompt the user for three inputs in sequence:

| Prompt | Valid range |
|---|---|
| Number of philosophers | ≥ 2 |
| Cycles per philosopher | ≥ 1 |
| Algorithm choice | 1 – 4 |

Invalid input shall be rejected with an error message and the prompt repeated.

**FR-21** Before starting, the application shall print a summary line:

```
Running <algorithm name> — <N> philosophers, <cycles> cycles each...
```

**FR-22** A status line shall be printed to stdout on every philosopher state change. The format shall be:

```
[cy   N] P0: <STATE>   | P1: <STATE>   | ...
```

where `N` is the minimum `cyclesCompleted` across all philosophers, state labels are padded to fixed width, and all philosopher columns are separated by ` | `.

**FR-23** On simulation completion (normal or early termination), the application shall print a simulation report containing: total wall-clock runtime, and per-philosopher cycles, average cycle time, and time in each state (ms and percentage of total thread time).

### 3.5 Monitor Daemons

**FR-24** Three monitor daemons shall run as daemon threads, each polling at `progressPollIntervalMs` (default 200 ms). All three hold a `DiningRunner` reference and call `runner.stop(reason)` on detection.

**FR-25 — Starvation Detector:** At each poll interval, compute `maxCycles = max(philosopher.cyclesCompleted)` across all philosophers. Starvation is declared when any philosopher's cycle count falls more than `starvationCycleThreshold` (default 10) behind `maxCycles`. Detection is cycle-count-relative, independent of wall-clock speed.

**FR-26 — Livelock Detector:** Maintain a rolling sum of all `cyclesCompleted`. Livelock is declared when this sum has not increased for `noProgressPollLimit` (default 30) consecutive polls *and* at least one philosopher thread is in the `RUNNABLE` state.

**FR-27 — Deadlock Detector:** Deadlock shall be detected by two mechanisms, applied at each poll interval:

1. **JVM lock-cycle detection:** `ThreadMXBean.findDeadlockedThreads()` detects circular waits on `synchronized` monitors and `ReentrantLock` ownable synchronizers. This covers algorithms 1, 2, and 4.
2. **Progress stagnation with all threads blocked:** If `Σ cyclesCompleted` stagnates for `noProgressPollLimit` consecutive polls *and* every non-terminated philosopher thread is in `BLOCKED`, `WAITING`, or `TIMED_WAITING` state, deadlock is declared. This covers algorithm 3 (Chandy/Misra), where queue-blocking is invisible to the JVM deadlock detector.

**FR-28** Livelock and deadlock are discriminated by thread state: livelock requires at least one `RUNNABLE` thread; deadlock requires all non-terminated philosopher threads to be blocked or waiting.

### 3.6 Simulation Reporting

**FR-29** The simulation report shall include:

- Total wall-clock runtime in seconds.
- Per philosopher: cycles completed, average cycle time (ms), total eating time (ms, %), total hungry time (ms, %), and total thinking time (ms, %).
- Jain's Fairness Index computed over `totalEatingMs` across all philosophers.

**FR-30** Jain's Fairness Index shall be computed as:

```
F(x₁, …, xₙ) = (Σᵢ xᵢ)² / (N × Σᵢ xᵢ²)
```

where `xᵢ = totalEatingMs` for philosopher `i`. F = 1.0 is perfectly fair; F = 1/N is completely unfair. For N = 5, F ≥ 0.99 is considered excellent.

---

## 4. Non-Functional Requirements

### 4.1 Thread Safety

**NFR-1** All state shared between philosopher threads and monitor daemon threads shall use explicit synchronization. No unsynchronized mutable shared field is permitted.

**NFR-2** `Philosopher.state` and per-philosopher timing accumulators (`totalThinkingMs`, `totalHungryMs`, `totalEatingMs`) shall be declared `volatile`. They are written only by the owning philosopher thread; `volatile` provides safe visibility to monitor threads without locking.

**NFR-3** `Philosopher.cyclesCompleted` shall be an `AtomicInteger` to support concurrent reads by monitor threads and increments by the owning thread.

**NFR-4** The `Waiter` in `ArbitratorAlgorithm` and the `state[]` array in `MonitorAlgorithm` shall be guarded by a lock (documented with `@GuardedBy`).

**NFR-5** All algorithm state that is accessed by multiple threads shall be annotated with `@ThreadSafe` or `@GuardedBy` from the JCIP annotations library.

### 4.2 Termination

**NFR-6** The main thread shall join all philosopher threads (via `Thread.join()`) before printing the simulation report. The report shall reflect final, stable state only.

**NFR-7** The simulation shall terminate cleanly under all four conditions: normal cycle completion, starvation detection, livelock detection, and deadlock detection.

**NFR-8** `runner.stop()` shall be idempotent. Concurrent calls from multiple monitors shall not produce duplicate interrupts or duplicate output. The first caller wins via `AtomicBoolean.compareAndSet`.

### 4.3 Implementation Constraints

**NFR-9** The application shall be implemented in Java 17.

**NFR-10** Synchronization shall use only primitives from the Java standard library (`java.util.concurrent`, `synchronized`, `wait()`, `notify()`, `notifyAll()`). No third-party concurrency frameworks are permitted.

**NFR-11** All four algorithm classes shall be interchangeable through the `DiningRunner` interface. The `Controller` shall hold only a `DiningRunner` reference; it shall have no knowledge of algorithm-specific types.

**NFR-12** All four algorithms shall generalize to any N ≥ 2 philosophers; no algorithm shall hard-code N = 5.

---

## 5. Algorithm Correctness Properties

| Property | Resource Hierarchy [3] | Arbitrator [2] | Chandy/Misra [4] | Monitor [5] |
|---|:---:|:---:|:---:|:---:|
| Deadlock-free | ✓ | ✓ | ✓ | ✓ |
| Starvation-free | ✗ | ✗ | ✓ | ✓ |
| Max simultaneous eaters (N=5) | 2 | 2 | 2 | 2 |
| Chopstick lock required | ✓ | ✗ | ✗ | ✗ |
| Centralized coordination | ✗ | ✓ | ✗ | Partial |
| Message passing | ✗ | ✗ | ✓ | ✗ |

---

## 6. References

[1] Hoare, C. A. R. (1978). Communicating sequential processes. *Communications of the ACM*, 21(8), 666–677. https://doi.org/10.1145/359576.359585

[2] Silberschatz, A., Galvin, P. B., & Gagne, G. (2018). *Operating System Concepts* (10th ed.). Wiley.

[3] Dijkstra, E. W. (1968). Cooperating sequential processes. In F. Genuys (Ed.), *Programming Languages*. Academic Press, pp. 43–112.

[4] Chandy, K. M., & Misra, J. (1984). The drinking philosophers problem. *ACM Transactions on Programming Languages and Systems*, 6(4), 632–646. https://doi.org/10.1145/1780.1804

[5] Hoare, C. A. R. (1974). Monitors: An operating system structuring concept. *Communications of the ACM*, 17(10), 549–557. https://doi.org/10.1145/355620.361161
