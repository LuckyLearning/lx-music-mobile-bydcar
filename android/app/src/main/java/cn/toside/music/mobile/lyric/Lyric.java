package cn.toside.music.mobile.lyric;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Lyric extends LyricPlayer {
  LyricView lyricView = null;
  LyricEvent lyricEvent = null;
  ReactApplicationContext reactAppContext;

  boolean isRunPlayer = false;
  // String lastText = "LX Music ^-^";
  int lastLine = 0;
  List lines = new ArrayList();
  boolean isShowTranslation;
  boolean isShowRoma;
  boolean isShowLyricView = false;
  boolean isSendLyricTextEvent = false;
  boolean isScreenOff = false;
  String lyricText = "";
  String translationText = "";
  String romaLyricText = "";

  Lyric(ReactApplicationContext reactContext, boolean isShowTranslation, boolean isShowRoma, float playbackRate) {
    this.reactAppContext = reactContext;
    this.isShowTranslation = isShowTranslation;
    this.isShowRoma = isShowRoma;
    this.playbackRate = playbackRate;
    registerScreenBroadcastReceiver();
    // checkA2DPConnection(reactContext);
  }

  private void registerScreenBroadcastReceiver() {
    final IntentFilter theFilter = new IntentFilter();
    /** System Defined Broadcast */
    theFilter.addAction(Intent.ACTION_SCREEN_ON);
    theFilter.addAction(Intent.ACTION_SCREEN_OFF);

    BroadcastReceiver screenOnOffReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String strAction = intent.getAction();

        switch (Objects.requireNonNull(strAction)) {
          case Intent.ACTION_SCREEN_OFF:
            Log.d("Lyric", "ACTION_SCREEN_OFF");
            handleScreenOff();
            break;
          case Intent.ACTION_SCREEN_ON:
            Log.d("Lyric", "ACTION_SCREEN_ON");
            handleScreenOn();
            break;
        }
      }
    };

    reactAppContext.registerReceiver(screenOnOffReceiver, theFilter);
  }

  // private void checkA2DPConnection(Context context) {
  //   BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

  //   if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
  //     bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
  //       @Override
  //       public void onServiceConnected(int profile, BluetoothProfile proxy) {
  //         if (profile == BluetoothProfile.A2DP) {
  //           List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
  //           if (!connectedDevices.isEmpty()) {
  //             System.out.println("已连接的 A2DP 媒体设备：");
  //             for (BluetoothDevice device : connectedDevices) {
  //               System.out.println("设备名称: " + "地址: " + device.getAddress());
  //             }
  //           } else {
  //             System.out.println("没有连接的 A2DP 媒体设备");
  //           }
  //         }
  //         bluetoothAdapter.closeProfileProxy(profile, proxy);
  //       }

  //       @Override
  //       public void onServiceDisconnected(int profile) {
  //         // 服务断开时的处理
  //         System.out.println("蓝牙服务断开时的处理");
  //       }
  //     }, BluetoothProfile.A2DP);
  //   } else {
  //     System.out.println("蓝牙未开启或设备不支持蓝牙");
  //   }
  // }

  private boolean isDisableAutoPause() {
    return !isRunPlayer || isSendLyricTextEvent;
  }
  private void handleScreenOff() {
    isScreenOff = true;
    if (isDisableAutoPause()) return;
    setTempPause(true);
  }

  private void handleScreenOn() {
    isScreenOff = false;
    if (isDisableAutoPause()) return;
    if (lyricView == null) lyricView = new LyricView(reactAppContext, lyricEvent);
    lyricView.runOnUiThread(() -> {
      handleGetCurrentLyric(lastLine);
      setTempPause(false);
    });
  }

  private void pausePlayer() {
    if (!isRunPlayer || isShowLyricView || isSendLyricTextEvent) return;
    isRunPlayer = false;
    this.pause();
  }

  private void setCurrentLyric(String lyric, ArrayList<String> extendedLyrics, ArrayList<String> viewExtendedLyrics) {
    if (isShowLyricView && !isScreenOff && lyricView != null) {
      lyricView.setLyric(lyric, viewExtendedLyrics);
    }
    if (isSendLyricTextEvent) {
      WritableMap params = Arguments.createMap();
      params.putString("text", lyric);
      params.putArray("extendedLyrics", Arguments.makeNativeArray(extendedLyrics));
      lyricEvent.sendEvent(lyricEvent.LYRIC_Line_PLAY, params);
    }
  }
  private void handleGetCurrentLyric(int lineNum) {
    lastLine = lineNum;
    if (lineNum >= 0 && lineNum < lines.size()) {
      HashMap line = (HashMap) lines.get(lineNum);
      if (line != null) {
        @SuppressWarnings("unchecked")
        ArrayList<String> extended = (ArrayList<String>) line.get("extendedLyrics");
        // [BYD] 无翻译/音译时用下一行歌词补位，让"最大行数"设置真正起效
        // （此前行数>1 时若没有翻译，悬浮窗永远只显示一行）
        ArrayList<String> viewExtended = extended;
        if (extended == null || extended.isEmpty()) {
          if (lineNum + 1 < lines.size()) {
            HashMap next = (HashMap) lines.get(lineNum + 1);
            if (next != null) {
              String nextText = (String) next.get("text");
              if (nextText != null && !nextText.isEmpty()) {
                viewExtended = new ArrayList<>(1);
                viewExtended.add(nextText);
              }
            }
          }
        }
        setCurrentLyric((String) line.get("text"), extended, viewExtended);
        return;
      }
    }
    setCurrentLyric("", new ArrayList<>(0), new ArrayList<>(0));
  }

  public void setSendLyricTextEvent(boolean isSend) {
    if (isSendLyricTextEvent == isSend) return;
    isSendLyricTextEvent = isSend;
    if (isSend) {
      if (lyricEvent == null) lyricEvent = new LyricEvent(reactAppContext);
      isRunPlayer = true;
    } else {
      pausePlayer();
    }
  }

  public void showDesktopLyric(Bundle options, Promise promise) {
    if (isShowLyricView) return;
    if (lyricEvent == null) lyricEvent = new LyricEvent(reactAppContext);
    isShowLyricView = true;
    // [BYD] 拉起前台服务保活
    startOverlayService();
    if (lyricView == null) lyricView = new LyricView(reactAppContext, lyricEvent);
    try {
      lyricView.showLyricView(options);
    } catch (Exception e) {
      promise.reject(e);
      Log.e("Lyric", e.getMessage());
      return;
    }
    isRunPlayer = true;
    promise.resolve(null);
  }

  // [BYD] 启动/停止前台保活服务
  private void startOverlayService() {
    try {
      Intent intent = new Intent(reactAppContext, LyricOverlayService.class);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        reactAppContext.startForegroundService(intent);
      } else {
        reactAppContext.startService(intent);
      }
    } catch (Exception e) {
      Log.w("Lyric", "startOverlayService failed: " + e.getMessage());
    }
  }

  private void stopOverlayService() {
    try {
      reactAppContext.stopService(new Intent(reactAppContext, LyricOverlayService.class));
    } catch (Exception e) {
      Log.w("Lyric", "stopOverlayService failed: " + e.getMessage());
    }
  }

  public void hideDesktopLyric() {
    if (!isShowLyricView) return;
    isShowLyricView = false;
    pausePlayer();
    // [BYD] 停掉前台保活服务
    stopOverlayService();
    if (lyricView != null) {
      lyricView.destroy();
      lyricView = null;
    }
  }

  private void refreshLyric() {
    if (!isRunPlayer) return;
    ArrayList<String> extendedLyrics = new ArrayList<>(2);
    if (isShowTranslation && !"".equals(translationText)) extendedLyrics.add(translationText);
    if (isShowRoma && !"".equals(romaLyricText)) extendedLyrics.add(romaLyricText);
    super.setLyric(lyricText, extendedLyrics);
  }

  public void setLyric(String lyric, String translation, String romaLyric) {
    lyricText = lyric;
    translationText = translation;
    romaLyricText = romaLyric;
    refreshLyric();
  }

  @Override
  public void onSetLyric(List lines) {
    this.lines = lines;
    handleGetCurrentLyric(-1);
    // for (int i = 0; i < lines.size(); i++) {
    //   HashMap line = (HashMap) lines.get(i);
    //   Log.d("Lyric", "onSetLyric: " +(String) line.get("text") + " " + line.get("extendedLyrics"));
    // }
  }

  @Override
  public void onPlay(int lineNum) {
    handleGetCurrentLyric(lineNum);
    // Log.d("Lyric", lineNum + " " + text + " " + (String) line.get("translation"));
  }

  public void pauseLyric() {
    pause();
    if (!isRunPlayer) return;
    handleGetCurrentLyric(-1);
  }

  public void lockLyric() {
    if (lyricView == null) return;
    lyricView.lockView();
  }

  public void unlockLyric() {
    if (lyricView == null) return;
    lyricView.unlockView();
  }

  public void setMaxLineNum(int maxLineNum) {
    if (lyricView == null) return;
    lyricView.setMaxLineNum(maxLineNum);
  }

  public void setWidth(int width) {
    if (lyricView == null) return;
    lyricView.setWidth(width);
  }

  public void setSingleLine(boolean singleLine) {
    if (lyricView == null) return;
    lyricView.setSingleLine(singleLine);
  }

  public void setShowToggleAnima(boolean showToggleAnima) {
    if (lyricView == null) return;
    lyricView.setShowToggleAnima(showToggleAnima);
  }

  public void toggleTranslation(boolean isShowTranslation) {
    this.isShowTranslation = isShowTranslation;
    refreshLyric();
  }

  public void toggleRoma(boolean isShowRoma) {
    this.isShowRoma = isShowRoma;
    refreshLyric();
  }

  public void setPlayedColor(String unplayColor, String playedColor, String shadowColor) {
    if (lyricView == null) return;
    lyricView.setColor(unplayColor, playedColor, shadowColor);
  }

  public void setAlpha(float alpha) {
    if (lyricView == null) return;
    lyricView.setAlpha(alpha);
  }

  public void setTextSize(float size) {
    if (lyricView == null) return;
    lyricView.setTextSize(size);
  }

  public void setLyricTextPosition(String positionX, String positionY) {
    if (lyricView == null) return;
    lyricView.setLyricTextPosition(positionX, positionY);
  }

  // [BYD] 自定义背景样式
  public void setBackgroundStyle(String bgColor, float bgRadius, String borderColor, float borderWidth) {
    if (lyricView == null) return;
    lyricView.setBackgroundStyle(bgColor, bgRadius, borderColor, borderWidth);
  }

  // [BYD] 字距
  public void setLetterSpacing(float letterSpacing) {
    if (lyricView == null) return;
    lyricView.setLetterSpacing(letterSpacing);
  }

  // [BYD] 切换投屏屏
  public void setTargetDisplay(int displayId) {
    if (lyricView == null) return;
    lyricView.setTargetDisplay(displayId);
  }

  // [BYD] 按屏幕百分比设置悬浮窗位置（0-100）
  public void setPosition(float x, float y) {
    if (lyricView == null) return;
    lyricView.setPositionPercent(x, y);
  }

  // [BYD] 窗口高度百分比（0 = 按行数自适应）
  public void setHeight(float height) {
    if (lyricView == null) return;
    lyricView.setHeightPercent(height);
  }

  // [BYD] 歌词样式：加粗 / 内置空心样式 / 空心大小
  public void setLyricStyle(boolean bold, int hollowStyle, float hollowSize) {
    if (lyricView == null) return;
    lyricView.setLyricStyle(bold, hollowStyle, hollowSize);
  }

  // [BYD] 其它行字号
  public void setOtherTextSize(float size) {
    if (lyricView == null) return;
    lyricView.setOtherTextSize(size);
  }

  // [BYD] 歌名/歌手
  public void setMusicMeta(String name, String singer) {
    if (lyricView == null) return;
    lyricView.setMusicMeta(name, singer);
  }

  // [BYD] 标题显示选项
  public void setTitleOptions(String mode, String position, String align, String color) {
    if (lyricView == null) return;
    lyricView.setTitleOptions(mode, position, align, color);
  }
}
