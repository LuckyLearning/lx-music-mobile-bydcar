package cn.toside.music.mobile.lyric;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

public class LyricModule extends ReactContextBaseJavaModule {
  private final ReactApplicationContext reactContext;
  Lyric lyric;
  // final Map<String, Object> constants = new HashMap<>();

  boolean isShowTranslation = false;
  boolean isShowRoma = false;
  float playbackRate = 1;

  private int listenerCount = 0;

  LyricModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;

    // constants.put("THEME_GREEN", "#07c556");
    // constants.put("THEME_YELLOW", "#fffa12");
    // constants.put("THEME_BLUE", "#19b5fe");
    // constants.put("THEME_RED", "#ff1222");
    // constants.put("THEME_PINK", "#f1828d");
    // constants.put("THEME_PURPLE", "#c851d4");
    // constants.put("THEME_ORANGE", "#fffa12");
    // constants.put("THEME_GREY", "#bdc3c7");
  }

  @Override
  public String getName() {
    return "LyricModule";
  }

//  @Override
//  public Map<String, Object> getConstants() {
//    return constants;
//  }

  @ReactMethod
  public void addListener(String eventName) {
    if (listenerCount == 0) {
      // Set up any upstream listeners or background tasks as necessary
    }

    listenerCount += 1;
  }

  @ReactMethod
  public void removeListeners(Integer count) {
    listenerCount -= count;
    if (listenerCount == 0) {
      // Remove upstream listeners, stop unnecessary background tasks
    }
  }

  @ReactMethod
  public void showDesktopLyric(ReadableMap data, Promise promise) {
    if (lyric == null) lyric = new Lyric(reactContext, isShowTranslation, isShowRoma, playbackRate);
    lyric.showDesktopLyric(Arguments.toBundle(data), promise);
  }

  @ReactMethod
  public void hideDesktopLyric(Promise promise) {
    if (lyric != null) lyric.hideDesktopLyric();
    promise.resolve(null);
  }

  @ReactMethod
  public void setSendLyricTextEvent(boolean isSend, Promise promise) {
    if (lyric == null) lyric = new Lyric(reactContext, isShowTranslation, isShowRoma, playbackRate);
    lyric.setSendLyricTextEvent(isSend);
    promise.resolve(null);
  }


  @ReactMethod
  public void setLyric(String lyric, String translation, String romaLyric, Promise promise) {
    // Log.d("Lyric", "set lyric: " + lyric);
    // Log.d("Lyric", "set lyric translation: " + translation);
    if (this.lyric != null) this.lyric.setLyric(lyric, translation, romaLyric);
    promise.resolve(null);
  }

  @ReactMethod
  public void setPlaybackRate(float playbackRate, Promise promise) {
    this.playbackRate = playbackRate;
    if (lyric != null) lyric.setPlaybackRate(playbackRate);
    promise.resolve(null);
  }

  @ReactMethod
  public void toggleTranslation(boolean isShowTranslation, Promise promise) {
    this.isShowTranslation = isShowTranslation;
    if (lyric != null) lyric.toggleTranslation(isShowTranslation);
    promise.resolve(null);
  }

  @ReactMethod
  public void toggleRoma(boolean isShowRoma, Promise promise) {
    this.isShowRoma = isShowRoma;
    if (lyric != null) lyric.toggleRoma(isShowRoma);
    promise.resolve(null);
  }

  @ReactMethod
  public void play(int time, Promise promise) {
    Log.d("Lyric", "play lyric: " + time);
    if (lyric != null) lyric.play(time);
    promise.resolve(null);
  }

  @ReactMethod
  public void pause(Promise promise) {
    Log.d("Lyric", "play pause");
    if (lyric != null) lyric.pauseLyric();
    promise.resolve(null);
  }

  @ReactMethod
  public void toggleLock(boolean isLock, Promise promise) {
    if (lyric != null) {
      if (isLock) {
        lyric.lockLyric();
      } else {
        lyric.unlockLyric();
      }
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void setColor(String unplayColor, String playedColor, String shadowColor, Promise promise) {
    if (lyric != null) lyric.setPlayedColor(unplayColor, playedColor, shadowColor);
    promise.resolve(null);
  }

  @ReactMethod
  public void setAlpha(float alpha, Promise promise) {
    if (lyric != null) lyric.setAlpha(alpha);
    promise.resolve(null);
  }

  @ReactMethod
  public void setTextSize(float size, Promise promise) {
    if (lyric != null) lyric.setTextSize(size);
    promise.resolve(null);
  }

  @ReactMethod
  public void setMaxLineNum(int maxLineNum, Promise promise) {
    if (lyric != null) lyric.setMaxLineNum(maxLineNum);
    promise.resolve(null);
  }

  @ReactMethod
  public void setSingleLine(boolean singleLine, Promise promise) {
    if (lyric != null) lyric.setSingleLine(singleLine);
    promise.resolve(null);
  }

  @ReactMethod
  public void setShowToggleAnima(boolean showToggleAnima, Promise promise) {
    if (lyric != null) lyric.setShowToggleAnima(showToggleAnima);
    promise.resolve(null);
  }

  @ReactMethod
  public void setWidth(int width, Promise promise) {
    if (lyric != null) lyric.setWidth(width);
    promise.resolve(null);
  }

  @ReactMethod
  public void setLyricTextPosition(String positionX, String positionY, Promise promise) {
    if (lyric != null) lyric.setLyricTextPosition(positionX, positionY);
    promise.resolve(null);
  }

  // [BYD] 枚举可投屏显示器
  @ReactMethod
  public void getDisplays(Promise promise) {
    promise.resolve(LyricDisplays.list(reactContext));
  }

  // [BYD] 设置悬浮窗背景样式
  @ReactMethod
  public void setBackgroundStyle(String bgColor, float bgRadius, String borderColor, float borderWidth, Promise promise) {
    if (lyric != null) lyric.setBackgroundStyle(bgColor, bgRadius, borderColor, borderWidth);
    promise.resolve(null);
  }

  // [BYD] 设置字距
  @ReactMethod
  public void setLetterSpacing(float letterSpacing, Promise promise) {
    if (lyric != null) lyric.setLetterSpacing(letterSpacing);
    promise.resolve(null);
  }

  // [BYD] 切换投屏目标屏
  @ReactMethod
  public void setTargetDisplay(int displayId, Promise promise) {
    if (lyric != null) lyric.setTargetDisplay(displayId);
    promise.resolve(null);
  }

  // [BYD] 按屏幕百分比设置悬浮窗位置（0-100，左上角基准）
  @ReactMethod
  public void setPosition(float x, float y, Promise promise) {
    if (lyric != null) lyric.setPosition(x, y);
    promise.resolve(null);
  }

  // [BYD] 窗口高度百分比（0 = 按行数自适应）
  @ReactMethod
  public void setHeight(float height, Promise promise) {
    if (lyric != null) lyric.setHeight(height);
    promise.resolve(null);
  }

  // [BYD] 歌词样式：加粗 / 内置空心样式(0关/1细/2电影) / 空心大小
  @ReactMethod
  public void setLyricStyle(boolean bold, int hollowStyle, float hollowSize, Promise promise) {
    if (lyric != null) lyric.setLyricStyle(bold, hollowStyle, hollowSize);
    promise.resolve(null);
  }

  // [BYD] 其它行字号
  @ReactMethod
  public void setOtherTextSize(float size, Promise promise) {
    if (lyric != null) lyric.setOtherTextSize(size);
    promise.resolve(null);
  }

  // [BYD] 歌名/歌手
  @ReactMethod
  public void setMusicMeta(String name, String singer, Promise promise) {
    if (lyric != null) lyric.setMusicMeta(name, singer);
    promise.resolve(null);
  }

  // [BYD] 标题显示：模式 / 上下位置 / 对齐 / 颜色
  @ReactMethod
  public void setTitleOptions(String mode, String position, String align, String color, Promise promise) {
    if (lyric != null) lyric.setTitleOptions(mode, position, align, color);
    promise.resolve(null);
  }

  @ReactMethod
  public void checkOverlayPermission(Promise promise) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactContext)) {
      promise.reject(new Exception("Permission denied"));
    }
    promise.resolve(null);
  }

  @ReactMethod
  public void openOverlayPermissionActivity(Promise promise) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactContext)) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + reactContext.getApplicationContext().getPackageName()));
      reactContext.startActivityForResult(intent, 1, null);
    }
    promise.resolve(null);
  }

}
