package lx.compat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.Collections;

/** Diagnostic screen only. It is not the phone or car product UI. */
public class DiagnosticActivity extends Activity {
    private TextView report;
    private final HostBroker broker = new HostBroker(Collections.<String>emptySet());
    private ISourceRuntime runtime;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            runtime = ISourceRuntime.Stub.asInterface(service);
            append("service connected\n");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runtime = null;
            append("service died\n");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostic);
        report = (TextView) findViewById(R.id.report);
        Button button = (Button) findViewById(R.id.run_probe);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runProbe();
            }
        });
        showStatic();
        bindService(new Intent(this, SourceRuntimeService.class), connection, Context.BIND_AUTO_CREATE);
    }

    private void showStatic() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        StringBuilder text = new StringBuilder();
        text.append("applicationId=cn.toside.music.mobile.nativegate.test\n");
        text.append("window=").append(WindowProfile.classify(width, height, metrics.density)).append('\n');
        text.append("planned full=").append(WindowProfile.classify(1920, 984, 1.5f)).append('\n');
        text.append("planned 70=").append(WindowProfile.classify(1344, 984, 1.5f)).append('\n');
        text.append("planned 30=").append(WindowProfile.classify(576, 984, 1.5f)).append('\n');
        text.append("script http=").append(broker.admitScriptRequest("http://example.com/a", "allow-all")).append('\n');
        text.append("script loopback=").append(broker.admitScriptRequest("https://127.0.0.1/", "allow-all")).append('\n');
        text.append("script https=").append(broker.admitScriptRequest("https://example.com/", "deny")).append('\n');
        report.setText(text);
    }

    private void runProbe() {
        if (runtime == null) {
            append("runtime not connected\n");
            return;
        }
        try {
            int gen = runtime.generation();
            String value = runtime.report(gen, "String(typeof fetch)+','+String(typeof require)", 8 * 1024 * 1024, 500);
            append("probe " + value + " api=" + GateNative.nativeApiLevel() + "\n");
        } catch (Exception e) {
            append("probe failed " + e.getClass().getSimpleName() + "\n");
        }
    }

    private void append(String line) {
        report.append(line);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }
}
