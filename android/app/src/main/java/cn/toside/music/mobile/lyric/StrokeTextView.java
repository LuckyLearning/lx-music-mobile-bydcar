package cn.toside.music.mobile.lyric;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.TextPaint;
import android.widget.TextView;

/**
 * [BYD] 多行歌词的描边 / 空心字支持。
 *
 * TextView 原生不带描边能力，这里用「8 向偏移多次绘制」模拟 outline：
 * 先把文字按描边色向 8 个方向各偏移 strokeWidth 画一遍（叠出轮廓），
 * 再画正常填充；空心（电影字幕风格）模式下填充层透明，只留轮廓。
 * 描边偏移绘制期间临时清掉阴影层，避免每圈偏移都叠一遍阴影。
 *
 * 单行模式用的是 LyricTextView（真 Stroke 画笔），两者效果一致。
 */
@SuppressLint("AppCompatCustomView")
public class StrokeTextView extends TextView {
  // 8 向偏移系数（对角线为 1/√2，保证轮廓宽度均匀）
  private static final float[][] OFFSETS = {
    {1f, 0f}, {-1f, 0f}, {0f, 1f}, {0f, -1f},
    {0.7071f, 0.7071f}, {0.7071f, -0.7071f}, {-0.7071f, 0.7071f}, {-0.7071f, -0.7071f},
  };

  private int strokeColor = Color.BLACK;
  private float strokeWidth = 0f;
  private boolean hollow = false;
  // 阴影参数记忆：描边偏移绘制时需要临时关掉阴影
  private float shadowRadius = 0f;
  private float shadowDx = 0f;
  private float shadowDy = 0f;
  private int shadowColor = 0;

  public StrokeTextView(Context context) {
    super(context);
  }

  public void setStrokeStyle(int color, float width, boolean isHollow) {
    this.strokeColor = (color >>> 24) == 0xFF ? (color & 0xFEFFFFFF) : color;
    this.strokeWidth = width;
    this.hollow = isHollow;
    postInvalidate();
  }

  @Override
  public void setShadowLayer(float radius, float dx, float dy, int color) {
    super.setShadowLayer(radius, dx, dy, color);
    shadowRadius = radius;
    shadowDx = dx;
    shadowDy = dy;
    shadowColor = color;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (strokeWidth > 0f) {
      TextPaint paint = getPaint();
      int fillColor = paint.getColor();
      paint.setShadowLayer(0f, 0f, 0f, 0);
      paint.setColor(strokeColor);
      for (float[] offset : OFFSETS) {
        canvas.save();
        canvas.translate(offset[0] * strokeWidth, offset[1] * strokeWidth);
        super.onDraw(canvas);
        canvas.restore();
      }
      paint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor);
      if (hollow) {
        // 空心字：填充层透明，只留描边轮廓
        paint.setColor(Color.TRANSPARENT);
        super.onDraw(canvas);
        paint.setColor(fillColor);
      } else {
        paint.setColor(fillColor);
        super.onDraw(canvas);
      }
    } else {
      super.onDraw(canvas);
    }
  }
}
