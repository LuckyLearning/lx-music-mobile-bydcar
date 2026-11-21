package cn.toside.music.mobile.boot;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

/**
 * 亮屏补拉的窗口。
 *
 * USER_PRESENT 每次亮屏都会来。接收器平时保持关闭，只在「开机且开关打开、
 * 界面还没到前台」时打开，窗口结束或界面已经出现就关掉。这样平时亮屏不会把进程拉起来。
 */
public final class BootWindow {

  public static final String ACTION_DISARM = "cn.toside.music.mobile.boot.DISARM";

  private static final int REQUEST_DISARM = 1009;

  /** 本进程已经打开过补拉接收器。跨进程以 PackageManager 的状态为准。 */
  private static volatile boolean armed;

  private BootWindow() {}

  public static void arm(Context context) {
    Context app = context.getApplicationContext();
    setUserPresentEnabled(app, true);
    AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
    if (alarms != null) {
      alarms.set(
          AlarmManager.ELAPSED_REALTIME,
          SystemClock.elapsedRealtime() + BootPolicy.USER_PRESENT_WINDOW_MS,
          disarmIntent(app));
    }
    armed = true;
  }

  public static void disarm(Context context) {
    Context app = context.getApplicationContext();
    if (!armed && !isUserPresentEnabled(app)) return;
    AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
    if (alarms != null) alarms.cancel(disarmIntent(app));
    setUserPresentEnabled(app, false);
    armed = false;
  }

  private static boolean isUserPresentEnabled(Context context) {
    int state = context.getPackageManager().getComponentEnabledSetting(userPresentName(context));
    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
  }

  private static void setUserPresentEnabled(Context context, boolean enabled) {
    context.getPackageManager().setComponentEnabledSetting(
        userPresentName(context),
        enabled
            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP);
  }

  private static ComponentName userPresentName(Context context) {
    return new ComponentName(context, UserPresentReceiver.class);
  }

  private static PendingIntent disarmIntent(Context context) {
    Intent intent = new Intent(context, BootReceiver.class);
    intent.setAction(ACTION_DISARM);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    return PendingIntent.getBroadcast(context, REQUEST_DISARM, intent, flags);
  }
}
