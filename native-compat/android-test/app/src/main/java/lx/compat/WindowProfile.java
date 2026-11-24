package lx.compat;

/** Same thresholds as native-compat WindowProfile.kt. Planned sizes, not a car measurement. */
public final class WindowProfile {
    private WindowProfile() {}

    public static String classify(int widthPx, int heightPx, float density) {
        if (density <= 0f || widthPx <= 0 || heightPx <= 0) return "unknown";
        float widthDp = widthPx / density;
        float heightDp = heightPx / density;
        if (heightDp >= 600f && heightDp <= 720f) {
            if (widthDp >= 1100f) return "car-full";
            if (widthDp >= 640f) return "car-70";
            return "car-30";
        }
        if (widthDp <= heightDp && widthDp < 600f) return "phone-portrait";
        return "phone-large";
    }
}
