package test.monitor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import model.Philosopher;
import model.RunnerConfig;
import solutions.DiningRunner;

/** Test double for {@link DiningRunner} used by monitor unit tests. */
class FakeRunner implements DiningRunner {

  private final List<Philosopher> philosophers;
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final AtomicReference<String> stopReason = new AtomicReference<>();

  FakeRunner(List<Philosopher> philosophers) {
    this.philosophers = philosophers;
  }

  @Override
  public void initialize(int n, RunnerConfig c) {
    // no-op test double
  }

  @Override
  public void start() {
    // no-op test double
  }

  @Override
  public void stop(String reason) {
    stopped.compareAndSet(false, true);
    stopReason.compareAndSet(null, reason);
  }

  @Override
  public List<Philosopher> getPhilosophers() {
    return philosophers;
  }

  boolean isStopped() {
    return stopped.get();
  }

  String getStopReason() {
    return stopReason.get();
  }
}
