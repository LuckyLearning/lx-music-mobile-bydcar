package cn.toside.music.mobile.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启。
 *
 * 开关打开时只做一件事：{@code startService} 拉起播放服务。
 * 不打开主界面。是否自动播放由 JS 看「启动后自动播放」。听风在同一台迪链上的后台自启就是这条路；
 * 0.0.9 的 {@code startActivity} 在 Android 12 上会被静默丢掉。
 *
 * exported 必须为 true：BOOT_COMPLETED 的发送方是系统进程，写成 false 时广播到不了。
 */
public class BootReceiver extends BroadcastReceiver {

  private static final String TAG = "LXBoot";

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
    BootPolicy.Kind kind = BootPolicy.decide(action, enabled);
    Log.i(TAG, "action=" + action + " enabled=" + enabled + " kind=" + kind);
    if (kind == BootPolicy.Kind.NONE) {
      if (BootPolicy.isBootAction(action)) {
        // 关掉 0.0.9 可能留下的亮屏补拉，避免之后亮屏时再去拉主界面。
        BootWindow.disarm(app);
        BootStatus.markReceived(app, BootStatus.ACTION_NONE, enabled ? "" : "switch_off");
      }
      return;
    }

    BootWindow.disarm(app);
    BootLaunch.startInBackground(app);
  }
}
