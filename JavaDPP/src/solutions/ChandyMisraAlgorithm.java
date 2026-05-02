package solutions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import model.Philosopher;
import model.PhilosopherState;
import model.RunnerConfig;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;

/**
 * Deadlock- and starvation-free dining philosophers via the Chandy/Misra message-passing protocol.
 *
 * <p>Each philosopher has a {@link LinkedBlockingQueue} inbox. Fork ownership is tracked per-thread
 * with dirty/clean state; no shared lock objects are used.
 */
@ThreadSafe
public class ChandyMisraAlgorithm implements DiningRunner {

  private List<Philosopher> philosophers;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private enum MsgType { REQUEST, FORK }

  @Immutable
  private static class Message {

    final MsgType type;
    final int senderId; // neighbor who sent this

    Message(MsgType type, int senderId) {
      this.type = type;
      this.senderId = senderId;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void initialize(int numPhilosophers, RunnerConfig config) {
    stopped.set(false);
    int n = numPhilosophers;

    // One inbox per philosopher
    LinkedBlockingQueue<Message>[] inboxes = new LinkedBlockingQueue[n];
    for (int i = 0; i < n; i++) {
      inboxes[i] = new LinkedBlockingQueue<>();
    }

    philosophers = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      final int id = i;
      final int leftId = (i - 1 + n) % n;
      final int rightId = (i + 1) % n;
      final LinkedBlockingQueue<Message> myInbox = inboxes[i];
      final LinkedBlockingQueue<Message> leftInbox = inboxes[leftId];
      final LinkedBlockingQueue<Message> rightInbox = inboxes[rightId];

      // Lower-numbered philosopher holds the fork between each adjacent pair initially (dirty)
      final boolean[] holdsLeft = {id < leftId};
      final boolean[] holdsRight = {id < rightId};
      final boolean[] leftDirty = {holdsLeft[0]};
      final boolean[] rightDirty = {holdsRight[0]};
      final boolean[] deferLeft = {false};
      final boolean[] deferRight = {false};

      Philosopher p = new Philosopher(id, config.targetCycles) {
        @Override
        public void run() {
          try {
            while (!isDone() && !stopped.get()) {
              transitionTo(PhilosopherState.THINKING);
              sleepProcessing(Philosopher.randomSleepMs());

              if (stopped.get()) {
                break;
              }
              transitionTo(PhilosopherState.HUNGRY);

              if (!holdsLeft[0]) {
                leftInbox.put(new Message(MsgType.REQUEST, id));
              }
              if (!holdsRight[0]) {
                rightInbox.put(new Message(MsgType.REQUEST, id));
              }

              // Wait until holding both forks
              while ((!holdsLeft[0] || !holdsRight[0]) && !stopped.get()) {
                Message msg = myInbox.poll(50, TimeUnit.MILLISECONDS);
                if (msg != null) {
                  processMessage(msg);
                }
              }

              if (stopped.get()) {
                break;
              }

              transitionTo(PhilosopherState.EATING);
              sleepProcessing(Philosopher.randomSleepMs());

              // Mark forks dirty after eating
              if (holdsLeft[0]) {
                leftDirty[0] = true;
              }
              if (holdsRight[0]) {
                rightDirty[0] = true;
              }

              // Drain inbox before sending deferred forks
              drainInbox();

              if (deferLeft[0]) {
                holdsLeft[0] = false;
                leftDirty[0] = false;
                deferLeft[0] = false;
                leftInbox.put(new Message(MsgType.FORK, id));
              }
              if (deferRight[0]) {
                holdsRight[0] = false;
                rightDirty[0] = false;
                deferRight[0] = false;
                rightInbox.put(new Message(MsgType.FORK, id));
              }
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            // Proactively release all dirty forks so neighbors don't stall
            // waiting for a REQUEST response we'll never process after exit.
            releaseHeldForks();
          }
        }

        private void releaseHeldForks() {
          try {
            drainInbox();
            if (holdsLeft[0] && leftDirty[0]) {
              holdsLeft[0] = false;
              leftDirty[0] = false;
              deferLeft[0] = false;
              leftInbox.put(new Message(MsgType.FORK, id));
            } else if (deferLeft[0]) {
              holdsLeft[0] = false;
              deferLeft[0] = false;
              leftInbox.put(new Message(MsgType.FORK, id));
            }
            if (holdsRight[0] && rightDirty[0]) {
              holdsRight[0] = false;
              rightDirty[0] = false;
              deferRight[0] = false;
              rightInbox.put(new Message(MsgType.FORK, id));
            } else if (deferRight[0]) {
              holdsRight[0] = false;
              deferRight[0] = false;
              rightInbox.put(new Message(MsgType.FORK, id));
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }

        private void processMessage(Message msg) throws InterruptedException {
          boolean isLeft = (msg.senderId == leftId);
          if (msg.type == MsgType.FORK) {
            if (isLeft) {
              holdsLeft[0] = true;
            } else {
              holdsRight[0] = true;
            }
          } else { // REQUEST
            boolean holds = isLeft ? holdsLeft[0] : holdsRight[0];
            boolean dirty = isLeft ? leftDirty[0] : rightDirty[0];
            if (!holds) {
              return; // we don't have it; ignore
            }
            PhilosopherState cur = getPhilosopherState();
            if (dirty && cur == PhilosopherState.THINKING) {
              // Give it up now
              if (isLeft) {
                holdsLeft[0] = false;
                leftDirty[0] = false;
                leftInbox.put(new Message(MsgType.FORK, id));
              } else {
                holdsRight[0] = false;
                rightDirty[0] = false;
                rightInbox.put(new Message(MsgType.FORK, id));
              }
            } else {
              // Defer until after eating
              if (isLeft) {
                deferLeft[0] = true;
              } else {
                deferRight[0] = true;
              }
            }
          }
        }

        private void sleepProcessing(int totalMs) throws InterruptedException {
          long deadline = System.currentTimeMillis() + totalMs;
          while (System.currentTimeMillis() < deadline && !stopped.get()) {
            long remaining = deadline - System.currentTimeMillis();
            long wait = Math.min(remaining, 50);
            if (wait <= 0) {
              break;
            }
            Message msg = myInbox.poll(wait, TimeUnit.MILLISECONDS);
            if (msg != null) {
              processMessage(msg);
            }
          }
        }

        private void drainInbox() throws InterruptedException {
          Message msg;
          while ((msg = myInbox.poll()) != null) {
            processMessage(msg);
          }
        }
      };
      philosophers.add(p);
    }
  }

  @Override
  public void start() {
    for (Philosopher p : philosophers) {
      p.start();
    }
  }

  @Override
  public void stop(String reason) {
    if (stopped.compareAndSet(false, true)) {
      for (Philosopher p : philosophers) {
        p.interrupt();
      }
    }
  }

  @Override
  public List<Philosopher> getPhilosophers() {
    return Collections.unmodifiableList(philosophers);
  }
}
