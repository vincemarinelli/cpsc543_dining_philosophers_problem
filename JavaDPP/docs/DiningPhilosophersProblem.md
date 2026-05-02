# Analysis of the Dining Philosophers Problem in Java

This presentation will explore the Dining Philosophers Problem and its various approaches to solving the synchronization and deadlock issues. We will discuss the classic solution using semaphores and mutexes, as well as more advanced techniques like the dining philosophers algorithm and the use of monitors. The focus will be on understanding the challenges of concurrent programming and the trade-offs between different synchronization mechanisms. Additionally, we will analyze the implementation of these solutions in Java, highlighting best practices and potential optimizations.

## The Dining Philosophers Problem
The Dining Philosophers Problem is one of the most iconic concurrency problems in computer science, originally introduced by Edsger Dijkstra in 1965 [3] as an exam problem relating to computers competing for access to shared resources. The problem, as now known, was reformuated by Tony Hoare to illustrate the challenges of resource allocation and synchronization in concurrent systems.

### The Problem As Written By Hoare

The text below is a copy of the original problem statement from the book **Communicating Sequential Processes** by C.A.R. Hoare[1].

#### _2.5 Example: The Dining Philosophers_
_In ancient times, a wealthy philanthropist endowed a College to accommodate five eminent philosophers. Each philosopher had a room in which he could engage in his professional activity of thinking; there was also a common dining room, furnished with a circular table, surrounded by five chairs, each labelled by the name of the philosopher who was to sit in it. The names of the philosophers were PHIL0, PHIL1, PHIL2, PHIL3, PHIL4, and they were disposed in this order anticlockwise around the table. To the left of each philosopher there was laid a golden fork, and in the centre stood a large bowl of spaghetti, which was
constantly replenished. A philosopher was expected to spend most of his time thinking; but when he felt hungry, he went to the dining room, sat down in his own chair, picked up his own fork on his left, and plunged it into the spaghetti. But such is the tangled nature of spaghetti that a second fork is required to carry it to the mouth. The philosopher therefore had also to pick up the fork on his right. When we was finished he would put down both his forks, get up from his chair, and continue thinking. Of course, a fork can be used by only one philosopher at a time.  If the other philosopher wants it, he just has to wait until the fork is available again._

### Brief Problem Statement [2]
Five philosophers sit around a circular table. Between each pair of adjacent philosophers lies a single chopstick (five total). Each philosopher progresses cyclically through the following states:

- **Thinking** — requires no resources
- **Eating** — requires both the left and right chopstick simultaneously

#### Philosopher state machine:

![Philosopher State Machine](../drawio/images/philosopher-state-machine.drawio.png)

*Source: [drawio/philosopher-state-machine.drawio](../drawio/philosopher-state-machine.drawio)*

A philosopher can only pick up one chopstick at a time, and cannot use a chopstick already held by a neighbor.

The task is to devise an algorithm for allocating resources (chopsticks) among the philosophers with the following properties:

- prevents deadlocks
- prevents starvation (literally in this case...)

### Core Concurrency Hazards Illustrated By This Problem
1. **Deadlock**<br>
   If every philosopher simultaneously picks up their left chopstick, none can pick up their right — they wait forever. This is the classic circular-wait deadlock.
2. **Starvation**<br>
   Even without deadlock, a philosopher may never get to eat if neighbors repeatedly acquire the chopsticks first.
3. **Livelock**<br>
   Philosophers can repeatedly pick up and put down chopsticks in lockstep, making no progress despite being active.
4. **Race Conditions**<br>
   Without proper synchronization, two philosophers might both believe they've acquired the same chopstick.

### Solution Strategies Examined Here
There are a number of approaches described in the literature, and there is a significant amount of variation in how each solution is implemented. In order to constrain scope and stay focused on solutions that utilize Java concurrency toolsets, we will examine these four common approaches:
1. Resource Hierarchy (Dijkstra's Original) [3]
   Number the chopsticks 1–5. Each philosopher always picks up the lower-numbered chopstick first. This breaks the circular-wait condition, eliminating deadlock. It's simple but can cause starvation.
1. Arbitrator / Waiter Pattern [2]
   A central "waiter" controls access — a philosopher must ask permission before picking up chopsticks. The waiter only grants permission if both are available. This prevents deadlock but introduces a bottleneck and reduces parallelism.
1. Chandy/Misra (Message Passing) [4]
   Chopsticks are passed between philosophers via requests and replies, with a "dirty/clean" token to ensure fairness. Eliminates both deadlock and starvation but is complex to implement.
1. Monitor / Condition Variable Approach [5]
   Each philosopher's state (THINKING, HUNGRY, EATING) is tracked. A philosopher only picks up chopsticks when both neighbors are not eating, enforced via a monitor and condition variables. This is the most natural fit for Java's concurrency model.

## How These Techniques Can Be Applied Using Java

Java provides several mechanisms that map directly onto these solutions:

| **Solution** | **Java Mechanisms** |
|--------------------------------|--------------------------------|
| Resource Hierarchy | `synchronized`, `ReentrantLock` (ordered acquisition) |
| Arbitrator / Waiter | `Semaphore`, centralized `synchronized` monitor |
| Monitor / Condition Variables | `synchronized` + `wait()`/`notifyAll()`, `ReentrantLock` + `Condition` |
| Chandy/Misra Message Passing | `BlockingQueue`, actor-style threading |

### All Four Solutions in Java

1. **Resource Hierarchy**<br>
   Straightforward to implement in Java with nothing more than synchronized blocks or ReentrantLock. Each chopstick is a shared object, and philosophers simply acquire locks in a globally consistent numeric order. No special concurrency primitives are required — the deadlock prevention is entirely in the logic, not the mechanism.
1. **Arbitrator / Waiter**<br>
   Naturally implemented with a Semaphore(1) acting as the waiter's permission token, or simply a synchronized method on a central Waiter object that checks availability before granting access.
1. **Monitor / Condition Variables**<br>
   The most idiomatic Java approach. Uses synchronized + wait()/notifyAll(), or more explicitly, ReentrantLock with named Condition objects (one per philosopher), allowing targeted wake-ups rather than broadcasting to all threads.
1. **Chandy/Misra (Message Passing)**<br>
   Fully implementable in Java using BlockingQueue (e.g., LinkedBlockingQueue) between each pair of philosophers to pass chopstick request and reply messages. Each philosopher runs its own thread and communicates purely through queues — no shared lock on the chopstick objects themselves. Java's java.util.concurrent package makes this quite clean.

## Solution Designs

### 1. `ResourceHierarchyAlgorithm` [3]

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

### 2. `ArbitratorAlgorithm` [2]

- A central `Waiter` controls access. A philosopher requests permission before picking up either chopstick; the waiter grants only when both adjacent chopsticks are free.
- Implemented with a `synchronized` method on the waiter; waiting philosophers call `wait()` and the waiter calls `notifyAll()` on each release.
- Eliminates deadlock; reduces parallelism to at most ⌊N/2⌋ eating simultaneously.

![Arbitrator Activity Diagram](../drawio/images/activity-arbitrator.png)

*Source: [drawio/activity-arbitrator.drawio](../drawio/activity-arbitrator.drawio)*

### 3. `ChandyMisraAlgorithm` [4]

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

### 4. `MonitorAlgorithm` [5]

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

## Live Demo

## Comparison of Solutions

### Theoretical Properties

All four algorithms guarantee freedom from deadlock, but by different mechanisms and with different fairness guarantees.

**Resource Hierarchy** [3] prevents deadlock by breaking the circular-wait condition: globally consistent lock ordering ensures no cycle of dependencies can form. However, it provides no fairness guarantee — a fortunate subset of neighbors can repeatedly acquire chopsticks first, indefinitely blocking another philosopher.

**Arbitrator** [2] prevents deadlock by ensuring no philosopher ever holds one chopstick while waiting for the other: both are granted atomically or not at all. Like Resource Hierarchy, it uses `notifyAll()` on each release, creating a thundering-herd race with no ordering guarantee, so starvation remains possible.

**Chandy/Misra** [4] guarantees both deadlock freedom and starvation freedom through its dirty/clean protocol. Once a philosopher has sent a `REQUEST`, the dirty-fork rule ensures the holder will eventually clean and send it — no request is permanently deferred.

**Monitor** [5] guarantees both deadlock freedom and starvation freedom through targeted per-philosopher `Condition` signals. `putDown(i)` calls `test()` on both neighbors, waking only those who are now eligible to eat, avoiding the thundering-herd broadcast.

| Property | Resource Hierarchy [3] | Arbitrator [2] | Chandy/Misra [4] | Monitor [5] |
|---|:---:|:---:|:---:|:---:|
| Deadlock-free | ✓ | ✓ | ✓ | ✓ |
| Starvation-free | ✗ | ✗ | ✓ | ✓ |
| Coordination mechanism | Ordered lock acquisition | Central waiter | Dirty/clean message passing | Shared state + per-philosopher conditions |
| Max simultaneous eaters (N=5) | 2 | 2 | 2 | 2 |
| Lock on chopstick objects | ✓ | ✗ | ✗ | ✗ |

### Simulation Results (N=5 Philosophers, 30 Cycles Each)

All four algorithms completed all 30 cycles without triggering starvation, livelock, or deadlock detection. Think and eat durations were sampled uniformly from [100 ms, 500 ms].

| Algorithm | Wall-clock | Avg cycle | JFI (eating time) | Avg hungry wait |
|---|---|---|---|---|
| Resource Hierarchy [3] | 27.858 s | 833 ms | 0.9918 | 28% |
| Arbitrator [2] | 24.486 s | 782 ms | 0.9927 | 26% |
| Chandy/Misra [4] | 31.561 s | 1019 ms | **0.9972** | 38% |
| Monitor [5] | 26.240 s | 827 ms | 0.9965 | 26% |

#### Resource Hierarchy

Resource Hierarchy produced the widest spread in per-philosopher hungry-wait time: P3 spent only 13% of its time waiting for chopsticks while P4 spent 39%. This reflects the known fairness limitation of ordered acquisition [3] — breaking circular wait does not distribute access equitably. Eating time ranged from 7,694 ms to 9,945 ms across philosophers, a spread of ~2,250 ms. The Jain's Fairness Index of 0.9918 was the lowest of all four algorithms, consistent with theoretical prediction.

#### Arbitrator

The Arbitrator was the fastest algorithm at 24.486 s wall clock. Atomically granting both chopsticks eliminates the two-step acquisition delay present in Resource Hierarchy. The tradeoff is that a single `synchronized` method serializes all requests: every philosopher contends on the same monitor, which constrains scalability as N grows [2]. Average hungry-wait was 26%, and the JFI of 0.9927 was moderate.

#### Chandy/Misra

Chandy/Misra had the highest JFI (0.9972) and the smallest eating-time range across philosophers (8,681 ms to 10,063 ms — a spread of only ~1,382 ms), empirically confirming the protocol's starvation-freedom guarantee [4]. The cost is coordination overhead: average cycle time was 1,019 ms (the longest), and 38% of thread time was spent in the HUNGRY state. This overhead comes from the 50 ms inbox-polling interval used while THINKING and EATING — messages arrive during those phases but are only checked every 50 ms, adding queuing latency before fork requests are processed.

#### Monitor

Monitor achieved the second-best JFI (0.9965) with the lowest average hungry-wait percentage (26%), offering the best balance of fairness and throughput. The targeted `self[i].signal()` in `test()` wakes only the philosopher that just became eligible, avoiding the `notifyAll()` thundering-herd cost present in the Arbitrator [5]. This makes it the most idiomatic and efficient Java implementation.

### Jain's Fairness Index Interpretation

All four algorithms scored above 0.99, indicating excellent fairness across all 30-cycle runs. Two caveats apply:

1. **Short runs may not reveal starvation.** The starvation detector's cycle-gap threshold is 10 (default), so a philosopher 10+ cycles behind triggers early termination. With only 30 target cycles, a consistently unlucky philosopher would be caught. Longer runs (100+ cycles) would better surface starvation susceptibility in Resource Hierarchy and Arbitrator.

2. **Moderate contention limits divergence.** With think and eat times sampled from [100 ms, 500 ms], mean think time (~300 ms) provides natural breathing room between acquisition attempts. Reducing think time (e.g., [0 ms, 50 ms]) would sharpen chopstick contention and cause JFI to diverge more visibly between starvation-susceptible and starvation-free algorithms.

### Summary

| Criterion | Best | Worst |
|---|---|---|
| Wall-clock throughput | Arbitrator (24.5 s) | Chandy/Misra (31.6 s) |
| Fairness (JFI) | Chandy/Misra (0.9972) | Resource Hierarchy (0.9918) |
| Hungry-wait overhead | Monitor / Arbitrator (26%) | Chandy/Misra (38%) |
| Starvation-freedom | Chandy/Misra, Monitor | Resource Hierarchy, Arbitrator |
| Implementation complexity | Resource Hierarchy (simplest) | Chandy/Misra (most complex) |

For Java applications, **Monitor / Condition Variables** provides the best practical balance: starvation-free, low hungry-wait overhead, and idiomatic use of `ReentrantLock` with named `Condition` objects. **Chandy/Misra** offers the strongest fairness guarantees and is the only lock-free design, but incurs measurable coordination overhead. **Resource Hierarchy** remains the simplest to implement and reason about when starvation is acceptable or unlikely in practice [3].

## Citations
1. Hoare, C. A. R. (1978). Communicating sequential processes. *Communications of the ACM*, 21(8), 666–677. https://doi.org/10.1145/359576.359585
1. Silberschatz, A., Galvin, P. B., & Gagne, G. (2018). *Operating System Concepts* (10th ed.). Wiley.
1. Dijkstra, E. W. (1968). Cooperating sequential processes. In F. Genuys (Ed.), *Programming Languages*. Academic Press, pp. 43–112.
1. Chandy, K. M., & Misra, J. (1984). The drinking philosophers problem. *ACM Transactions on Programming Languages and Systems*, 6(4), 632–646. https://doi.org/10.1145/1780.1804
1. Hoare, C. A. R. (1974). Monitors: An operating system structuring concept. *Communications of the ACM*, 17(10), 549–557. https://doi.org/10.1145/355620.361161

