package cn.toside.music.mobile.boot;

/** One live boot job; cancellation invalidates its generation without admitting overlapping jobs. */
public final class BootTaskState {
  private int generation;
  private int active;
  private long lastStart = -1;
  public static final long DUPLICATE_WINDOW_MS = 90_000L;

  public synchronized int begin(long elapsedMs) {
    if (active != 0) return 0;
    if (lastStart >= 0 && elapsedMs - lastStart < DUPLICATE_WINDOW_MS) return 0;
    lastStart = elapsedMs;
    active = ++generation;
    return active;
  }

  public synchronized boolean isCurrent(int token) {
    return token != 0 && token == active && token == generation;
  }

  public synchronized void cancel() {
    ++generation;
  }

  public synchronized void finish(int token) {
    if (active == token) active = 0;
  }
}
