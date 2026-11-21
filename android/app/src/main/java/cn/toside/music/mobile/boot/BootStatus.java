package cn.toside.music.mobile.boot;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 开机自启的落盘证据。
 *
 * 这条链路的失败全是「开机后什么都没发生」。广播到了先写一行，
 * 起播有结论再补结果。设置页把它念出来，用来区分「广播没到」和「到了但没播起来」。
 * 与开关 {@code lx_boot} 分开存，关开关不会抹掉上次的排障记录。
 */
public final class BootStatus {

  static final String PREFS_NAME = "lx_boot_status";
  static final String KEY_AT = "at";
  static final String KEY_ACTION = "action";
  static final String KEY_OK = "ok";
  static final String KEY_REASON = "reason";

  public static final String ACTION_AUTOPLAY = "autoplay";
  public static final String ACTION_NONE = "none";

  public static final class Snapshot {
    public final long atMillis;
    public final String action;
    /** {@code null} 表示广播到了、结果还没写。进程中途被杀掉时会留下这个空档。 */
    public final Boolean ok;
    public final String reason;

    Snapshot(long atMillis, String action, Boolean ok, String reason) {
      this.atMillis = atMillis;
      this.action = action == null ? ACTION_NONE : action;
      this.ok = ok;
      this.reason = reason == null ? "" : reason;
    }
  }

  private BootStatus() {}

  /** 用 commit：接下来就要拉服务，进程可能马上被收走，apply 会把这行丢掉。 */
  public static void markReceived(Context context, String action, String reason) {
    prefs(context).edit()
        .putLong(KEY_AT, System.currentTimeMillis())
        .putString(KEY_ACTION, action)
        .remove(KEY_OK)
        .putString(KEY_REASON, reason == null ? "" : reason)
        .commit();
  }

  public static void markResult(Context context, boolean ok, String reason) {
    prefs(context).edit()
        .putBoolean(KEY_OK, ok)
        .putString(KEY_REASON, reason == null ? "" : reason)
        .commit();
  }

  public static Snapshot snapshot(Context context) {
    SharedPreferences prefs = prefs(context);
    Boolean ok = prefs.contains(KEY_OK) ? prefs.getBoolean(KEY_OK, false) : null;
    return new Snapshot(
        prefs.getLong(KEY_AT, 0L),
        prefs.getString(KEY_ACTION, ACTION_NONE),
        ok,
        prefs.getString(KEY_REASON, ""));
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }
}
