package cn.toside.music.mobile.boot;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机后只拉播放服务，不打开主界面。
 *
 * 必须用 {@code startService}，不能用 {@code startForegroundService}。
 * 后者要求 5 秒内调用 {@code startForeground()}；冷启动时读列表、建播放器、取在线地址
 * 在车机上经常超过 5 秒，结果是
 * {@code Context.startForegroundService() did not then call Service.startForeground()}。
 * 听风在 2026-10-18 用模拟器复现过，真机更慢。
 * {@code BOOT_COMPLETED} 接收器自带临时白名单，允许直接 {@code startService}。
 * 前台通知由服务自己在 {@code onStartCommand} 里贴，播放通知由播放器起播时再贴。
 */
public final class BootLaunch {

  private static final String TAG = "LXBoot";

  private BootLaunch() {}

  public static void startInBackground(Context context) {
    Context app = context.getApplicationContext();
    Intent intent = new Intent(app, BootPlaybackService.class);
    intent.setAction(BootPlaybackService.ACTION_AUTOPLAY);
    try {
      ComponentName started = app.startService(intent);
      if (started == null) {
        Log.w(TAG, "startService returned null");
        BootStatus.markResult(app, false, "服务没起来");
        return;
      }
      Log.i(TAG, "started BootPlaybackService");
    } catch (RuntimeException error) {
      Log.w(TAG, "startService failed", error);
      String message = error.getMessage();
      if (message == null || message.length() == 0) message = error.getClass().getSimpleName();
      BootStatus.markResult(app, false, "服务没起来：" + message);
    }
  }
}
