package controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import model.Philosopher;
import model.PhilosopherState;
import model.RunnerConfig;
import model.SimulationStats;
import monitor.DeadlockDetector;
import monitor.LivelockDetector;
import monitor.StarvationDetector;
import net.jcip.annotations.NotThreadSafe;
import solutions.ArbitratorAlgorithm;
import solutions.ChandyMisraAlgorithm;
import solutions.DiningRunner;
import solutions.MonitorAlgorithm;
import solutions.ResourceHierarchyAlgorithm;

/**
 * CLI entry point: prompts the user for configuration, wires up the chosen algorithm with three
 * monitor daemons, and prints a live status loop until all philosophers finish.
 */
@NotThreadSafe
public class Controller {

  private static final String[] ALGO_NAMES = {
    "Resource Hierarchy (Dijkstra)",
    "Arbitrator / Waiter",
    "Chandy/Misra (Message Passing)",
    "Monitor / Condition Variables"
  };

  /** Runs the interactive simulation from start to finish. */
  public void run() {
    Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
    System.out.println("Welcome to the Dining Philosophers Simulator");
    System.out.println();

    int numPhilosophers =
        promptInt(sc, "Enter number of philosophers (minimum 2): ", 2, Integer.MAX_VALUE);
    int targetCycles =
        promptInt(sc, "Enter number of cycles per philosopher: ", 1, Integer.MAX_VALUE);

    System.out.println("Select solution:");
    for (int i = 0; i < ALGO_NAMES.length; i++) {
      System.out.printf("  %d. %s%n", i + 1, ALGO_NAMES[i]);
    }
    int choice = promptInt(sc, "Choice: ", 1, 4);

    RunnerConfig config = new RunnerConfig(numPhilosophers, targetCycles);
    DiningRunner runner = createRunner(choice);
    runner.initialize(numPhilosophers, config);

    System.out.printf(
        "%nRunning %s — %d philosophers, %d cycles each...%n%n",
        ALGO_NAMES[choice - 1], numPhilosophers, targetCycles);

    // Start status printer daemon
    Thread statusThread = new Thread(() -> printStatusLoop(runner), "StatusPrinter");
    statusThread.setDaemon(true);

    // Start monitor daemons
    final Thread starvation =
        daemonThread(new StarvationDetector(runner, config), "StarvationDetector");
    final Thread livelock =
        daemonThread(new LivelockDetector(runner, config), "LivelockDetector");
    final Thread deadlock =
        daemonThread(new DeadlockDetector(runner, config), "DeadlockDetector");
    final long startMs = System.currentTimeMillis();

    runner.start();
    statusThread.start();
    starvation.start();
    livelock.start();
    deadlock.start();

    // Wait for all philosopher threads to finish
    for (Philosopher p : runner.getPhilosophers()) {
      try {
        p.join();
      } catch (InterruptedException ignored) {
        // Joining is best-effort; interrupted flag is irrelevant post-simulation
      }
    }

    final long wallClockMs = System.currentTimeMillis() - startMs;

    // Stop monitors
    starvation.interrupt();
    livelock.interrupt();
    deadlock.interrupt();
    statusThread.interrupt();

    // Check if all philosophers finished normally
    boolean allDone = runner.getPhilosophers().stream()
        .allMatch(p -> p.getCyclesCompleted() >= targetCycles);
    if (allDone) {
      System.out.printf("All philosophers completed %d cycles.%n", targetCycles);
    }

    SimulationStats.print(runner.getPhilosophers(), wallClockMs);
  }

  private void printStatusLoop(DiningRunner runner) {
    List<Philosopher> philosophers = runner.getPhilosophers();
    PhilosopherState[] lastStates = new PhilosopherState[philosophers.size()];
    try {
      while (!Thread.currentThread().isInterrupted()) {
        boolean changed = false;
        PhilosopherState[] current = new PhilosopherState[philosophers.size()];
        for (int i = 0; i < philosophers.size(); i++) {
          current[i] = philosophers.get(i).getPhilosopherState();
          if (current[i] != lastStates[i]) {
            changed = true;
          }
        }
        if (changed) {
          int minCycles = philosophers.stream()
              .mapToInt(Philosopher::getCyclesCompleted)
              .min().orElse(0);
          StringBuilder sb = new StringBuilder();
          sb.append(String.format("[cy %3d] ", minCycles));
          for (int i = 0; i < philosophers.size(); i++) {
            if (i > 0) {
              sb.append(" | ");
            }
            sb.append(String.format("P%d: %-8s", i, current[i]));
          }
          System.out.println(sb);
          System.arraycopy(current, 0, lastStates, 0, current.length);
        }
        Thread.sleep(20);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private DiningRunner createRunner(int choice) {
    switch (choice) {
      case 1:
        return new ResourceHierarchyAlgorithm();
      case 2:
        return new ArbitratorAlgorithm();
      case 3:
        return new ChandyMisraAlgorithm();
      case 4:
        return new MonitorAlgorithm();
      default:
        throw new IllegalArgumentException("Invalid choice: " + choice);
    }
  }

  private Thread daemonThread(Runnable r, String name) {
    Thread t = new Thread(r, name);
    t.setDaemon(true);
    return t;
  }

  private int promptInt(Scanner sc, String prompt, int min, int max) {
    while (true) {
      System.out.print(prompt);
      try {
        int val = Integer.parseInt(sc.nextLine().trim());
        if (val >= min && val <= max) {
          return val;
        }
        System.out.printf("Please enter a value between %d and %d.%n", min, max);
      } catch (NumberFormatException e) {
        System.out.println("Invalid input. Please enter an integer.");
      }
    }
  }
}
