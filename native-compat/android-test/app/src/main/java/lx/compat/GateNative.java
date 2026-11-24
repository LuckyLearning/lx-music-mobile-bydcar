package lx.compat;

/** Loads liblxgate. Callers are the isolated service only. */
public final class GateNative {
    static {
        System.loadLibrary("lxgate");
    }

    private GateNative() {}

    public static native int nativeApiLevel();

    public static native int evalSynthetic(String source, int memoryBytes, int cpuMs);

    public static native String evalReport(String source, int memoryBytes, int cpuMs);
}
