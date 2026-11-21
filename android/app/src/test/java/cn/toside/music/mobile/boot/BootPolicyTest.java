package cn.toside.music.mobile.boot;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 开机链路的失败表现全是「开机后什么都没发生」，开发机上复现不了开机，靠这里把组合钉住。
 * 开关打开时只能是后台拉起播放服务，不能再回到 0.0.9 的打开主界面。
 */
public class BootPolicyTest {

  @Test
  public void enabledBootPlaysInBackground() {
    assertEquals(
        BootPolicy.Kind.PLAY_IN_BACKGROUND,
        BootPolicy.decide(BootPolicy.ACTION_BOOT_COMPLETED, true));
    assertEquals(
        BootPolicy.Kind.PLAY_IN_BACKGROUND,
        BootPolicy.decide(BootPolicy.ACTION_QUICKBOOT_POWERON, true));
  }

  @Test
  public void disabledBootDoesNothing() {
    assertEquals(BootPolicy.Kind.NONE, BootPolicy.decide(BootPolicy.ACTION_BOOT_COMPLETED, false));
    assertEquals(
        BootPolicy.Kind.NONE, BootPolicy.decide(BootPolicy.ACTION_QUICKBOOT_POWERON, false));
  }

  @Test
  public void userPresentAndOtherBroadcastsDoNotStartPlayback() {
    assertEquals(BootPolicy.Kind.NONE, BootPolicy.decide(null, true));
    assertEquals(BootPolicy.Kind.NONE, BootPolicy.decide("", true));
    assertEquals(BootPolicy.Kind.NONE, BootPolicy.decide(BootPolicy.ACTION_USER_PRESENT, true));
    assertEquals(
        BootPolicy.Kind.NONE,
        BootPolicy.decide("android.intent.action.MY_PACKAGE_REPLACED", true));
  }
}
