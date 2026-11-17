package cn.toside.music.mobile.lyric;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;

/**
 * [BYD] 多行歌词块：当前行和后续行放在同一列里，切行时整列一起上下平移。
 *
 * 以前当前行走 TextSwitcher、其它行瞬间换字，两套动画对不齐，再叠加每行
 * updateViewLayout，上下滚动就会一卡一卡。这里用固定行高 + 裁剪，
 * 只在「下一行正好是即将唱到的那句」时做一次位移动画，其它情况直接落位。
 */
public class LyricLinesView extends FrameLayout {
  private final LinearLayout column;
  private final StrokeTextView topIncoming;
  private final StrokeTextView bottomIncoming;
  private final ArrayList<StrokeTextView> slots = new ArrayList<>();

  private int visibleCount = 1;
  private boolean showAnima = true;
  private boolean animating = false;
  private int animGen = 0;
  private int lineNum = Integer.MIN_VALUE;
  private final ArrayList<String> lines = new ArrayList<>();

  private int playedColor = 0xFFFFFFFF;
  private int otherColor = 0x88FFFFFF;
  private int shadowColor = 0x99000000;
  private float textSize = 18f;
  private float otherTextSize = 14f;
  private float letterSpacing = 0f;
  private boolean bold = false;
  private int hollowStyle = 0;
  private float hollowSize = 0f;
  private float textAlpha = 1f;
  private int horizGravity = Gravity.START;
  private int slotHeight = 0;
  private int gapPx;

  LyricLinesView(Context context) {
    super(context);
    setClipChildren(true);
    setClipToPadding(true);
    gapPx = Math.round(2f * context.getResources().getDisplayMetrics().density);

    column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    topIncoming = makeLine();
    bottomIncoming = makeLine();
    topIncoming.setVisibility(GONE);
    bottomIncoming.setVisibility(GONE);
    setVisibleCount(5);
  }

  void setShowAnima(boolean show) {
    showAnima = show;
    if (!show) snapTo(lines, lineNum);
  }

  void setGravity(int gravity) {
    horizGravity = gravity;
    applyGravity(topIncoming);
    applyGravity(bottomIncoming);
    for (StrokeTextView slot : slots) applyGravity(slot);
  }

  void setVisibleCount(int count) {
    int next = Math.max(1, count);
    if (next == visibleCount && slots.size() == next) return;
    cancelAnim();
    visibleCount = next;
    column.removeAllViews();
    slots.clear();
    column.addView(topIncoming);
    for (int i = 0; i < visibleCount; i++) {
      StrokeTextView slot = makeLine();
      slots.add(slot);
      column.addView(slot);
    }
    column.addView(bottomIncoming);
    applyStyles(playedColor, otherColor, shadowColor, textSize, otherTextSize, letterSpacing, bold, hollowStyle, hollowSize, textAlpha);
    bind();
  }

  void applyStyles(
    int playedColor,
    int otherColor,
    int shadowColor,
    float textSize,
    float otherTextSize,
    float letterSpacing,
    boolean bold,
    int hollowStyle,
    float hollowSize,
    float textAlpha
  ) {
    this.playedColor = playedColor;
    this.otherColor = otherColor;
    this.shadowColor = shadowColor;
    this.textSize = textSize;
    this.otherTextSize = otherTextSize;
    this.letterSpacing = letterSpacing;
    this.bold = bold;
    this.hollowStyle = hollowStyle;
    this.hollowSize = hollowSize;
    this.textAlpha = textAlpha;

    topIncoming.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    topIncoming.setTextSize(textSize);
    slotHeight = Math.max(topIncoming.getLineHeight(), 1);
    styleLine(topIncoming, true);
    styleLine(bottomIncoming, false);
    for (int i = 0; i < slots.size(); i++) styleLine(slots.get(i), i == 0);
    requestLayout();
  }

  /** 窗口高度估算：固定行盒，避免每行歌词都把悬浮窗撑高/缩回去。 */
  int getDesiredHeight() {
    int count = Math.max(visibleCount, slots.size());
    int row = slotHeight > 0 ? slotHeight : Math.round(textSize * 1.6f);
    return count * (row + gapPx);
  }

  void setLines(String text, ArrayList<String> extended, int newLine) {
    ArrayList<String> next = new ArrayList<>();
    next.add(text == null ? "" : text);
    if (extended != null && visibleCount > 1) {
      for (String line : extended) {
        if (next.size() >= visibleCount) break;
        if (line == null || line.length() == 0) continue;
        next.add(line);
      }
    }
    while (next.size() < visibleCount) next.add("");

    if (sameLines(next) && newLine == lineNum) return;

    boolean forward = showAnima
      && !animating
      && newLine == lineNum + 1
      && lineNum >= 0
      && lines.size() > 1
      && text != null
      && text.equals(lines.get(1));
    boolean backward = showAnima
      && !animating
      && newLine == lineNum - 1
      && newLine >= 0
      && next.size() > 1
      && !lines.isEmpty()
      && lines.get(0).equals(next.get(1));

    if (forward) {
      animateShift(next, newLine, true);
    } else if (backward) {
      animateShift(next, newLine, false);
    } else {
      snapTo(next, newLine);
    }
  }

  private void animateShift(ArrayList<String> next, int newLine, boolean forward) {
    int distance = rowStep();
    if (distance <= 0) {
      snapTo(next, newLine);
      return;
    }
    animating = true;
    final int gen = ++animGen;
    if (forward) {
      topIncoming.setVisibility(GONE);
      bottomIncoming.setText(next.get(Math.min(visibleCount - 1, next.size() - 1)));
      styleLine(bottomIncoming, false);
      bottomIncoming.setVisibility(VISIBLE);
      column.setTranslationY(0f);
    } else {
      bottomIncoming.setVisibility(GONE);
      topIncoming.setText(next.get(0));
      styleLine(topIncoming, true);
      topIncoming.setVisibility(VISIBLE);
      column.setTranslationY(-distance);
    }
    column.setLayerType(LAYER_TYPE_HARDWARE, null);
    column.animate().cancel();
    column.animate()
      .translationY(forward ? -distance : 0f)
      .setDuration(200)
      .withEndAction(() -> {
        if (gen != animGen) return;
        column.setLayerType(LAYER_TYPE_NONE, null);
        finishShift(next, newLine);
      })
      .start();
  }

  private void finishShift(ArrayList<String> next, int newLine) {
    column.setTranslationY(0f);
    topIncoming.setVisibility(GONE);
    bottomIncoming.setVisibility(GONE);
    animating = false;
    lineNum = newLine;
    lines.clear();
    lines.addAll(next);
    bind();
  }

  private void snapTo(ArrayList<String> next, int newLine) {
    cancelAnim();
    lineNum = newLine;
    // next 可能就是 lines 本身，先拷贝再清空
    ArrayList<String> copy = new ArrayList<>(next);
    lines.clear();
    lines.addAll(copy);
    bind();
  }

  private void cancelAnim() {
    animGen++;
    animating = false;
    column.animate().cancel();
    column.setTranslationY(0f);
    column.setLayerType(LAYER_TYPE_NONE, null);
    topIncoming.setVisibility(GONE);
    bottomIncoming.setVisibility(GONE);
  }

  private void bind() {
    for (int i = 0; i < slots.size(); i++) {
      StrokeTextView slot = slots.get(i);
      String text = i < lines.size() ? lines.get(i) : "";
      if (!text.contentEquals(slot.getText())) slot.setText(text);
      styleLine(slot, i == 0);
      slot.setVisibility(VISIBLE);
    }
  }

  private boolean sameLines(ArrayList<String> next) {
    if (next.size() != lines.size()) return false;
    for (int i = 0; i < next.size(); i++) {
      if (!next.get(i).equals(lines.get(i))) return false;
    }
    return true;
  }

  private int rowStep() {
    if (slots.size() > 1 && slots.get(0).getHeight() > 0 && slots.get(1).getTop() > slots.get(0).getTop()) {
      return slots.get(1).getTop() - slots.get(0).getTop();
    }
    return (slotHeight > 0 ? slotHeight : Math.round(textSize * 1.6f)) + gapPx;
  }

  private StrokeTextView makeLine() {
    StrokeTextView view = new StrokeTextView(getContext());
    view.setSingleLine(true);
    view.setMaxLines(1);
    view.setEllipsize(TextUtils.TruncateAt.END);
    view.setIncludeFontPadding(true);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    view.setLayoutParams(lp);
    applyGravity(view);
    return view;
  }

  private void applyGravity(StrokeTextView view) {
    view.setGravity(horizGravity | Gravity.CENTER_VERTICAL);
  }

  private void styleLine(StrokeTextView view, boolean current) {
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, current ? textSize : otherTextSize);
    view.setTextColor(current ? playedColor : otherColor);
    view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    view.setLetterSpacing(letterSpacing);
    view.setShadowLayer(current ? 1.6f : 1.2f, 1f, 1f, shadowColor);
    LyricStroke.apply(view, hollowStyle, hollowSize, current, textAlpha);
    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) view.getLayoutParams();
    int height = slotHeight > 0 ? slotHeight : view.getLineHeight();
    if (lp.height != height || lp.bottomMargin != gapPx) {
      lp.height = height;
      lp.bottomMargin = gapPx;
      view.setLayoutParams(lp);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    if (widthMode == MeasureSpec.UNSPECIFIED) width = 0;
    int childWidth = MeasureSpec.makeMeasureSpec(Math.max(width, 0), MeasureSpec.EXACTLY);
    column.measure(childWidth, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    // 入场行在裁剪区外，不计入窗口高度，避免动画时把悬浮窗撑高。
    int visible = 0;
    for (StrokeTextView slot : slots) {
      visible += slot.getMeasuredHeight() + gapPx;
    }
    if (visible <= 0) visible = getDesiredHeight();
    setMeasuredDimension(Math.max(width, 0), visible);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int width = right - left;
    column.layout(0, 0, width, column.getMeasuredHeight());
  }

  @Override
  protected void onDetachedFromWindow() {
    cancelAnim();
    super.onDetachedFromWindow();
  }
}
