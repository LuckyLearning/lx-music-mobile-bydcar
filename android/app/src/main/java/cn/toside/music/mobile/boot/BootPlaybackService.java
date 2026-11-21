package cn.toside.music.mobile.boot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import cn.toside.music.mobile.MainActivity;
import cn.toside.music.mobile.R;

/**
 * 开机后台起播。
 *
 * 播放逻辑在 JS（在线地址、上次列表都在那边），所以这里做两件事：
 * 立刻贴一条前台通知，让车机在取地址的那几秒里别把进程冻住；
 * 然后跑无界面的 JS 任务，按 {@link BootRetryPolicy} 恢复上次播放。
 * 任务结束或播放器自己的通知贴上之后，这条「正在恢复」通知就撤掉。
 *
 * 不在接收器里 {@code startForegroundService}：见 {@link BootLaunch}。
 */
public class BootPlaybackService extends HeadlessJsTaskService {

  public static final String ACTION_AUTOPLAY = "cn.toside.music.mobile.boot.AUTOPLAY";
  public static final String TASK_NAME = "BootAutostart";

  private static final String TAG = "LXBoot";
  private static final String CHANNEL_ID = "lx_boot_playback";
  private static final int NOTIFICATION_ID = 20002;

  private static volatile BootPlaybackService instance;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  public static void finishIfRunning() {
    BootPlaybackService service = instance;
    if (service == null) return;
    service.mainHandler.post(service::stopKeepingAlive);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null || !ACTION_AUTOPLAY.equals(intent.getAction())) {
      return START_NOT_STICKY;
    }
    try {
      startForeground(NOTIFICATION_ID, buildNotification());
    } catch (RuntimeException error) {
      Log.w(TAG, "startForeground failed", error);
      String message = error.getMessage();
      if (message == null || message.length() == 0) message = error.getClass().getSimpleName();
      BootStatus.markResult(this, false, "前台服务没起来：" + message);
      stopSelf();
      return START_NOT_STICKY;
    }
    Log.i(TAG, "foreground, starting headless playback");
    return super.onStartCommand(intent, flags, startId);
  }

  @Nullable
  @Override
  protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {
    if (intent == null || !ACTION_AUTOPLAY.equals(intent.getAction())) return null;
    WritableMap data = Arguments.createMap();
    WritableArray delays = Arguments.createArray();
    for (long delay : BootRetryPolicy.DELAYS_MS) delays.pushDouble(delay);
    data.putArray("delays", delays);
    data.putDouble("confirmMs", BootRetryPolicy.CONFIRM_MS);
    // 超时留足三次尝试加在线取地址。0 表示不限时，车机上挂住就收不回来。
    return new HeadlessJsTaskConfig(TASK_NAME, data, 90_000, true);
  }

  @Override
  public void onDestroy() {
    if (instance == this) instance = null;
    stopKeepingAlive();
    super.onDestroy();
  }

  private void stopKeepingAlive() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
      } else {
        stopForeground(true);
      }
    } catch (RuntimeException error) {
      Log.w(TAG, "stopForeground failed", error);
    }
  }

  private Notification buildNotification() {
    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
      NotificationChannel channel = new NotificationChannel(
          CHANNEL_ID,
          getString(R.string.boot_playback_channel),
          NotificationManager.IMPORTANCE_LOW);
      channel.setShowBadge(false);
      manager.createNotificationChannel(channel);
    }
    Intent open = new Intent(this, MainActivity.class);
    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
    }
    PendingIntent content = PendingIntent.getActivity(this, 1008, open, pendingFlags);
    return new NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.boot_playback_title))
        .setContentText(getString(R.string.boot_playback_text))
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(content)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build();
  }
}
