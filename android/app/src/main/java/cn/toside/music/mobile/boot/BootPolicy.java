package cn.toside.music.mobile.boot;

/**
 * 开机自启的判定。纯函数，方便在没有车机的情况下把组合钉住。
 *
 * 失败表现全是「开机后什么都没发生」，而它只取决于广播 action、开关、
 * 以及「这次开机界面有没有真正到过前台」。
 *
 * 行为对齐 AnyLyric 的 BootPolicy：标准开机广播和车机 QUICKBOOT 才算开机；
 * 开关关闭则什么都不做。USER_PRESENT 不是开机本身，只在开机后的短窗口里
 * 给「待机页把第一次拉起丢掉」补一次（AnyLyric 在车机上遇到的情况）。
 */
public final class BootPolicy {

  /** 与 {@code Intent.ACTION_BOOT_COMPLETED} 同值。不引用 Android 类，单元测试可直接编译。 */
  public static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

  /** 车机与部分盒子的快速开机广播，语义与 BOOT_COMPLETED 相同。 */
  public static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

  /** 与 {@code Intent.ACTION_USER_PRESENT} 同值。 */
  public static final String ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT";

  /**
   * 亮屏补拉只认开机后这段时间。再晚的亮屏是用户正常使用，不该把应用抢回前台。
   * 与 AnyLyric 的 PermissionWake.USER_PRESENT_WINDOW_MS 相同。
   */
  public static final long USER_PRESENT_WINDOW_MS = 10L * 60L * 1000L;

  /** 用墙钟反推本次开机时刻时允许的误差。 */
  public static final long BOOT_ANCHOR_SKEW_MS = 5_000L;

  private BootPolicy() {}

  /** 开机广播且开关打开时，拉起应用。其它广播一律忽略。 */
  public static boolean shouldStart(String action, boolean enabled) {
    if (!enabled || action == null) return false;
    return ACTION_BOOT_COMPLETED.equals(action) || ACTION_QUICKBOOT_POWERON.equals(action);
  }

  /**
   * 开机后的亮屏补拉。
   *
   * 车机开机时常先停在待机页，BOOT_COMPLETED 里 startActivity 会被系统丢掉，
   * 界面其实没出现。亮屏这一下再试一次。窗口之外，或这次开机已经到过前台，则不再试。
   */
  public static boolean shouldRetryOnUserPresent(
      boolean enabled, long uptimeMs, long nowWallMs, long lastForegroundWallMs) {
    if (!enabled) return false;
    if (uptimeMs < 0L || uptimeMs > USER_PRESENT_WINDOW_MS) return false;
    return !wasForegroundThisBoot(uptimeMs, nowWallMs, lastForegroundWallMs);
  }

  /**
   * 本次开机界面是否已经到过前台。
   *
   * {@code elapsedRealtime} 每次开机从 0 计，所以开机墙钟约等于 nowWall - uptime。
   * 上次前台时间早于这次开机，说明还没在本次开机里出现过。
   */
  public static boolean wasForegroundThisBoot(
      long uptimeMs, long nowWallMs, long lastForegroundWallMs) {
    if (lastForegroundWallMs <= 0L) return false;
    long uptime = uptimeMs < 0L ? 0L : uptimeMs;
    long bootAnchor = nowWallMs - uptime;
    return lastForegroundWallMs >= bootAnchor - BOOT_ANCHOR_SKEW_MS;
  }
}
