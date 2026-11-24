package lx.compat;

import java.io.BufferedReader;
import java.io.FileReader;

/** Reads only the ABI directory of liblxgate.so. The full install path is not returned. */
public final class AbiProbe {
    private AbiProbe() {}

    public static String loadedLibraryAbi() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/self/maps"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("liblxgate.so")) continue;
                if (line.contains("/arm64-v8a/") || line.contains("/arm64/")) return "arm64-v8a";
                if (line.contains("/armeabi-v7a/")) return "armeabi-v7a";
                if (line.contains("/x86_64/")) return "x86_64";
                if (line.contains("/x86/")) return "x86";
                return "已加载，目录无法识别";
            }
            return "未加载";
        } catch (Exception e) {
            return "读取失败";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
