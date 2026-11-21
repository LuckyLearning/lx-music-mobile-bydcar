package cn.toside.music.mobile;

import com.reactnativenavigation.NavigationActivity;

import cn.toside.music.mobile.boot.BootPrefs;
import cn.toside.music.mobile.boot.BootWindow;

public class MainActivity extends NavigationActivity {

  @Override
  public void onResume() {
    super.onResume();
    // 开机补拉只认「界面真正到过前台」。开关关闭时只读偏好，不写盘。
    if (!BootPrefs.isEnabled(this)) return;
    BootPrefs.markForegroundOnce(this);
    BootWindow.disarm(this);
  }
}
