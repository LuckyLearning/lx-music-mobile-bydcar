package cn.toside.music.mobile.boot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 重试节奏与听风一致：0s / 4s / 10s。确认窗口单独加长，给在线取地址用。 */
public class BootRetryPolicyTest {

  @Test
  public void delaysMatchTheCarSchedule() {
    assertEquals(3, BootRetryPolicy.attempts());
    assertEquals(0L, BootRetryPolicy.DELAYS_MS[0]);
    assertEquals(4_000L, BootRetryPolicy.DELAYS_MS[1]);
    assertEquals(10_000L, BootRetryPolicy.DELAYS_MS[2]);
    assertEquals(8_000L, BootRetryPolicy.CONFIRM_MS);
  }

  @Test
  public void waitAndExhaustionFollowTheArray() {
    assertEquals(0L, BootRetryPolicy.waitMs(0));
    assertEquals(4_000L, BootRetryPolicy.waitMs(1));
    assertEquals(10_000L, BootRetryPolicy.waitMs(2));
    assertEquals(10_000L, BootRetryPolicy.waitMs(99));
    assertEquals(0L, BootRetryPolicy.waitMs(-1));
    assertTrue(BootRetryPolicy.hasMore(0));
    assertTrue(BootRetryPolicy.hasMore(1));
    assertFalse(BootRetryPolicy.hasMore(2));
    assertFalse(BootRetryPolicy.isExhausted(0));
    assertTrue(BootRetryPolicy.isExhausted(2));
  }
}
