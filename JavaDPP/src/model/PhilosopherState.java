package model;

import net.jcip.annotations.Immutable;

/** Lifecycle states for a dining philosopher. */
@Immutable
public enum PhilosopherState {
  THINKING, HUNGRY, EATING
}
