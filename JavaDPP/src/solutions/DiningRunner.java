package solutions;

import java.util.List;
import model.Philosopher;
import model.RunnerConfig;
import net.jcip.annotations.ThreadSafe;

/** Contract for all dining-philosophers concurrency algorithms. */
@ThreadSafe
public interface DiningRunner {

  /** Initialises internal state; must be called before {@link #start()}. */
  void initialize(int numPhilosophers, RunnerConfig config);

  /** Starts all philosopher threads. */
  void start();

  /** Signals all threads to stop; idempotent. */
  void stop(String reason);

  /** Returns an unmodifiable view of the philosopher threads. */
  List<Philosopher> getPhilosophers();
}
