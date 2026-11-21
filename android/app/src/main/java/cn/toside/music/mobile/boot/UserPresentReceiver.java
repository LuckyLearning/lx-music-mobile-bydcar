package cn.toside.music.mobile.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * 开机后短窗口内的亮屏补拉。平时在清单里是关闭的，由 {@link BootWindow} 临时打开。
 */
public class UserPresentReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (context == null || intent == null) return;
    if (!BootPolicy.ACTION_USER_PRESENT.equals(intent.getAction())) return;

    Context app = context.getApplicationContext();
    boolean enabled = BootPrefs.isEnabled(app);
    long uptime = SystemClock.elapsedRealtime();
    long now = System.currentTimeMillis();
    long lastForeground = BootPrefs.lastForegroundWall(app);
    if (!BootPolicy.shouldRetryOnUserPresent(enabled, uptime, now, lastForeground)) {
      BootWindow.disarm(app);
      return;
    }
    BootLaunch.start(app);
  }
}
