package cn.toside.music.mobile.lyric;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.skydoves.colorpickerview.AlphaTileView;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

import java.util.Locale;

/**
 * [BYD] 悬浮窗颜色用 Skydoves ColorPickerView 对话框选，不手填色值。
 * 色相环是库的默认 HSV 调色板，亮度条始终在，透明度条只在已存颜色带 alpha 时挂上。
 * 取消、点外面、或打不开对话框都 resolve null，调用方继续用上一次有效颜色。
 */
public class ColorPickerModule extends ReactContextBaseJavaModule {
  private Promise pending;
  private AlertDialog showing;

  ColorPickerModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "ColorPickerModule";
  }

  @ReactMethod
  public void pick(String color, boolean withAlpha, String title, String confirmLabel, String cancelLabel, Promise promise) {
    final Activity activity = getCurrentActivity();
    if (activity == null || activity.isFinishing()) {
      promise.resolve(null);
      return;
    }
    final String initialText = color == null ? "" : color;
    final boolean attachAlpha = withAlpha;
    final String dialogTitle = title == null ? "" : title;
    final String confirmText = (confirmLabel == null || confirmLabel.length() == 0) ? "OK" : confirmLabel;
    final String cancelText = (cancelLabel == null || cancelLabel.length() == 0) ? "Cancel" : cancelLabel;
    activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (activity.isFinishing()) {
          promise.resolve(null);
          return;
        }
        showPicker(activity, initialText, attachAlpha, dialogTitle, confirmText, cancelText, promise);
      }
    });
  }

  private void showPicker(
      final Activity activity,
      final String initialText,
      final boolean attachAlpha,
      final String dialogTitle,
      final String confirmText,
      final String cancelText,
      final Promise promise) {
    if (showing != null && showing.isShowing()) {
      showing.dismiss();
    }
    pending = promise;
    final int initialColor = LyricView.parseColor(initialText);
    // 只有手指动过色相环或滑条才写回。只打开再点确认时不改已保存的字符串。
    final boolean[] moved = new boolean[] { false };

    final AlphaTileView preview = new AlphaTileView(activity);
    preview.setPaintColor(initialColor);
    final TextView hexView = new TextView(activity);
    hexView.setText(toCssHex(initialColor));
    hexView.setTextSize(16);
    hexView.setFocusable(false);
    hexView.setFocusableInTouchMode(false);
    hexView.setTextIsSelectable(false);

    LinearLayout header = new LinearLayout(activity);
    header.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(activity, 16);
    header.setPadding(pad, pad, pad, dp(activity, 8));

    if (dialogTitle.length() > 0) {
      TextView titleView = new TextView(activity);
      titleView.setText(dialogTitle);
      titleView.setTextSize(18);
      titleView.setPadding(0, 0, 0, dp(activity, 10));
      header.addView(titleView);
    }

    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48));
    previewParams.rightMargin = dp(activity, 12);
    row.addView(preview, previewParams);
    row.addView(hexView, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT));
    header.addView(row);

    try {
      ColorPickerDialog.Builder builder = new ColorPickerDialog.Builder(activity);
      builder.setCustomTitle(header);
      builder.attachBrightnessSlideBar(true);
      builder.attachAlphaSlideBar(attachAlpha);
      builder.setBottomSpace(12);
      builder.setCancelable(true);
      builder.setPositiveButton(confirmText, new ColorEnvelopeListener() {
        @Override
        public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
          if (!moved[0] || envelope == null) {
            settle(null);
            return;
          }
          settle(toCssHex(envelope.getColor()));
        }
      });
      builder.setNegativeButton(cancelText, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int which) {
          dialogInterface.dismiss();
        }
      });
      builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialogInterface) {
          if (showing == dialogInterface) showing = null;
          settle(null);
        }
      });

      final ColorPickerView picker = builder.getColorPickerView();
      picker.setColorListener(new ColorEnvelopeListener() {
        @Override
        public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
          if (envelope == null) return;
          if (fromUser) moved[0] = true;
          int selected = envelope.getColor();
          preview.setPaintColor(selected);
          hexView.setText(toCssHex(selected));
        }
      });

      AlertDialog dialog = builder.create();
      // 挂到窗口之后再听布局，否则 ViewTreeObserver 会在 attach 时被换掉。
      dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        @Override
        public void onShow(DialogInterface dialogInterface) {
          // 布局完成后把色相环和滑条对准当前颜色。post 再补一次，避免第一次布局已经走过。
          picker.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean applied = false;

            @Override
            public void onGlobalLayout() {
              if (applied || picker.getWidth() <= 0 || picker.getHeight() <= 0) return;
              applied = true;
              ViewTreeObserver observer = picker.getViewTreeObserver();
              if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
              applyInitialColor(picker, initialColor);
            }
          });
          picker.post(new Runnable() {
            @Override
            public void run() {
              applyInitialColor(picker, initialColor);
            }
          });
        }
      });
      showing = dialog;
      dialog.show();
    } catch (RuntimeException error) {
      showing = null;
      settle(null);
    }
  }

  /** 不设 preferenceName，否则库会用上次记住的颜色盖掉当前设置。 */
  private static void applyInitialColor(ColorPickerView picker, int color) {
    if (picker.getWidth() <= 0 || picker.getHeight() <= 0) return;
    picker.setInitialColor(color);
  }

  /** 先到先写。确认写入色值后，随后的 dismiss 再进来就是空操作。 */
  private void settle(String hex) {
    Promise promise = pending;
    pending = null;
    if (promise != null) promise.resolve(hex);
  }

  /** Android AARRGGBB 转成设置里用的 CSS #RRGGBBAA。 */
  static String toCssHex(int color) {
    return String.format(
        Locale.US,
        "#%02x%02x%02x%02x",
        Color.red(color),
        Color.green(color),
        Color.blue(color),
        Color.alpha(color));
  }

  private static int dp(Activity activity, int value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }
}
