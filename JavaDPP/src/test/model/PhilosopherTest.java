package test.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import model.Philosopher;
import model.PhilosopherState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for {@link Philosopher}. */
public class PhilosopherTest {

  @Test
  void initialState() {
    Philosopher p = new Philosopher(2, 5);
    assertEquals(2, p.getPhilosopherId());
    assertEquals(5, p.getTargetCycles());
    assertEquals(PhilosopherState.THINKING, p.getPhilosopherState());
    assertEquals(0, p.getCyclesCompleted());
    assertFalse(p.isDone());
    assertEquals(0L, p.getTotalThinkingMs());
    assertEquals(0L, p.getTotalHungryMs());
    assertEquals(0L, p.getTotalEatingMs());
  }

  @Test
  void transitionAccumulatesCyclesOnEatingToThinking() {
    Philosopher p = new Philosopher(0, 3);

    p.transitionTo(PhilosopherState.HUNGRY);
    assertEquals(0, p.getCyclesCompleted());

    p.transitionTo(PhilosopherState.EATING);
    assertEquals(0, p.getCyclesCompleted());

    p.transitionTo(PhilosopherState.THINKING); // cycle 1 complete
    assertEquals(1, p.getCyclesCompleted());
    assertFalse(p.isDone());

    p.transitionTo(PhilosopherState.HUNGRY);
    p.transitionTo(PhilosopherState.EATING);
    p.transitionTo(PhilosopherState.THINKING); // cycle 2
    assertEquals(2, p.getCyclesCompleted());

    p.transitionTo(PhilosopherState.HUNGRY);
    p.transitionTo(PhilosopherState.EATING);
    p.transitionTo(PhilosopherState.THINKING); // cycle 3
    assertEquals(3, p.getCyclesCompleted());
    assertTrue(p.isDone());
  }

  @Test
  void timingAccumulatorsAreNonNegative() throws InterruptedException {
    Philosopher p = new Philosopher(0, 1);
    p.transitionTo(PhilosopherState.HUNGRY);
    assertTrue(p.getTotalThinkingMs() >= 0);

    p.transitionTo(PhilosopherState.EATING);
    assertTrue(p.getTotalHungryMs() >= 0);

    Thread.sleep(20);
    p.transitionTo(PhilosopherState.THINKING);
    assertTrue(p.getTotalEatingMs() >= 0);
    assertTrue(p.getTotalThinkingMs() >= 0);
  }

  @Test
  void randomSleepMsWithinBounds() {
    for (int i = 0; i < 30; i++) {
      int v = Philosopher.randomSleepMs();
      assertTrue(v >= Philosopher.MIN_SLEEP_MS, "below MIN: " + v);
      assertTrue(v <= Philosopher.MAX_SLEEP_MS, "above MAX: " + v);
    }
  }

  @Test
  @Timeout(5)
  void baseRunIsNoOp() throws InterruptedException {
    Philosopher p = new Philosopher(0, 1);
    p.start();
    p.join(3000);
    assertFalse(p.isAlive());
  }

  @Test
  void stateTransitionUpdatesState() {
    Philosopher p = new Philosopher(0, 5);
    p.transitionTo(PhilosopherState.HUNGRY);
    assertEquals(PhilosopherState.HUNGRY, p.getPhilosopherState());
    p.transitionTo(PhilosopherState.EATING);
    assertEquals(PhilosopherState.EATING, p.getPhilosopherState());
  }
}
