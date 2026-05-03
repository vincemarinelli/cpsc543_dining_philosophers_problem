package test.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import model.Chopstick;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Chopstick}. */
public class ChopstickTest {

  @Test
  void idIsRetained() {
    assertEquals(7, new Chopstick(7).getId());
  }

  @Test
  void initiallyNotHeld() {
    assertFalse(new Chopstick(0).isHeld());
  }

  @Test
  void acquireAndRelease() throws InterruptedException {
    Chopstick c = new Chopstick(0);
    c.acquire();
    assertTrue(c.isHeld());
    c.release();
    assertFalse(c.isHeld());
  }

  @Test
  void secondAcquireBlocksUntilReleased() throws InterruptedException {
    Chopstick c = new Chopstick(0);
    c.acquire();

    Thread contender = new Thread(() -> {
      try {
        c.acquire();
        c.release();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    contender.setDaemon(true);
    contender.start();

    Thread.sleep(50);
    assertTrue(contender.isAlive()); // still blocked
    c.release();
    contender.join(2000);
    assertFalse(contender.isAlive());
  }

  @Test
  void acquireIsInterruptible() throws InterruptedException {
    Chopstick c = new Chopstick(0);
    c.acquire();

    Thread t = new Thread(() -> {
      try {
        c.acquire();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    t.setDaemon(true);
    t.start();
    Thread.sleep(30);
    t.interrupt();
    t.join(2000);
    assertFalse(t.isAlive());
    c.release();
  }
}
