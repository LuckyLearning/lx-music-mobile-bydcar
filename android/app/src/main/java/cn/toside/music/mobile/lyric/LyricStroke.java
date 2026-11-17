package cn.toside.music.mobile.lyric;

import android.graphics.Color;
import android.view.View;

/**
 * [BYD] 当前行 / 其它行共用的空心字参数。
 * 描边色再乘一次文字透明度，避免整窗 setAlpha 把背景一起淡掉。
 */
final class LyricStroke {
  private static final int STYLE1_COLOR = Color.argb(230, 255, 255, 255);
  private static final int STYLE2_COLOR = Color.argb(230, 0, 0, 0);

  private LyricStroke() {}

  static int withAlpha(int color, float alpha) {
    int baseA = (color >>> 24) & 0xFF;
    int a = Math.max(0, Math.min(255, Math.round(baseA * alpha)));
    return (color & 0x00FFFFFF) | (a << 24);
  }

  static void apply(View view, int hollowStyle, float hollowSize, boolean isCurrent, float alpha) {
    if (view == null) return;
    float size = Math.max(0f, hollowSize) * (isCurrent ? 1f : 0.75f);
    int strokeColor = Color.TRANSPARENT;
    float strokeWidth = 0f;
    boolean hollow = false;
    if (hollowStyle > 0 && size > 0f) {
      hollow = true;
      if (hollowStyle == 1) {
        strokeColor = withAlpha(STYLE1_COLOR, alpha);
        strokeWidth = Math.max(1f, size * 0.7f);
      } else {
        strokeColor = withAlpha(STYLE2_COLOR, alpha);
        strokeWidth = Math.max(1.5f, size * 1.2f);
      }
    }
    if (view instanceof LyricSwitchView) {
      ((LyricSwitchView) view).setStrokeStyle(strokeColor, strokeWidth, hollow);
    } else if (view instanceof LyricTextView) {
      ((LyricTextView) view).setStroke(strokeColor, strokeWidth, hollow);
    } else if (view instanceof StrokeTextView) {
      ((StrokeTextView) view).setStrokeStyle(strokeColor, strokeWidth, hollow);
    }
  }
}
