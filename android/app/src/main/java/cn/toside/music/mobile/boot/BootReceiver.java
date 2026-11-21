package cn.toside.music.mobile.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * 开机自启。
 *
 * 开关打开时拉起主界面，效果等同用户点一次图标。AnyLyric 在开机时重绑通知监听并让悬浮窗出现，
 * 是因为它自己不是播放器；本应用是播放器，对应的「已经起来」就是主界面。
 *
 * exported 必须为 true：BOOT_COMPLETED 的发送方是系统进程，写成 false 时广播到不了。
 */
public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (context == null || intent == null) return;
    Context app = context.getApplicationContext();
    String action = intent.getAction();
    if (BootWindow.ACTION_DISARM.equals(action)) {
      BootWindow.disarm(app);
      return;
    }

    boolean enabled = BootPrefs.isEnabled(app);
    if (!BootPolicy.shouldStart(action, enabled)) {
      // 开关关着时把上次残留的亮屏补拉关掉，避免升级或关开关后仍在亮屏时被叫醒。
      if (BootPolicy.ACTION_BOOT_COMPLETED.equals(action)
          || BootPolicy.ACTION_QUICKBOOT_POWERON.equals(action)) {
        BootWindow.disarm(app);
      }
      return;
    }

    long uptime = SystemClock.elapsedRealtime();
    long now = System.currentTimeMillis();
    if (BootPolicy.wasForegroundThisBoot(uptime, now, BootPrefs.lastForegroundWall(app))) {
      BootWindow.disarm(app);
      return;
    }
    BootLaunch.start(app);
    // startActivity 有时会在返回前就把界面拉到 onResume。那种情况补拉窗口不用开。
    if (BootPolicy.wasForegroundThisBoot(
        SystemClock.elapsedRealtime(), System.currentTimeMillis(), BootPrefs.lastForegroundWall(app))) {
      return;
    }
    BootWindow.arm(app);
  }
}
