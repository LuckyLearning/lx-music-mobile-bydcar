// [BYD] 多屏枚举辅助类（新增文件，不影响上游任何类）。
// 车机常带副驾屏/后排屏（Presentation display），此工具把 DisplayManager 的可投屏信息
// 整理成 JS 可读的列表，供悬浮歌词选择投射目标屏。
package cn.toside.music.mobile.lyric;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

public final class LyricDisplays {
  private LyricDisplays() {}

  /**
   * 枚举所有可用于投屏的显示器。
   * 首个元素始终是 DEFAULT_DISPLAY，便于前端标注“主屏/车机屏”。
   */
  public static WritableArray list(Context context) {
    WritableArray arr = Arguments.createArray();
    DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    if (dm == null) return arr;
    Display[] displays = dm.getDisplays();
    for (Display d : displays) {
      WritableMap map = Arguments.createMap();
      map.putInt("id", d.getDisplayId());
      map.putString("name", d.getName());
      // getRealSize 在多屏/异形屏下比 getMetrics 更准确
      android.graphics.Point size = new android.graphics.Point();
      android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
      d.getRealSize(size);
      d.getMetrics(metrics);
      map.putInt("width", size.x);
      map.putInt("height", size.y);
      map.putDouble("density", metrics.density);
      map.putBoolean("isDefault", d.getDisplayId() == Display.DEFAULT_DISPLAY);
      // API 17+ 才有 getFlags；Presentation 类型通常为副屏
      int flags = Display.FLAG_PRESENTATION;
      map.putBoolean("isPresentation", (d.getFlags() & flags) == flags);
      arr.pushMap(map);
    }
    return arr;
  }

  /** 按 id 取 Display，找不到时回退到默认屏，保证调用方不拿到 null。 */
  public static Display resolve(Context context, int displayId) {
    DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    if (dm == null) return null;
    Display d = dm.getDisplay(displayId);
    if (d == null) d = dm.getDisplay(Display.DEFAULT_DISPLAY);
    return d;
  }
}
