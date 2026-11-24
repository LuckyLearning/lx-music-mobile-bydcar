package lx.compat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.Collections;

/**
 * Diagnostic page only. It does not play audio and it is not a source-compatibility result.
 */
public class DiagnosticActivity extends Activity {
    private TextView report;
    private final HostBroker broker = new HostBroker(Collections.<String>emptySet());
    private ISourceRuntime runtime;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            runtime = ISourceRuntime.Stub.asInterface(service);
            render();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runtime = null;
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostic);
        report = (TextView) findViewById(R.id.report);
        Button button = (Button) findViewById(R.id.run_probe);
        button.setText("重新检测");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                render();
            }
        });
        render();
        bindService(new Intent(this, SourceRuntimeService.class), connection, Context.BIND_AUTO_CREATE);
    }

    private void render() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        StringBuilder text = new StringBuilder();
        text.append("诊断页。不是播放器，不能代表音源兼容或沙箱通过。\n\n");
        text.append("应用 ID：cn.toside.music.mobile.nativegate.test\n");
        text.append("系统 SDK_INT：").append(Build.VERSION.SDK_INT).append('\n');
        text.append("设备 ABI：").append(supportedAbis()).append('\n');
        text.append("界面进程已加载库：").append(passFail(!"未加载".equals(AbiProbe.loadedLibraryAbi()) && !"读取失败".equals(AbiProbe.loadedLibraryAbi()), AbiProbe.loadedLibraryAbi())).append('\n');
        text.append("编译进库的 API 标记：").append(compileMarker()).append("（编译标记，不是系统版本）\n");
        text.append("当前窗口：").append(windowName(WindowProfile.classify(metrics.widthPixels, metrics.heightPixels, metrics.density))).append('\n');
        text.append("计划分类 全屏/70%/30%：")
            .append(windowName(WindowProfile.classify(1920, 984, 1.5f))).append(" / ")
            .append(windowName(WindowProfile.classify(1344, 984, 1.5f))).append(" / ")
            .append(windowName(WindowProfile.classify(576, 984, 1.5f))).append('\n');
        String http = broker.admitScriptRequest("http://example.com/a", "allow-all");
        String loop = broker.admitScriptRequest("https://127.0.0.1/", "allow-all");
        String https = broker.admitScriptRequest("https://example.com/", "deny");
        text.append("脚本明文请求：").append(passFail("rejected-cleartext".equals(http), "已拒绝明文")).append('\n');
        text.append("回环地址：").append(passFail("rejected-private".equals(loop), "已拒绝私网")).append('\n');
        text.append("https 准入：").append(passFail("https".equals(https), "允许公网 https")).append('\n');
        text.append("隔离进程：").append(runtime == null ? "失败（未连接）" : "通过（已连接）").append('\n');
        text.append("隔离探针：").append(probeLine()).append('\n');
        text.append("隔离进程已加载库：").append(isolatedAbi()).append('\n');
        report.setText(text);
    }

    private String probeLine() {
        if (runtime == null) return "失败（服务未连接）";
        try {
            int gen = runtime.generation();
            String value = runtime.report(gen, "String(typeof fetch)+','+String(typeof require)", 8 * 1024 * 1024, 500);
            boolean pass = value != null && value.startsWith("0") && value.contains("undefined,undefined");
            return passFail(pass, "无 fetch/require");
        } catch (Exception e) {
            return "失败（" + e.getClass().getSimpleName() + "）";
        }
    }

    private String isolatedAbi() {
        if (runtime == null) return "未连接";
        try {
            return runtime.loadedAbi();
        } catch (Exception e) {
            return "读取失败";
        }
    }

    private static String compileMarker() {
        try {
            return String.valueOf(GateNative.nativeApiLevel());
        } catch (Throwable e) {
            return "未加载";
        }
    }

    private static String supportedAbis() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
            if (i > 0) out.append(", ");
            out.append(Build.SUPPORTED_ABIS[i]);
        }
        return out.toString();
    }

    private static String windowName(String code) {
        if ("phone-portrait".equals(code)) return "手机竖屏";
        if ("phone-large".equals(code)) return "手机大屏";
        if ("car-full".equals(code)) return "车机全屏（计划）";
        if ("car-70".equals(code)) return "车机 70%（计划）";
        if ("car-30".equals(code)) return "车机 30%（计划）";
        return "未知";
    }

    private static String passFail(boolean pass, String detail) {
        return (pass ? "通过" : "失败") + "（" + detail + "）";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }
}
