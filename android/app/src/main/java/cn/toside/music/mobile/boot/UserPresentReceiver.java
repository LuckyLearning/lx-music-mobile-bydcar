package cn.toside.music.mobile.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 0.0.9 用它在开机后的亮屏时再拉一次主界面。
 *
 * 那次 {@code startActivity} 和开机时的是同一个限制：Android 10 起后台拉 Activity
 * 会被静默丢掉，而且车机常常没有锁屏，这个广播根本不来。
 * 0.0.10 不再从这里启动任何界面。若上一版把它临时打开了，收到广播就关掉。
 */
public class UserPresentReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (context == null) return;
    BootWindow.disarm(context.getApplicationContext());
  }
}
