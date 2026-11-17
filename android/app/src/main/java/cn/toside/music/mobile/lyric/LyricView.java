package cn.toside.music.mobile.lyric;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 桌面歌词悬浮窗。
 *
 * [BYD] 布局：
 * FrameLayout(root, 背景独立于文字透明度)
 *   └─ LinearLayout(column)
 *        ├─ titleView（可选，歌名/歌名+歌手）
 *        ├─ textView 当前行 LyricSwitchView
 *        └─ otherView 其它行 StrokeTextView
 *
 * 背景挂在 root 上，锁定时也保留；文字透明度只改文字色 alpha，不再 setAlpha 整窗，
 * 避免「背景色不起效」。
 */
public class LyricView extends Activity implements View.OnTouchListener {
  FrameLayout rootView = null;
  LinearLayout columnView = null;
  StrokeTextView titleView = null;
  LyricSwitchView textView = null;
  StrokeTextView otherView = null;
  WindowManager windowManager = null;
  WindowManager.LayoutParams layoutParams = null;
  final private ReactApplicationContext reactContext;
  final private LyricEvent lyricEvent;

  private float lastX;
  private float lastY;
  private float nowX;
  private float nowY;
  private float tranX;
  private float tranY;
  private float prevViewPercentageX = 0;
  private float prevViewPercentageY = 0;
  private float widthPercentage = 1f;

  private float preY = 0;

  private boolean isLock = false;
  private boolean isSingleLine = false;
  private boolean isShowToggleAnima = false;
  private String unplayColor = "rgba(255, 255, 255, 0.55)";
  private String playedColor = "rgba(7, 197, 86, 1)";
  private String shadowColor = "rgba(0, 0, 0, 0.15)";
  // [BYD] 背景 / 边框 / 字距
  private String bgColor = "rgba(0, 0, 0, 0.35)";
  private float bgRadius = 12f;
  private String borderColor = "rgba(255, 255, 255, 0.2)";
  private float borderWidth = 0f;
  private float letterSpacing = 0f;
  // [BYD] 加粗 + 内置空心字样式（0=关 / 1=细描边空心 / 2=电影字幕空心）
  private boolean bold = false;
  private int hollowStyle = 0;
  private float hollowSize = 4f;
  private float heightPercentage = 0f;
  private int targetDisplayId = android.view.Display.DEFAULT_DISPLAY;
  private String textX = "LEFT";
  private String textY = "TOP";
  private float alpha = 1f;
  private float textSize = 18f;
  private float otherTextSize = 14f;
  private int maxWidth = 0;
  private int maxHeight = 0;

  private int maxLineNum = 5;
  private String currentLyric = "LX Music ^-^";
  private ArrayList<String> currentExtendedLyrics = new ArrayList<>();

  // [BYD] 歌名/歌手
  private String musicName = "";
  private String musicSinger = "";
  private String titleMode = "none"; // none | name | name_singer
  private String titlePosition = "above"; // above | below
  private String titleAlign = "left"; // left | center | right
  private String titleColor = "rgba(255, 255, 255, 0.85)";

  private int mLastRotation;
  private OrientationEventListener orientationEventListener = null;

  final Handler fixViewPositionHandler;
  final Runnable fixViewPositionRunnable = this::updateViewPosition;

  // 内置空心样式：细描边偏白 / 电影字幕粗黑描边
  private static final int HOLLOW_STYLE1_COLOR = Color.argb(230, 255, 255, 255);
  private static final int HOLLOW_STYLE2_COLOR = Color.argb(230, 0, 0, 0);

  LyricView(ReactApplicationContext reactContext, LyricEvent lyricEvent) {
    this.reactContext = reactContext;
    this.lyricEvent = lyricEvent;
    fixViewPositionHandler = new Handler();
  }

  private void listenOrientationEvent() {
    if (orientationEventListener == null) {
      orientationEventListener = new OrientationEventListener(reactContext, SensorManager.SENSOR_DELAY_NORMAL) {
        @Override
        public void onOrientationChanged(int orientation) {
          Display display = windowManager.getDefaultDisplay();
          int rotation = display.getRotation();
          if (rotation != mLastRotation) {
            fixViewPositionHandler.postDelayed(fixViewPositionRunnable, 300);
          }
          mLastRotation = rotation;
        }
      };
    }
    if (orientationEventListener.canDetectOrientation()) {
      orientationEventListener.enable();
    }
  }

  private void removeOrientationEvent() {
    if (orientationEventListener == null) return;
    orientationEventListener.disable();
  }

  private int getLayoutParamsFlags() {
    int flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

    if (isLock) {
      flag = flag | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    }

    return flag;
  }

  private boolean updateWH() {
    Display display = windowManager.getDefaultDisplay();
    Point size = new Point();
    display.getRealSize(size);
    if (maxWidth == size.x && maxHeight == size.y) return false;
    maxWidth = size.x;
    maxHeight = size.y;
    return true;
  }

  private GradientDrawable buildBackground() {
    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.RECTANGLE);
    bg.setColor(parseColor(bgColor));
    bg.setCornerRadius(bgRadius);
    if (borderWidth > 0f) bg.setStroke(Math.max(1, Math.round(borderWidth)), parseColor(borderColor));
    return bg;
  }

  private void applyRootBackground() {
    if (rootView == null) return;
    // 锁定时也保留背景；仅降低边框存在感（边框宽仍按设置）
    rootView.setBackground(buildBackground());
    rootView.setPadding(dp(10), dp(8), dp(10), dp(8));
  }

  private int dp(int v) {
    float d = reactContext.getResources().getDisplayMetrics().density;
    return Math.round(v * d);
  }

  private void resolveWindowManagerForTarget() {
    Display target = LyricDisplays.resolve(reactContext, targetDisplayId);
    Context wmContext;
    if (target != null && target.getDisplayId() != Display.DEFAULT_DISPLAY) {
      wmContext = reactContext.createDisplayContext(target);
    } else {
      wmContext = reactContext;
    }
    windowManager = (WindowManager) wmContext.getSystemService(Context.WINDOW_SERVICE);
  }

  private int estimateContentHeight() {
    int lineH = textView != null && textView.getPaint() != null
      ? textView.getPaint().getFontMetricsInt(null)
      : (int) (textSize * 1.4f);
    int otherH = otherView != null && otherView.getPaint() != null
      ? otherView.getPaint().getFontMetricsInt(null)
      : (int) (otherTextSize * 1.35f);
    int titleH = 0;
    if (!"none".equals(titleMode) && titleView != null && titleView.getVisibility() == View.VISIBLE) {
      titleH = titleView.getPaint().getFontMetricsInt(null) + dp(4);
    }
    int otherLines = Math.max(0, maxLineNum - 1);
    if (isSingleLine) otherLines = 0;
    return titleH + lineH + otherH * otherLines + dp(16);
  }

  private void setLayoutParamsHeight() {
    if (rootView == null) return;
    int height = heightPercentage > 0f
      ? (int) (maxHeight * heightPercentage)
      : estimateContentHeight();
    if (height > maxHeight - 100) height = maxHeight - 100;
    if (height < dp(40)) height = dp(40);
    layoutParams.height = height;
  }

  private void fixViewPosition() {
    int maxX = maxWidth - layoutParams.width;
    int x = (int) (maxWidth * prevViewPercentageX);
    if (x < 0) x = 0;
    else if (x > maxX) x = maxX;
    if (layoutParams.x != x) layoutParams.x = x;

    setLayoutParamsHeight();

    int maxY = maxHeight - layoutParams.height;
    int y = (int) (maxHeight * prevViewPercentageY);
    if (y < 0) y = 0;
    else if (y > maxY) y = maxY;
    if (layoutParams.y != y) layoutParams.y = y;
  }

  private void updateViewPosition() {
    if (!updateWH()) return;

    int width = (int) (maxWidth * widthPercentage);
    if (layoutParams.width != width) {
      layoutParams.width = width;
    }

    fixViewPosition();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void sendPositionEvent(float x, float y) {
    WritableMap params = Arguments.createMap();
    params.putDouble("x", x);
    params.putDouble("y", y);
    lyricEvent.sendEvent(lyricEvent.SET_VIEW_POSITION, params);
  }

  /** RN Arguments.toBundle 把数字都写成 Double，统一用这个读，避免 getInt 读不到 */
  private static double readNum(Bundle options, String key, double fallback) {
    if (options == null || !options.containsKey(key)) return fallback;
    Object v = options.get(key);
    if (v instanceof Number) return ((Number) v).doubleValue();
    return fallback;
  }

  public void showLyricView(Bundle options) {
    isLock = options.getBoolean("isLock", isLock);
    isSingleLine = options.getBoolean("isSingleLine", isSingleLine);
    isShowToggleAnima = options.getBoolean("isShowToggleAnima", isShowToggleAnima);
    unplayColor = options.getString("unplayColor", unplayColor);
    playedColor = options.getString("playedColor", playedColor);
    shadowColor = options.getString("shadowColor", shadowColor);
    prevViewPercentageX = (float) readNum(options, "lyricViewX", 0) / 100f;
    prevViewPercentageY = (float) readNum(options, "lyricViewY", 0) / 100f;
    textX = options.getString("textX", textX);
    textY = options.getString("textY", textY);
    alpha = (float) readNum(options, "alpha", alpha);
    textSize = (float) readNum(options, "textSize", textSize);
    otherTextSize = (float) readNum(options, "otherTextSize", otherTextSize);
    widthPercentage = (float) readNum(options, "width", 100) / 100f;
    maxLineNum = (int) readNum(options, "maxLineNum", maxLineNum);
    bgColor = options.getString("bgColor", bgColor);
    bgRadius = (float) readNum(options, "bgRadius", bgRadius);
    borderColor = options.getString("borderColor", borderColor);
    borderWidth = (float) readNum(options, "borderWidth", borderWidth);
    letterSpacing = (float) readNum(options, "letterSpacing", letterSpacing);
    bold = options.getBoolean("bold", bold);
    hollowStyle = (int) readNum(options, "hollowStyle", hollowStyle);
    hollowSize = (float) readNum(options, "hollowSize", hollowSize);
    heightPercentage = (float) readNum(options, "height", 0) / 100f;
    targetDisplayId = (int) readNum(options, "displayId", targetDisplayId);
    // 兼容旧包把 -1 当主屏
    if (targetDisplayId < 0) targetDisplayId = Display.DEFAULT_DISPLAY;

    musicName = options.getString("musicName", musicName);
    musicSinger = options.getString("musicSinger", musicSinger);
    titleMode = options.getString("titleMode", titleMode);
    titlePosition = options.getString("titlePosition", titlePosition);
    titleAlign = options.getString("titleAlign", titleAlign);
    titleColor = options.getString("titleColor", titleColor);

    handleShowLyric();
    listenOrientationEvent();
  }

  public void showLyricView() {
    try {
      handleShowLyric();
    } catch (Exception e) {
      Log.e("Lyric", e.getMessage());
      return;
    }
    listenOrientationEvent();
  }

  public static int parseColor(String input) {
    if (input == null || input.length() == 0) return Color.BLACK;
    if (input.startsWith("#")) {
      try {
        return Color.parseColor(input);
      } catch (Exception e) {
        return Color.BLACK;
      }
    }
    Pattern c = Pattern.compile("rgba? *\\( *(\\d+), *(\\d+), *(\\d+)(?:, *([\\d.]+))? *\\)");
    Matcher m = c.matcher(input);
    if (m.matches()) {
      int red = Integer.parseInt(m.group(1));
      int green = Integer.parseInt(m.group(2));
      int blue = Integer.parseInt(m.group(3));
      float a = 1;
      if (m.group(4) != null) a = Float.parseFloat(m.group(4));
      return Color.argb((int) (a * 255), red, green, blue);
    }
    return Color.parseColor("#000000");
  }

  /** 把全局文字透明度叠到颜色上（不改 View.setAlpha，以免背景跟着淡） */
  private int withTextAlpha(int color) {
    int baseA = (color >>> 24) & 0xFF;
    int a = Math.max(0, Math.min(255, Math.round(baseA * alpha)));
    return (color & 0x00FFFFFF) | (a << 24);
  }

  private int resolveHorizGravity(String align) {
    if (align == null) align = textX;
    switch (align.toUpperCase()) {
      case "CENTER":
        return Gravity.CENTER_HORIZONTAL;
      case "RIGHT":
        return Gravity.END;
      case "LEFT":
      default:
        return Gravity.START;
    }
  }

  private int resolveVertGravity() {
    switch (textY) {
      case "CENTER":
        return Gravity.CENTER_VERTICAL;
      case "BOTTOM":
        return Gravity.BOTTOM;
      case "TOP":
      default:
        return Gravity.TOP;
    }
  }

  private void applyHollowTo(TextView v, boolean isCurrent) {
    float size = Math.max(0f, hollowSize) * (isCurrent ? 1f : 0.75f);
    if (hollowStyle <= 0 || size <= 0f) {
      if (v instanceof LyricTextView) {
        // LyricSwitchView 内部处理
      } else if (v instanceof StrokeTextView) {
        ((StrokeTextView) v).setStrokeStyle(Color.TRANSPARENT, 0f, false);
      }
      return;
    }
    int strokeColor;
    float strokeW;
    boolean hollow;
    if (hollowStyle == 1) {
      // 细描边空心
      strokeColor = HOLLOW_STYLE1_COLOR;
      strokeW = Math.max(1f, size * 0.7f);
      hollow = true;
    } else {
      // 电影字幕：粗黑描边空心
      strokeColor = HOLLOW_STYLE2_COLOR;
      strokeW = Math.max(1.5f, size * 1.2f);
      hollow = true;
    }
    strokeColor = withTextAlpha(strokeColor);
    if (v instanceof StrokeTextView) {
      ((StrokeTextView) v).setStrokeStyle(strokeColor, strokeW, hollow);
    }
  }

  private void applyHollowToSwitch(LyricSwitchView v, boolean isCurrent) {
    float size = Math.max(0f, hollowSize) * (isCurrent ? 1f : 0.75f);
    if (hollowStyle <= 0 || size <= 0f) {
      v.setStrokeStyle(Color.TRANSPARENT, 0f, false);
      return;
    }
    int strokeColor;
    float strokeW;
    boolean hollow = true;
    if (hollowStyle == 1) {
      strokeColor = HOLLOW_STYLE1_COLOR;
      strokeW = Math.max(1f, size * 0.7f);
    } else {
      strokeColor = HOLLOW_STYLE2_COLOR;
      strokeW = Math.max(1.5f, size * 1.2f);
    }
    v.setStrokeStyle(withTextAlpha(strokeColor), strokeW, hollow);
  }

  private void applyTextStyles() {
    if (textView == null) return;
    Typeface typeface = bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT;
    textView.setBold(bold);
    textView.setTextColor(withTextAlpha(parseColor(playedColor)));
    textView.setShadowColor(withTextAlpha(parseColor(shadowColor)));
    textView.setLetterSpacings(letterSpacing);
    textView.setTextSize(textSize);
    applyHollowToSwitch(textView, true);

    if (otherView != null) {
      otherView.setTypeface(typeface);
      otherView.setTextColor(withTextAlpha(parseColor(unplayColor)));
      otherView.setShadowLayer(1.2f, 1f, 1f, withTextAlpha(parseColor(shadowColor)));
      otherView.setLetterSpacing(letterSpacing);
      otherView.setTextSize(otherTextSize);
      applyHollowTo(otherView, false);
    }

    if (titleView != null) {
      titleView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
      titleView.setTextColor(withTextAlpha(parseColor(titleColor)));
      titleView.setTextSize(Math.max(10f, otherTextSize * 0.95f));
      titleView.setLetterSpacing(letterSpacing * 0.5f);
      // 标题跟随空心样式，但更细一点
      applyHollowTo(titleView, false);
    }
  }

  private void applyTitleText() {
    if (titleView == null) return;
    if ("none".equals(titleMode)) {
      titleView.setVisibility(View.GONE);
      titleView.setText("");
      return;
    }
    String text;
    if ("name_singer".equals(titleMode)) {
      if (musicName == null || musicName.length() == 0) {
        text = musicSinger == null ? "" : musicSinger;
      } else if (musicSinger == null || musicSinger.length() == 0) {
        text = musicName;
      } else {
        text = musicName + " - " + musicSinger;
      }
    } else {
      text = musicName == null ? "" : musicName;
    }
    if (text.length() == 0) {
      titleView.setVisibility(View.GONE);
      titleView.setText("");
      return;
    }
    titleView.setVisibility(View.VISIBLE);
    titleView.setText(text);
    titleView.setGravity(resolveHorizGravity(titleAlign) | Gravity.CENTER_VERTICAL);
  }

  private void rebuildColumnOrder() {
    if (columnView == null || titleView == null || textView == null || otherView == null) return;
    columnView.removeAllViews();
    boolean titleAbove = !"below".equals(titlePosition);
    if (titleAbove) columnView.addView(titleView);
    columnView.addView(textView);
    columnView.addView(otherView);
    if (!titleAbove) columnView.addView(titleView);
    applyTitleText();
  }

  private void createContentViews() {
    rootView = new FrameLayout(reactContext);
    rootView.setOnTouchListener(this);

    columnView = new LinearLayout(reactContext);
    columnView.setOrientation(LinearLayout.VERTICAL);
    FrameLayout.LayoutParams columnLp = new FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    );
    columnLp.gravity = resolveVertGravity() | Gravity.START;
    columnView.setLayoutParams(columnLp);
    rootView.addView(columnView);

    titleView = new StrokeTextView(reactContext);
    titleView.setSingleLine(true);
    titleView.setMaxLines(1);
    titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    titleLp.bottomMargin = dp(4);
    titleView.setLayoutParams(titleLp);

    textView = new LyricSwitchView(reactContext, isSingleLine, isShowToggleAnima);
    textView.setText("");
    LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    textView.setLayoutParams(currentLp);

    otherView = new StrokeTextView(reactContext);
    otherView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams otherLp = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    otherLp.topMargin = dp(2);
    otherView.setLayoutParams(otherLp);

    rebuildColumnOrder();
    applyTextStyles();
    applyTitleText();
    applyLyricGravity();
    applyRootBackground();
  }

  private void applyLyricGravity() {
    int h = resolveHorizGravity(textX);
    if (textView != null) textView.setGravity(h | Gravity.CENTER_VERTICAL);
    if (otherView != null) otherView.setGravity(h | Gravity.TOP);
    if (titleView != null) titleView.setGravity(resolveHorizGravity(titleAlign) | Gravity.CENTER_VERTICAL);
    if (columnView != null && columnView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
      FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) columnView.getLayoutParams();
      lp.gravity = resolveVertGravity() | Gravity.START;
      columnView.setLayoutParams(lp);
    }
  }

  private void handleShowLyric() {
    if (windowManager == null) {
      resolveWindowManagerForTarget();
      layoutParams = new WindowManager.LayoutParams();
      DisplayMetrics outMetrics = new DisplayMetrics();
      windowManager.getDefaultDisplay().getMetrics(outMetrics);
    }

    if (rootView != null) {
      try {
        windowManager.removeView(rootView);
      } catch (Exception ignored) {}
    }

    createContentViews();

    layoutParams.type = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ?
      WindowManager.LayoutParams.TYPE_SYSTEM_ALERT :
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

    layoutParams.flags = getLayoutParamsFlags();
    // Android 12+ 锁定穿透点击：略降窗口 alpha，但背景仍由 drawable 决定
    if (isLock && Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
      layoutParams.alpha = 0.8f;
    } else {
      layoutParams.alpha = 1.0f;
    }

    layoutParams.gravity = Gravity.TOP | Gravity.START;

    updateWH();

    layoutParams.width = (int) (maxWidth * widthPercentage);
    setLayoutParamsHeight();

    layoutParams.x = (int) (maxWidth * prevViewPercentageX);
    layoutParams.y = (int) (maxHeight * prevViewPercentageY);

    fixViewPosition();

    layoutParams.format = PixelFormat.TRANSLUCENT;

    windowManager.addView(rootView, layoutParams);

    // 恢复当前歌词
    setLyric(currentLyric, currentExtendedLyrics);
  }

  public void setLyric(String text, ArrayList<String> extendedLyrics) {
    if (text == null) text = "";
    if (extendedLyrics == null) extendedLyrics = new ArrayList<>();
    currentLyric = text;
    currentExtendedLyrics = extendedLyrics;
    if (textView == null) return;

    textView.setText(text);

    if (otherView == null) return;
    if (isSingleLine || maxLineNum <= 1 || extendedLyrics.size() == 0) {
      otherView.setVisibility(View.GONE);
      otherView.setText("");
    } else {
      int num = maxLineNum - 1;
      StringBuilder sb = new StringBuilder();
      for (String lrc : extendedLyrics) {
        if (lrc == null || lrc.length() == 0) continue;
        if (sb.length() > 0) sb.append('\n');
        sb.append(lrc);
        if (--num < 1) break;
      }
      if (sb.length() == 0) {
        otherView.setVisibility(View.GONE);
        otherView.setText("");
      } else {
        otherView.setVisibility(View.VISIBLE);
        otherView.setMaxLines(Math.max(1, maxLineNum - 1));
        otherView.setText(sb.toString());
      }
    }

    // 自适应高度：行数变化时重算，避免其它行被裁切
    if (windowManager != null && layoutParams != null && heightPercentage <= 0f) {
      setLayoutParamsHeight();
      try {
        windowManager.updateViewLayout(rootView, layoutParams);
      } catch (Exception ignored) {}
    }
  }

  public void setMusicMeta(String name, String singer) {
    this.musicName = name == null ? "" : name;
    this.musicSinger = singer == null ? "" : singer;
    applyTitleText();
    if (windowManager != null && rootView != null && layoutParams != null && heightPercentage <= 0f) {
      setLayoutParamsHeight();
      try {
        windowManager.updateViewLayout(rootView, layoutParams);
      } catch (Exception ignored) {}
    }
  }

  public void setTitleOptions(String mode, String position, String align, String color) {
    if (mode != null) this.titleMode = mode;
    if (position != null) this.titlePosition = position;
    if (align != null) this.titleAlign = align;
    if (color != null) this.titleColor = color;
    if (rootView == null) return;
    rebuildColumnOrder();
    applyTextStyles();
    applyTitleText();
    if (windowManager != null && layoutParams != null) {
      if (heightPercentage <= 0f) setLayoutParamsHeight();
      windowManager.updateViewLayout(rootView, layoutParams);
    }
  }

  public void setMaxLineNum(int maxLineNum) {
    this.maxLineNum = maxLineNum;
    if (rootView == null) return;
    setLyric(currentLyric, currentExtendedLyrics);
    setLayoutParamsHeight();

    int maxY = maxHeight - layoutParams.height;
    int y = layoutParams.y;
    if (y < 0) y = 0;
    else if (y > maxY) y = maxY;
    if (layoutParams.y != y) layoutParams.y = y;

    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void setWidth(int width) {
    if (rootView == null) return;
    widthPercentage = width / 100f;
    layoutParams.width = (int) (maxWidth * widthPercentage);

    int maxX = maxWidth - layoutParams.width;
    int x = layoutParams.x;
    if (x < 0) x = 0;
    else if (x > maxX) x = maxX;
    if (layoutParams.x != x) layoutParams.x = x;

    windowManager.updateViewLayout(rootView, layoutParams);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    int maxX = maxWidth - layoutParams.width;
    int maxY = maxHeight - layoutParams.height;

    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        lastX = event.getRawX();
        lastY = event.getRawY();
        preY = lastY;
        break;
      case MotionEvent.ACTION_MOVE:
        nowX = event.getRawX();
        nowY = event.getRawY();
        if (preY == 0) {
          preY = nowY;
        }
        tranX = nowX - lastX;
        tranY = nowY - lastY;

        int x = layoutParams.x + (int) tranX;
        if (x < 0) x = 0;
        else if (x > maxX) x = maxX;
        int y = layoutParams.y + (int) tranY;
        if (y < 0) y = 0;
        else if (y > maxY) y = maxY;

        layoutParams.x = x;
        layoutParams.y = y;
        windowManager.updateViewLayout(rootView, layoutParams);
        lastX = nowX;
        lastY = nowY;
        break;
      case MotionEvent.ACTION_UP:
        tranY = 0;
        float percentageX = (float) layoutParams.x / (float) maxWidth * 100f;
        float percentageY = (float) layoutParams.y / (float) maxHeight * 100f;
        if (percentageX != prevViewPercentageX || percentageY != prevViewPercentageY) {
          prevViewPercentageX = percentageX / 100f;
          prevViewPercentageY = percentageY / 100f;
          sendPositionEvent(percentageX, percentageY);
        }
        break;
    }
    return true;
  }

  public void lockView() {
    isLock = true;
    if (windowManager == null || rootView == null) return;
    layoutParams.flags = getLayoutParamsFlags();
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
      layoutParams.alpha = 0.8f;
    }
    // 锁定仍保留自定义背景
    applyRootBackground();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void unlockView() {
    isLock = false;
    if (windowManager == null || rootView == null) return;
    layoutParams.flags = getLayoutParamsFlags();
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
      layoutParams.alpha = 1.0f;
    }
    applyRootBackground();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void setColor(String unplayColor, String playedColor, String shadowColor) {
    this.unplayColor = unplayColor;
    this.playedColor = playedColor;
    this.shadowColor = shadowColor;
    applyTextStyles();
  }

  public void setLyricTextPosition(String textX, String textY) {
    this.textX = textX;
    this.textY = textY;
    if (rootView == null) return;
    applyLyricGravity();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void setAlpha(float alpha) {
    this.alpha = alpha;
    applyTextStyles();
  }

  public void setSingleLine(boolean isSingleLine) {
    this.isSingleLine = isSingleLine;
    if (rootView == null) return;
    // 重建视图以切换 LyricTextView / StrokeTextView
    try {
      windowManager.removeView(rootView);
    } catch (Exception ignored) {}
    createContentViews();
    windowManager.addView(rootView, layoutParams);
    if (isLock) lockView();
    else unlockView();
    setLyric(currentLyric, currentExtendedLyrics);
  }

  public void setShowToggleAnima(boolean showToggleAnima) {
    isShowToggleAnima = showToggleAnima;
    if (textView == null) return;
    textView.setShowAnima(showToggleAnima);
  }

  public void setTextSize(float size) {
    this.textSize = size;
    if (rootView == null) return;
    applyTextStyles();
    setLayoutParamsHeight();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void setOtherTextSize(float size) {
    this.otherTextSize = size;
    if (rootView == null) return;
    applyTextStyles();
    setLayoutParamsHeight();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void setBackgroundStyle(String bgColor, float bgRadius, String borderColor, float borderWidth) {
    this.bgColor = bgColor;
    this.bgRadius = bgRadius;
    this.borderColor = borderColor;
    this.borderWidth = borderWidth;
    if (rootView == null) return;
    applyRootBackground();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void setLetterSpacing(float letterSpacing) {
    this.letterSpacing = letterSpacing;
    applyTextStyles();
  }

  public void setPositionPercent(float x, float y) {
    prevViewPercentageX = x / 100f;
    prevViewPercentageY = y / 100f;
    if (windowManager == null || rootView == null || layoutParams == null) return;
    fixViewPosition();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  public void setHeightPercent(float height) {
    heightPercentage = height / 100f;
    if (windowManager == null || rootView == null || layoutParams == null) return;
    setLayoutParamsHeight();
    fixViewPosition();
    windowManager.updateViewLayout(rootView, layoutParams);
  }

  /** [BYD] 加粗 + 内置空心样式 + 空心大小；其它行同步区分大小 */
  public void setLyricStyle(boolean isBold, int newHollowStyle, float newHollowSize) {
    this.bold = isBold;
    this.hollowStyle = newHollowStyle;
    this.hollowSize = newHollowSize;
    if (rootView == null) return;
    applyTextStyles();
    if (windowManager != null && layoutParams != null && heightPercentage <= 0f) {
      setLayoutParamsHeight();
      windowManager.updateViewLayout(rootView, layoutParams);
    }
  }

  public void setTargetDisplay(int displayId) {
    if (displayId < 0) displayId = Display.DEFAULT_DISPLAY;
    if (this.targetDisplayId == displayId) return;
    this.targetDisplayId = displayId;
    if (!isShowEnabled()) return;
    if (windowManager != null && rootView != null) {
      try {
        windowManager.removeView(rootView);
      } catch (Exception ignored) {}
    }
    windowManager = null;
    layoutParams = null;
    rootView = null;
    textView = null;
    otherView = null;
    titleView = null;
    columnView = null;
    handleShowLyric();
  }

  private boolean isShowEnabled() {
    return windowManager != null && rootView != null;
  }

  public void destroyView() {
    if (rootView == null || windowManager == null) return;
    try {
      windowManager.removeView(rootView);
    } catch (Exception ignored) {}
    rootView = null;
    textView = null;
    otherView = null;
    titleView = null;
    columnView = null;
    removeOrientationEvent();
  }

  public void destroy() {
    destroyView();
    windowManager = null;
    layoutParams = null;
  }
}
