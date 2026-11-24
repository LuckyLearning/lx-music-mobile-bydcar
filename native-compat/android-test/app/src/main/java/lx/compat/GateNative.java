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

    public interface NativeCallback {
        String rpc(String message);
    }

    public static native long sessionOpen(NativeCallback callback);

    public static native String sessionCommand(long handle, String commandJson);

    public static native void sessionClose(long handle);
}
