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
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import java.lang.reflect.Field;
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
 *        └─ 单行：textView LyricSwitchView
 *           多行：lyricLines 整列联动滚动
 *
 * 背景挂在 root 上，锁定时也保留；文字透明度只改文字色 alpha，不再 setAlpha 整窗，
 * 避免「背景色不起效」。
 * 拖动按帧合并，并关掉系统窗口位移动画，避免上下滑动一卡一卡。
 */
public class LyricView extends Activity implements View.OnTouchListener {
  FrameLayout rootView = null;
  LinearLayout columnView = null;
  StrokeTextView titleView = null;
  LyricSwitchView textView = null;
  LyricLinesView lyricLines = null;
  WindowManager windowManager = null;
  WindowManager.LayoutParams layoutParams = null;
  final private ReactApplicationContext reactContext;
  final private LyricEvent lyricEvent;

  private float lastX;
  private float lastY;
  private float tranX;
  private float tranY;
  private float prevViewPercentageX = 0;
  private float prevViewPercentageY = 0;
  private float widthPercentage = 1f;

  private boolean dragging = false;
  private boolean dragFramePosted = false;
  private boolean layoutDirty = false;
  private float pendingX = 0f;
  private float pendingY = 0f;
  private final Choreographer.FrameCallback dragFrameCallback = frameTimeNanos -> {
    dragFramePosted = false;
    applyPendingPosition();
  };

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
  // 当前行、其它行、标题行各自一份，不再互相打折。
  private boolean bold = false;
  private int hollowStyle = 0;
  private float hollowSize = 4f;
  private String otherAlign = "LEFT";
  private float otherLetterSpacing = 0f;
  private boolean otherBold = false;
  private int otherHollowStyle = 0;
  private float otherHollowSize = 3f;
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
  private int currentLineNum = Integer.MIN_VALUE;
  private String currentLyric = "LX Music ^-^";
  private ArrayList<String> currentExtendedLyrics = new ArrayList<>();

  // [BYD] 歌名/歌手
  private String musicName = "";
  private String musicSinger = "";
  private String titleMode = "none"; // none | name | name_singer
  private String titlePosition = "above"; // above | below
  private String titleAlign = "left"; // left | center | right
  private String titleColor = "rgba(255, 255, 255, 0.85)";
  private float titleTextSize = 13f;
  private float titleLetterSpacing = 0f;
  private boolean titleBold = true;
  private int titleHollowStyle = 0;
  private float titleHollowSize = 3f;

  private int mLastRotation;
  private OrientationEventListener orientationEventListener = null;

  final Handler mainHandler;
  final Runnable fixViewPositionRunnable = this::updateViewPosition;

  // 系统给窗口位置变化加了补间，跟手拖动会一顿一顿。这是隐藏 flag。
  private static final int PRIVATE_FLAG_NO_MOVE_ANIMATION = 0x00000040;

  LyricView(ReactApplicationContext reactContext, LyricEvent lyricEvent) {
    this.reactContext = reactContext;
    this.lyricEvent = lyricEvent;
    mainHandler = new Handler(Looper.getMainLooper());
  }

  private boolean onMainThread() {
    return Looper.myLooper() == Looper.getMainLooper();
  }

  private void listenOrientationEvent() {
    if (orientationEventListener == null) {
      orientationEventListener = new OrientationEventListener(reactContext, SensorManager.SENSOR_DELAY_NORMAL) {
        @Override
        public void onOrientationChanged(int orientation) {
          Display display = windowManager.getDefaultDisplay();
          int rotation = display.getRotation();
          if (rotation != mLastRotation) {
            mainHandler.postDelayed(fixViewPositionRunnable, 300);
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
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
      WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

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
    int titleH = 0;
    if (!"none".equals(titleMode) && titleView != null && titleView.getVisibility() == View.VISIBLE) {
      titleH = titleView.getPaint().getFontMetricsInt(null) + dp(4);
    }
    if (!isSingleLine && lyricLines != null) {
      return titleH + lyricLines.getDesiredHeight() + dp(16);
    }
    int lineH = textView != null && textView.getPaint() != null
      ? textView.getPaint().getFontMetricsInt(null)
      : (int) (textSize * 1.4f);
    return titleH + lineH + dp(16);
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
    setLayoutParamsHeight();
    // 手指还按着时不要用旧百分比把窗口拽回去
    if (dragging) {
      layoutDirty = true;
      return;
    }
    int maxX = Math.max(0, maxWidth - layoutParams.width);
    int x = (int) (maxWidth * prevViewPercentageX);
    if (x < 0) x = 0;
    else if (x > maxX) x = maxX;
    if (layoutParams.x != x) layoutParams.x = x;

    int maxY = Math.max(0, maxHeight - layoutParams.height);
    int y = (int) (maxHeight * prevViewPercentageY);
    if (y < 0) y = 0;
    else if (y > maxY) y = maxY;
    if (layoutParams.y != y) layoutParams.y = y;
    pendingX = layoutParams.x;
    pendingY = layoutParams.y;
  }

  /** 关掉系统对悬浮窗移动的补间，否则连续 updateViewLayout 会一卡一卡。 */
  private void disableMoveAnimation() {
    if (layoutParams == null) return;
    try {
      Field field = WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
      field.setAccessible(true);
      int flags = field.getInt(layoutParams);
      field.setInt(layoutParams, flags | PRIVATE_FLAG_NO_MOVE_ANIMATION);
    } catch (Exception ignored) {}
  }

  private void commitLayout() {
    if (windowManager == null || rootView == null || layoutParams == null) return;
    if (dragging) {
      layoutDirty = true;
      return;
    }
    disableMoveAnimation();
    try {
      windowManager.updateViewLayout(rootView, layoutParams);
      layoutDirty = false;
    } catch (Exception ignored) {}
  }

  private void scheduleDragFrame() {
    if (dragFramePosted) return;
    dragFramePosted = true;
    Choreographer.getInstance().postFrameCallback(dragFrameCallback);
  }

  private void applyPendingPosition() {
    if (windowManager == null || rootView == null || layoutParams == null) return;
    int maxX = Math.max(0, maxWidth - layoutParams.width);
    int maxY = Math.max(0, maxHeight - layoutParams.height);
    int x = Math.round(pendingX);
    int y = Math.round(pendingY);
    if (x < 0) x = 0;
    else if (x > maxX) x = maxX;
    if (y < 0) y = 0;
    else if (y > maxY) y = maxY;
    if (layoutParams.x == x && layoutParams.y == y) return;
    layoutParams.x = x;
    layoutParams.y = y;
    disableMoveAnimation();
    try {
      windowManager.updateViewLayout(rootView, layoutParams);
    } catch (Exception ignored) {}
  }

  private void updateViewPosition() {
    if (!updateWH()) return;

    int width = (int) (maxWidth * widthPercentage);
    if (layoutParams.width != width) {
      layoutParams.width = width;
    }

    fixViewPosition();
    commitLayout();
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

  private static float atLeastOne(double value) {
    return Math.max(1f, (float) value);
  }

  /**
   * 当前行 / 其它行 / 标题行各自的字号、对齐、字距、加粗、空心。
   * legacyFallback 只在打开悬浮窗时使用：旧数据没有独立字段时，按升级前实际画出来的效果补上。
   */
  private void readRoleStyles(Bundle options, boolean legacyFallback) {
    if (options == null) return;
    if (options.containsKey("textX")) textX = options.getString("textX", textX);
    if (options.containsKey("textSize")) textSize = atLeastOne(readNum(options, "textSize", textSize));
    if (options.containsKey("otherTextSize")) otherTextSize = atLeastOne(readNum(options, "otherTextSize", otherTextSize));
    if (options.containsKey("letterSpacing")) letterSpacing = (float) readNum(options, "letterSpacing", letterSpacing);
    if (options.containsKey("bold")) bold = options.getBoolean("bold", bold);
    if (options.containsKey("hollowStyle")) hollowStyle = (int) readNum(options, "hollowStyle", hollowStyle);
    if (options.containsKey("hollowSize")) hollowSize = (float) readNum(options, "hollowSize", hollowSize);

    if (options.containsKey("otherAlign")) otherAlign = options.getString("otherAlign", otherAlign);
    else if (legacyFallback) otherAlign = textX;
    if (options.containsKey("otherLetterSpacing")) otherLetterSpacing = (float) readNum(options, "otherLetterSpacing", otherLetterSpacing);
    else if (legacyFallback) otherLetterSpacing = letterSpacing;
    if (options.containsKey("otherBold")) otherBold = options.getBoolean("otherBold", otherBold);
    else if (legacyFallback) otherBold = bold;
    if (options.containsKey("otherHollowStyle")) otherHollowStyle = (int) readNum(options, "otherHollowStyle", otherHollowStyle);
    else if (legacyFallback) otherHollowStyle = hollowStyle;
    if (options.containsKey("otherHollowSize")) otherHollowSize = (float) readNum(options, "otherHollowSize", otherHollowSize);
    else if (legacyFallback) otherHollowSize = hollowSize * 0.75f;

    if (options.containsKey("titleAlign")) titleAlign = options.getString("titleAlign", titleAlign);
    if (options.containsKey("titleTextSize")) titleTextSize = atLeastOne(readNum(options, "titleTextSize", titleTextSize));
    else if (legacyFallback) titleTextSize = Math.max(10f, otherTextSize * 0.95f);
    if (options.containsKey("titleLetterSpacing")) titleLetterSpacing = (float) readNum(options, "titleLetterSpacing", titleLetterSpacing);
    else if (legacyFallback) titleLetterSpacing = letterSpacing * 0.5f;
    if (options.containsKey("titleBold")) titleBold = options.getBoolean("titleBold", titleBold);
    else if (legacyFallback) titleBold = true;
    if (options.containsKey("titleHollowStyle")) titleHollowStyle = (int) readNum(options, "titleHollowStyle", titleHollowStyle);
    else if (legacyFallback) titleHollowStyle = hollowStyle;
    if (options.containsKey("titleHollowSize")) titleHollowSize = (float) readNum(options, "titleHollowSize", titleHollowSize);
    else if (legacyFallback) titleHollowSize = hollowSize * 0.75f;
  }

  public void showLyricView(Bundle options) {
    if (!onMainThread()) {
      mainHandler.post(() -> showLyricView(options));
      return;
    }
    try {
      showLyricViewOnMain(options);
    } catch (Exception e) {
      Log.e("Lyric", "showLyricView", e);
    }
  }

  private void showLyricViewOnMain(Bundle options) {
    isLock = options.getBoolean("isLock", isLock);
    isSingleLine = options.getBoolean("isSingleLine", isSingleLine);
    isShowToggleAnima = options.getBoolean("isShowToggleAnima", isShowToggleAnima);
    unplayColor = options.getString("unplayColor", unplayColor);
    playedColor = options.getString("playedColor", playedColor);
    shadowColor = options.getString("shadowColor", shadowColor);
    prevViewPercentageX = (float) readNum(options, "lyricViewX", 0) / 100f;
    prevViewPercentageY = (float) readNum(options, "lyricViewY", 0) / 100f;
    textY = options.getString("textY", textY);
    alpha = (float) readNum(options, "alpha", alpha);
    widthPercentage = (float) readNum(options, "width", 100) / 100f;
    maxLineNum = (int) readNum(options, "maxLineNum", maxLineNum);
    bgColor = options.getString("bgColor", bgColor);
    bgRadius = (float) readNum(options, "bgRadius", bgRadius);
    borderColor = options.getString("borderColor", borderColor);
    borderWidth = (float) readNum(options, "borderWidth", borderWidth);
    heightPercentage = (float) readNum(options, "height", 0) / 100f;
    readRoleStyles(options, true);
    targetDisplayId = (int) readNum(options, "displayId", targetDisplayId);
    // 兼容旧包把 -1 当主屏
    if (targetDisplayId < 0) targetDisplayId = Display.DEFAULT_DISPLAY;

    musicName = options.getString("musicName", musicName);
    musicSinger = options.getString("musicSinger", musicSinger);
    titleMode = options.getString("titleMode", titleMode);
    titlePosition = options.getString("titlePosition", titlePosition);
    titleColor = options.getString("titleColor", titleColor);

    handleShowLyric();
    listenOrientationEvent();
  }

  public void showLyricView() {
    if (!onMainThread()) {
      mainHandler.post(this::showLyricView);
      return;
    }
    try {
      handleShowLyric();
    } catch (Exception e) {
      Log.e("Lyric", e.getMessage());
      return;
    }
    listenOrientationEvent();
  }

  /** CSS #RRGGBBAA（以及 #RGBA）转成 Android 的 #AARRGGBB。半截或不合法的值不抛异常。 */
  private static String cssHexToAndroid(String raw) {
    String body = raw.charAt(0) == '#' ? raw.substring(1) : raw;
    if (body.length() == 3 || body.length() == 4) {
      StringBuilder expanded = new StringBuilder(body.length() * 2);
      for (int i = 0; i < body.length(); i++) {
        expanded.append(body.charAt(i)).append(body.charAt(i));
      }
      body = expanded.toString();
    }
    if (body.length() == 8) return "#" + body.substring(6, 8) + body.substring(0, 6);
    return "#" + body;
  }

  public static int parseColor(String input) {
    if (input == null || input.length() == 0) return Color.BLACK;
    String value = input.trim();
    if (value.startsWith("#")) {
      try {
        return Color.parseColor(cssHexToAndroid(value));
      } catch (Exception e) {
        return Color.BLACK;
      }
    }
    try {
      Pattern c = Pattern.compile("rgba? *\\( *(\\d+), *(\\d+), *(\\d+)(?:, *([\\d.]+))? *\\)");
      Matcher m = c.matcher(value);
      if (m.matches()) {
        int red = Integer.parseInt(m.group(1));
        int green = Integer.parseInt(m.group(2));
        int blue = Integer.parseInt(m.group(3));
        float a = 1;
        if (m.group(4) != null) a = Float.parseFloat(m.group(4));
        return Color.argb((int) (a * 255), red, green, blue);
      }
    } catch (Exception e) {
      return Color.BLACK;
    }
    try {
      return Color.parseColor("#000000");
    } catch (Exception e) {
      return Color.BLACK;
    }
  }

  /** 把全局文字透明度叠到颜色上（不改 View.setAlpha，以免背景跟着淡） */
  private int withTextAlpha(int color) {
    return LyricStroke.withAlpha(color, alpha);
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

  private void applyTextStyles() {
    if (textView != null) {
      textView.setBold(bold);
      textView.setTextColor(withTextAlpha(parseColor(playedColor)));
      textView.setShadowColor(withTextAlpha(parseColor(shadowColor)));
      textView.setLetterSpacings(letterSpacing);
      textView.setTextSize(textSize);
      LyricStroke.apply(textView, hollowStyle, hollowSize, alpha);
    }

    if (lyricLines != null) {
      lyricLines.applyStyles(
        withTextAlpha(parseColor(playedColor)),
        withTextAlpha(parseColor(unplayColor)),
        withTextAlpha(parseColor(shadowColor)),
        textSize,
        otherTextSize,
        letterSpacing,
        otherLetterSpacing,
        bold,
        otherBold,
        hollowStyle,
        otherHollowStyle,
        hollowSize,
        otherHollowSize,
        alpha
      );
    }

    if (titleView != null) {
      titleView.setTypeface(titleBold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
      titleView.setTextColor(withTextAlpha(parseColor(titleColor)));
      titleView.setTextSize(Math.max(1f, titleTextSize));
      titleView.setLetterSpacing(titleLetterSpacing);
      LyricStroke.apply(titleView, titleHollowStyle, titleHollowSize, alpha);
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
    View lyricBody = isSingleLine ? textView : lyricLines;
    if (columnView == null || titleView == null || lyricBody == null) return;
    columnView.removeAllViews();
    boolean titleAbove = !"below".equals(titlePosition);
    if (titleAbove) columnView.addView(titleView);
    columnView.addView(lyricBody);
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

    if (isSingleLine) {
      textView = new LyricSwitchView(reactContext, true, isShowToggleAnima);
      textView.setText("");
      LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      );
      textView.setLayoutParams(currentLp);
      lyricLines = null;
    } else {
      textView = null;
      lyricLines = new LyricLinesView(reactContext);
      lyricLines.setShowAnima(isShowToggleAnima);
      lyricLines.setVisibleCount(Math.max(1, maxLineNum));
      LinearLayout.LayoutParams linesLp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      );
      lyricLines.setLayoutParams(linesLp);
    }

    rebuildColumnOrder();
    applyTextStyles();
    applyTitleText();
    applyLyricGravity();
    applyRootBackground();
  }

  private void applyLyricGravity() {
    int current = resolveHorizGravity(textX);
    int other = resolveHorizGravity(otherAlign);
    if (textView != null) textView.setGravity(current | Gravity.CENTER_VERTICAL);
    if (lyricLines != null) lyricLines.setGravities(current, other);
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
    layoutParams.windowAnimations = 0;
    disableMoveAnimation();

    windowManager.addView(rootView, layoutParams);

    // 恢复当前歌词
    setLyric(currentLyric, currentExtendedLyrics, currentLineNum);
  }

  private void relayoutIfHeightChanged() {
    if (windowManager == null || layoutParams == null || rootView == null) return;
    if (heightPercentage > 0f) return;
    int before = layoutParams.height;
    setLayoutParamsHeight();
    if (layoutParams.height != before) commitLayout();
  }

  public void setLyric(String text, ArrayList<String> extendedLyrics) {
    setLyric(text, extendedLyrics, currentLineNum);
  }

  public void setLyric(String text, ArrayList<String> extendedLyrics, int lineNum) {
    if (!onMainThread()) {
      String safe = text == null ? "" : text;
      ArrayList<String> copy = extendedLyrics == null ? new ArrayList<>() : new ArrayList<>(extendedLyrics);
      mainHandler.post(() -> setLyric(safe, copy, lineNum));
      return;
    }
    if (text == null) text = "";
    if (extendedLyrics == null) extendedLyrics = new ArrayList<>();
    currentLyric = text;
    currentExtendedLyrics = extendedLyrics;
    currentLineNum = lineNum;

    if (isSingleLine) {
      if (textView != null) {
        CharSequence shown = textView.getText();
        if (shown == null || !text.contentEquals(shown)) textView.setText(text);
      }
    } else if (lyricLines != null) {
      lyricLines.setLines(text, extendedLyrics, lineNum);
    }

    // 行高固定后高度通常不变，避免每句歌词都 updateViewLayout 卡一下
    relayoutIfHeightChanged();
  }

  public void setMusicMeta(String name, String singer) {
    if (!onMainThread()) {
      String safeName = name == null ? "" : name;
      String safeSinger = singer == null ? "" : singer;
      mainHandler.post(() -> setMusicMeta(safeName, safeSinger));
      return;
    }
    this.musicName = name == null ? "" : name;
    this.musicSinger = singer == null ? "" : singer;
    applyTitleText();
    relayoutIfHeightChanged();
  }

  public void setTitleOptions(String mode, String position, String align, String color) {
    if (!onMainThread()) {
      mainHandler.post(() -> setTitleOptions(mode, position, align, color));
      return;
    }
    if (mode != null) this.titleMode = mode;
    if (position != null) this.titlePosition = position;
    if (align != null) this.titleAlign = align;
    if (color != null) this.titleColor = color;
    if (rootView == null) return;
    rebuildColumnOrder();
    applyTextStyles();
    applyTitleText();
    if (heightPercentage <= 0f) setLayoutParamsHeight();
    commitLayout();
  }

  public void setMaxLineNum(int maxLineNum) {
    if (!onMainThread()) {
      mainHandler.post(() -> setMaxLineNum(maxLineNum));
      return;
    }
    this.maxLineNum = maxLineNum;
    if (rootView == null) return;
    if (lyricLines != null) lyricLines.setVisibleCount(Math.max(1, maxLineNum));
    setLayoutParamsHeight();

    int maxY = Math.max(0, maxHeight - layoutParams.height);
    int y = layoutParams.y;
    if (y < 0) y = 0;
    else if (y > maxY) y = maxY;
    if (layoutParams.y != y) layoutParams.y = y;

    commitLayout();
  }

  public void setWidth(int width) {
    if (!onMainThread()) {
      mainHandler.post(() -> setWidth(width));
      return;
    }
    if (rootView == null) return;
    widthPercentage = width / 100f;
    layoutParams.width = (int) (maxWidth * widthPercentage);

    int maxX = Math.max(0, maxWidth - layoutParams.width);
    int x = layoutParams.x;
    if (x < 0) x = 0;
    else if (x > maxX) x = maxX;
    if (layoutParams.x != x) layoutParams.x = x;

    commitLayout();
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    if (layoutParams == null || windowManager == null || rootView == null) return false;
    int maxX = Math.max(0, maxWidth - layoutParams.width);
    int maxY = Math.max(0, maxHeight - layoutParams.height);

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        dragging = true;
        lastX = event.getRawX();
        lastY = event.getRawY();
        // 按下时的窗口坐标做基准，整段手势用浮点偏移，避免不足 1px 被截掉
        tranX = layoutParams.x;
        tranY = layoutParams.y;
        pendingX = tranX;
        pendingY = tranY;
        break;
      case MotionEvent.ACTION_MOVE: {
        if (!dragging) {
          dragging = true;
          lastX = event.getRawX();
          lastY = event.getRawY();
          tranX = layoutParams.x;
          tranY = layoutParams.y;
        }
        float x = tranX + (event.getRawX() - lastX);
        float y = tranY + (event.getRawY() - lastY);
        if (x < 0) x = 0;
        else if (x > maxX) x = maxX;
        if (y < 0) y = 0;
        else if (y > maxY) y = maxY;
        pendingX = x;
        pendingY = y;
        scheduleDragFrame();
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        dragging = false;
        applyPendingPosition();
        float oldPctX = prevViewPercentageX;
        float oldPctY = prevViewPercentageY;
        if (maxWidth > 0 && maxHeight > 0) {
          // 先记下松手位置，后面的高度修正才不会把窗口拉回拖动前的百分比
          prevViewPercentageX = layoutParams.x / (float) maxWidth;
          prevViewPercentageY = layoutParams.y / (float) maxHeight;
        }
        if (layoutDirty) {
          layoutDirty = false;
          fixViewPosition();
          commitLayout();
          if (maxWidth > 0 && maxHeight > 0) {
            prevViewPercentageX = layoutParams.x / (float) maxWidth;
            prevViewPercentageY = layoutParams.y / (float) maxHeight;
          }
        }
        if (maxWidth <= 0 || maxHeight <= 0) break;
        if (Math.abs(prevViewPercentageX - oldPctX) > 0.0005f || Math.abs(prevViewPercentageY - oldPctY) > 0.0005f) {
          sendPositionEvent(prevViewPercentageX * 100f, prevViewPercentageY * 100f);
        }
        break;
      }
      default:
        break;
    }
    return true;
  }

  public void lockView() {
    if (!onMainThread()) {
      mainHandler.post(this::lockView);
      return;
    }
    isLock = true;
    if (windowManager == null || rootView == null) return;
    layoutParams.flags = getLayoutParamsFlags();
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
      layoutParams.alpha = 0.8f;
    }
    // 锁定仍保留自定义背景
    applyRootBackground();
    commitLayout();
  }

  public void unlockView() {
    if (!onMainThread()) {
      mainHandler.post(this::unlockView);
      return;
    }
    isLock = false;
    if (windowManager == null || rootView == null) return;
    layoutParams.flags = getLayoutParamsFlags();
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
      layoutParams.alpha = 1.0f;
    }
    applyRootBackground();
    commitLayout();
  }

  public void setColor(String unplayColor, String playedColor, String shadowColor) {
    if (!onMainThread()) {
      mainHandler.post(() -> setColor(unplayColor, playedColor, shadowColor));
      return;
    }
    this.unplayColor = unplayColor;
    this.playedColor = playedColor;
    this.shadowColor = shadowColor;
    applyTextStyles();
  }

  public void setLyricTextPosition(String textX, String textY) {
    if (!onMainThread()) {
      mainHandler.post(() -> setLyricTextPosition(textX, textY));
      return;
    }
    this.textX = textX;
    this.textY = textY;
    if (rootView == null) return;
    applyLyricGravity();
    commitLayout();
  }

  public void setAlpha(float alpha) {
    if (!onMainThread()) {
      mainHandler.post(() -> setAlpha(alpha));
      return;
    }
    this.alpha = alpha;
    applyTextStyles();
  }

  public void setSingleLine(boolean isSingleLine) {
    if (!onMainThread()) {
      mainHandler.post(() -> setSingleLine(isSingleLine));
      return;
    }
    this.isSingleLine = isSingleLine;
    if (rootView == null) return;
    // 重建视图以切换单行跑马灯 / 多行联动滚动
    try {
      windowManager.removeView(rootView);
    } catch (Exception ignored) {}
    createContentViews();
    disableMoveAnimation();
    windowManager.addView(rootView, layoutParams);
    if (isLock) lockView();
    else unlockView();
    setLyric(currentLyric, currentExtendedLyrics, currentLineNum);
  }

  public void setShowToggleAnima(boolean showToggleAnima) {
    if (!onMainThread()) {
      mainHandler.post(() -> setShowToggleAnima(showToggleAnima));
      return;
    }
    isShowToggleAnima = showToggleAnima;
    if (textView != null) textView.setShowAnima(showToggleAnima);
    if (lyricLines != null) lyricLines.setShowAnima(showToggleAnima);
  }

  public void setTextSize(float size) {
    if (!onMainThread()) {
      mainHandler.post(() -> setTextSize(size));
      return;
    }
    this.textSize = Math.max(1f, size);
    if (rootView == null) return;
    applyTextStyles();
    setLayoutParamsHeight();
    commitLayout();
  }

  public void setOtherTextSize(float size) {
    if (!onMainThread()) {
      mainHandler.post(() -> setOtherTextSize(size));
      return;
    }
    this.otherTextSize = Math.max(1f, size);
    if (rootView == null) return;
    applyTextStyles();
    setLayoutParamsHeight();
    commitLayout();
  }

  public void setBackgroundStyle(String bgColor, float bgRadius, String borderColor, float borderWidth) {
    if (!onMainThread()) {
      mainHandler.post(() -> setBackgroundStyle(bgColor, bgRadius, borderColor, borderWidth));
      return;
    }
    this.bgColor = bgColor;
    this.bgRadius = bgRadius;
    this.borderColor = borderColor;
    this.borderWidth = borderWidth;
    if (rootView == null) return;
    applyRootBackground();
  }

  public void setLetterSpacing(float letterSpacing) {
    if (!onMainThread()) {
      mainHandler.post(() -> setLetterSpacing(letterSpacing));
      return;
    }
    this.letterSpacing = letterSpacing;
    applyTextStyles();
  }

  public void setPositionPercent(float x, float y) {
    if (!onMainThread()) {
      mainHandler.post(() -> setPositionPercent(x, y));
      return;
    }
    prevViewPercentageX = x / 100f;
    prevViewPercentageY = y / 100f;
    if (windowManager == null || rootView == null || layoutParams == null) return;
    if (dragging) return;
    int maxX = Math.max(0, maxWidth - layoutParams.width);
    int maxY = Math.max(0, maxHeight - layoutParams.height);
    float px = maxWidth * prevViewPercentageX;
    float py = maxHeight * prevViewPercentageY;
    if (px < 0) px = 0;
    else if (px > maxX) px = maxX;
    if (py < 0) py = 0;
    else if (py > maxY) py = maxY;
    pendingX = px;
    pendingY = py;
    scheduleDragFrame();
  }

  public void setHeightPercent(float height) {
    if (!onMainThread()) {
      mainHandler.post(() -> setHeightPercent(height));
      return;
    }
    heightPercentage = height / 100f;
    if (windowManager == null || rootView == null || layoutParams == null) return;
    setLayoutParamsHeight();
    fixViewPosition();
    commitLayout();
  }

  /** [BYD] 当前行：加粗 / 内置空心样式 / 空心大小。其它行和标题行各自单独设置。 */
  public void setLyricStyle(boolean isBold, int newHollowStyle, float newHollowSize) {
    if (!onMainThread()) {
      mainHandler.post(() -> setLyricStyle(isBold, newHollowStyle, newHollowSize));
      return;
    }
    this.bold = isBold;
    this.hollowStyle = newHollowStyle;
    this.hollowSize = newHollowSize;
    if (rootView == null) return;
    applyTextStyles();
    relayoutIfHeightChanged();
  }

  /** [BYD] 一次写入三行各自的字号、对齐、字距、加粗、空心。styles 不再被重新赋值，lambda 可以捕获。 */
  public void setLineStyles(Bundle styles) {
    if (!onMainThread()) {
      mainHandler.post(() -> setLineStyles(styles));
      return;
    }
    readRoleStyles(styles, false);
    if (rootView == null) return;
    applyTextStyles();
    applyLyricGravity();
    applyTitleText();
    setLayoutParamsHeight();
    commitLayout();
  }

  public void setTargetDisplay(int displayId) {
    if (!onMainThread()) {
      // displayId is reassigned below, so the lambda must capture a final copy.
      // Normalization still happens on the main thread, including for this posted call.
      final int requestedDisplayId = displayId;
      mainHandler.post(() -> setTargetDisplay(requestedDisplayId));
      return;
    }
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
    lyricLines = null;
    titleView = null;
    columnView = null;
    handleShowLyric();
  }

  private boolean isShowEnabled() {
    return windowManager != null && rootView != null;
  }

  public void destroyView() {
    if (!onMainThread()) {
      mainHandler.post(this::destroyView);
      return;
    }
    if (rootView == null || windowManager == null) return;
    try {
      windowManager.removeView(rootView);
    } catch (Exception ignored) {}
    rootView = null;
    textView = null;
    lyricLines = null;
    titleView = null;
    columnView = null;
    removeOrientationEvent();
  }

  public void destroy() {
    if (!onMainThread()) {
      mainHandler.post(this::destroy);
      return;
    }
    destroyView();
    windowManager = null;
    layoutParams = null;
  }
}
