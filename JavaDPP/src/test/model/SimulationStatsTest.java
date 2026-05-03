package test.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import model.Philosopher;
import model.PhilosopherState;
import model.SimulationStats;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SimulationStats}. */
public class SimulationStatsTest {

  @Test
  void jfiBecomes1WhenAllEatingTimesAreZero() {
    List<Philosopher> ps = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      ps.add(new Philosopher(i, 5));
    }
    assertEquals(1.0, SimulationStats.jainFairnessIndex(ps));
  }

  @Test
  void jfiIsHighForEqualEatingTimes() throws InterruptedException {
    List<Philosopher> ps = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Philosopher p = new Philosopher(i, 1);
      p.transitionTo(PhilosopherState.HUNGRY);
      p.transitionTo(PhilosopherState.EATING);
      Thread.sleep(20);
      p.transitionTo(PhilosopherState.THINKING);
      ps.add(p);
    }
    double jfi = SimulationStats.jainFairnessIndex(ps);
    assertTrue(jfi > 0.9, "expected JFI > 0.9 for equal eating, got " + jfi);
  }

  @Test
  void jfiIsLowerForUnequalEatingTimes() throws InterruptedException {
    final List<Philosopher> ps = new ArrayList<>();
    // first philosopher eats for ~60 ms
    Philosopher heavy = new Philosopher(0, 1);
    heavy.transitionTo(PhilosopherState.HUNGRY);
    heavy.transitionTo(PhilosopherState.EATING);
    Thread.sleep(60);
    heavy.transitionTo(PhilosopherState.THINKING);
    ps.add(heavy);

    // rest eat for ~0 ms
    for (int i = 1; i < 4; i++) {
      Philosopher light = new Philosopher(i, 1);
      light.transitionTo(PhilosopherState.HUNGRY);
      light.transitionTo(PhilosopherState.EATING);
      light.transitionTo(PhilosopherState.THINKING);
      ps.add(light);
    }

    double jfiUnequal = SimulationStats.jainFairnessIndex(ps);
    // compare against perfectly equal case
    List<Philosopher> equal = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Philosopher p = new Philosopher(i, 1);
      p.transitionTo(PhilosopherState.HUNGRY);
      p.transitionTo(PhilosopherState.EATING);
      Thread.sleep(20);
      p.transitionTo(PhilosopherState.THINKING);
      equal.add(p);
    }
    double jfiEqual = SimulationStats.jainFairnessIndex(equal);
    assertTrue(jfiEqual > jfiUnequal,
        "equal JFI=" + jfiEqual + " should exceed unequal JFI=" + jfiUnequal);
  }

  @Test
  void printDoesNotThrow() throws InterruptedException {
    List<Philosopher> ps = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Philosopher p = new Philosopher(i, 2);
      p.transitionTo(PhilosopherState.HUNGRY);
      p.transitionTo(PhilosopherState.EATING);
      Thread.sleep(10);
      p.transitionTo(PhilosopherState.THINKING);
      ps.add(p);
    }
    assertDoesNotThrow(() -> SimulationStats.print(ps, 1234L));
  }

  @Test
  void printHandlesZeroCycles() {
    List<Philosopher> ps = new ArrayList<>();
    ps.add(new Philosopher(0, 1));
    assertDoesNotThrow(() -> SimulationStats.print(ps, 500L));
  }
}
