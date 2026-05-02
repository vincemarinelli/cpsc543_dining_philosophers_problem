package model;

import java.util.concurrent.locks.ReentrantLock;
import net.jcip.annotations.ThreadSafe;

/** A chopstick that philosophers compete for; backed by a {@link ReentrantLock}. */
@ThreadSafe
public class Chopstick {

  private final int id;
  private final ReentrantLock lock = new ReentrantLock();

  /** Creates a chopstick with the given identifier. */
  public Chopstick(int id) {
    this.id = id;
  }

  /** Returns this chopstick's identifier. */
  public int getId() {
    return id;
  }

  /** Acquires the chopstick, blocking interruptibly until it is available. */
  public void acquire() throws InterruptedException {
    lock.lockInterruptibly();
  }

  /** Releases the chopstick. */
  public void release() {
    lock.unlock();
  }

  /** Returns {@code true} if the chopstick is currently held by any thread. */
  public boolean isHeld() {
    return lock.isLocked();
  }
}
