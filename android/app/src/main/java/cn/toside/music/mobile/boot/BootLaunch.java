package cn.toside.music.mobile.boot;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import cn.toside.music.mobile.MainActivity;

/**
 * 把主界面拉起来。走和用户点图标一样的入口，不附加额外参数，
 * 因此「启动后自动播放」「桌面歌词」等既有启动逻辑保持原样。
 */
public final class BootLaunch {

  private static final String TAG = "LXBoot";

  private BootLaunch() {}

  public static void start(Context context) {
    Context app = context.getApplicationContext();
    // targetSdk 29：Android 10 起，没有悬浮窗权限时后台 startActivity 经常被静默丢掉。
    // 仍然试一次，部分车机在「自启动」白名单里会放行。
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
      Log.w(TAG, "no overlay permission; Android 10+ may block this background activity start");
    }
    Intent launch = new Intent(app, MainActivity.class);
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      app.startActivity(launch);
      Log.i(TAG, "started MainActivity");
    } catch (RuntimeException error) {
      Log.w(TAG, "startActivity failed: " + error.getMessage());
    }
  }
}
