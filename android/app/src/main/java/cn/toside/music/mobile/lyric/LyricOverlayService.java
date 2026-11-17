// [BYD] 桌面歌词前台服务：悬浮窗显示期间保持前台服务，降低被系统回收的概率。
// 上游实现把悬浮窗挂在 React 应用进程上，后台时容易被车机省电策略杀掉；此服务仅做“保活”。
package cn.toside.music.mobile.lyric;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import cn.toside.music.mobile.R;

public class LyricOverlayService extends Service {
  private static final String CHANNEL_ID = "byd_lyric_overlay";
  private static final int NOTIFICATION_ID = 20001;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    startForeground(NOTIFICATION_ID, buildNotification());
    // 被系统杀掉后不自动重建，由 JS 侧再次 show 时拉起，避免用户关歌词后服务常驻
    return START_NOT_STICKY;
  }

  private Notification buildNotification() {
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
      NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_MIN);
      channel.setShowBadge(false);
      nm.createNotificationChannel(channel);
    }
    return new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("桌面歌词运行中")
      .setContentText("保持歌词悬浮窗稳定显示")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .setOngoing(true)
      .build();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) { return null; }
}
