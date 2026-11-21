package cn.toside.music.mobile.boot;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 开机自启开关。默认关闭，与 AnyLyric 的 {@code bootAutostart} 一致。
 *
 * 存在独立的 SharedPreferences 里：开机广播到达时 JS 还没起来，读不到应用内设置。
 * JS 在启动和用户拨动开关时把同一值写进来。
 */
public final class BootPrefs {

  static final String PREFS_NAME = "lx_boot";
  static final String KEY_ENABLED = "boot_autostart";
  static final String KEY_FOREGROUND_WALL = "last_foreground_wall";

  /** 本次进程已经记过前台时间，避免每次 onResume 都写盘。 */
  private static volatile boolean foregroundMarked;

  private BootPrefs() {}

  public static boolean isEnabled(Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, false);
  }

  public static void setEnabled(Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
  }

  public static long lastForegroundWall(Context context) {
    return prefs(context).getLong(KEY_FOREGROUND_WALL, 0L);
  }

  /** 界面到前台时记一笔。同一进程只写一次。 */
  public static void markForegroundOnce(Context context) {
    if (foregroundMarked) return;
    foregroundMarked = true;
    boolean ok = prefs(context).edit()
        .putLong(KEY_FOREGROUND_WALL, System.currentTimeMillis())
        .commit();
    if (!ok) foregroundMarked = false;
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }
}
