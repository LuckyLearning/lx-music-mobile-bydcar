package cn.toside.music.mobile.boot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 开机链路的失败表现全是「开机后什么都没发生」，开发机上复现不了开机，靠这里把组合钉住。
 */
public class BootPolicyTest {

  private static final long WINDOW = BootPolicy.USER_PRESENT_WINDOW_MS;

  @Test
  public void standardBootStartsWhenEnabled() {
    assertTrue(BootPolicy.shouldStart(BootPolicy.ACTION_BOOT_COMPLETED, true));
  }

  @Test
  public void quickBootStartsWhenEnabled() {
    assertTrue(BootPolicy.shouldStart(BootPolicy.ACTION_QUICKBOOT_POWERON, true));
  }

  @Test
  public void switchOffDoesNothing() {
    assertFalse(BootPolicy.shouldStart(BootPolicy.ACTION_BOOT_COMPLETED, false));
    assertFalse(BootPolicy.shouldStart(BootPolicy.ACTION_QUICKBOOT_POWERON, false));
  }

  @Test
  public void otherBroadcastsAndNullAreIgnored() {
    assertFalse(BootPolicy.shouldStart(null, true));
    assertFalse(BootPolicy.shouldStart("", true));
    assertFalse(BootPolicy.shouldStart(BootPolicy.ACTION_USER_PRESENT, true));
    assertFalse(BootPolicy.shouldStart("android.intent.action.MY_PACKAGE_REPLACED", true));
  }

  @Test
  public void userPresentRetriesOnlyInsideTheBootWindow() {
    assertTrue(BootPolicy.shouldRetryOnUserPresent(true, 0L, 1_000_000L, 0L));
    assertTrue(BootPolicy.shouldRetryOnUserPresent(true, WINDOW, 1_000_000L, 0L));
    assertFalse(BootPolicy.shouldRetryOnUserPresent(true, WINDOW + 1L, 1_000_000L, 0L));
    assertFalse(BootPolicy.shouldRetryOnUserPresent(true, -1L, 1_000_000L, 0L));
  }

  @Test
  public void userPresentRespectsTheSwitch() {
    assertFalse(BootPolicy.shouldRetryOnUserPresent(false, 10_000L, 1_000_000L, 0L));
  }

  @Test
  public void userPresentDoesNotStealFocusAfterTheAppWasShown() {
    long uptime = 30_000L;
    long now = 1_080_000L;
    long bootAnchor = now - uptime;
    assertFalse(BootPolicy.shouldRetryOnUserPresent(true, uptime, now, bootAnchor));
    assertFalse(BootPolicy.shouldRetryOnUserPresent(true, uptime, now, now));
  }

  @Test
  public void previousBootDoesNotCountAsShownThisBoot() {
    long uptime = 20_000L;
    long now = 2_000_000L;
    long previousForeground = 1_000_000L;
    assertTrue(BootPolicy.shouldRetryOnUserPresent(true, uptime, now, previousForeground));
    assertFalse(BootPolicy.wasForegroundThisBoot(uptime, now, previousForeground));
  }

  @Test
  public void foregroundInsideSkewStillCounts() {
    long uptime = 10_000L;
    long now = 500_000L;
    long bootAnchor = now - uptime;
    assertTrue(BootPolicy.wasForegroundThisBoot(
        uptime, now, bootAnchor - BootPolicy.BOOT_ANCHOR_SKEW_MS));
    assertFalse(BootPolicy.wasForegroundThisBoot(
        uptime, now, bootAnchor - BootPolicy.BOOT_ANCHOR_SKEW_MS - 1L));
    assertFalse(BootPolicy.wasForegroundThisBoot(uptime, now, 0L));
  }
}
