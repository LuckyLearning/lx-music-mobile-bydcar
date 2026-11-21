package cn.toside.music.mobile.boot;

/**
 * 开机自启的判定。纯函数，方便在没有车机的情况下把组合钉住。
 *
 * 0.0.9 在开关打开时拉起主界面。Android 10 起后台 {@code startActivity} 会被系统静默丢掉
 * （不抛异常），USER_PRESENT 也不在豁免名单里，迪链 5.0 又常常没有锁屏，补拉等于没做。
 * 听风在同一台车上的后台自启不打开界面：开机广播里 {@code startService} 拉播放服务。
 * 本应用对应的也是拉播放服务，而不是再试一次主界面。出不出声由 JS 看「启动后自动播放」。
 */
public final class BootPolicy {

  /** 与 {@code Intent.ACTION_BOOT_COMPLETED} 同值。不引用 Android 类，单元测试可直接编译。 */
  public static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

  /** 车机与部分盒子的快速开机广播，语义与 BOOT_COMPLETED 相同。听风只听这一条厂商广播。 */
  public static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

  /** 与 {@code Intent.ACTION_USER_PRESENT} 同值。0.0.10 不再用它补拉界面。 */
  public static final String ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT";

  /**
   * 0.0.9 亮屏补拉窗口。升级后若接收器还开着，用它把闹钟取消掉。
   * 新的开机路径不再武装这个窗口。
   */
  public static final long USER_PRESENT_WINDOW_MS = 10L * 60L * 1000L;

  public enum Kind {
    /** 什么都不做 */
    NONE,
    /** 只拉播放服务，不打开主界面。出不出声由 JS 决定。 */
    PLAY_IN_BACKGROUND,
  }

  private BootPolicy() {}

  public static boolean isBootAction(String action) {
    return ACTION_BOOT_COMPLETED.equals(action) || ACTION_QUICKBOOT_POWERON.equals(action);
  }

  /**
   * 开关打开且是开机广播 → 后台拉起播放服务。其它情况什么都不做。
   * 没有「打开主界面」这一档：那条路在这台车机上到不了前台。
   */
  public static Kind decide(String action, boolean enabled) {
    if (!enabled || !isBootAction(action)) return Kind.NONE;
    return Kind.PLAY_IN_BACKGROUND;
  }
}
