package cn.toside.music.mobile.boot;

import org.junit.Test;
import static org.junit.Assert.*;

public class BootTaskStateTest {
  @Test public void bootAndQuickbootShareOneTask() {
    BootTaskState state = new BootTaskState();
    int first = state.begin(100);
    assertTrue(first > 0);
    assertEquals(0, state.begin(101));
    state.finish(first);
    assertEquals(0, state.begin(102)); // second broadcast after a fast idle init
    assertTrue(state.begin(100 + BootTaskState.DUPLICATE_WINDOW_MS) > first);
  }
  @Test public void disableInvalidatesResultAndRetryWithoutOpeningOverlap() {
    BootTaskState state = new BootTaskState();
    int first = state.begin(100);
    assertTrue(state.isCurrent(first));
    state.cancel();
    assertFalse(state.isCurrent(first));
    assertEquals(0, state.begin(100_000));
    state.finish(first);
    int next = state.begin(100_000);
    assertTrue(state.isCurrent(next));
    state.finish(first); // late old completion must not clear new generation
    assertTrue(state.isCurrent(next));
    assertFalse(state.isCurrent(first));
  }
}
