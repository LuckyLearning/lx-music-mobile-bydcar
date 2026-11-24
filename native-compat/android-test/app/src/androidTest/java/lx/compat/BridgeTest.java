package lx.compat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BridgeTest {
    @Test
    public void isolatedProcessRunsPreloadBridge() throws Exception {
        IBroker broker = bind();
        String script = "var md5=lx.utils.crypto.md5('a');"
            + "lx.on('request', function(){ return Promise.resolve('https://example.com/a.mp3'); })"
            + ".then(function(){ return lx.send('inited', {sources:{kw:{type:'music', actions:['musicUrl'], qualitys:['128k']}}}); });"
            + "setTimeout(function(){}, 0);"
            + "lx.request('https://127.0.0.1/x', {timeout:1000}, function(err){});";
        String request = "{\"requestKey\":\"t1\",\"data\":{\"source\":\"kw\",\"action\":\"musicUrl\",\"info\":{\"type\":\"128k\",\"musicInfo\":{\"songmid\":\"1\"}}}}";
        String trace = broker.runScript(script, request);
        assertTrue(trace, trace.contains("init status=true sources=kw"));
        assertTrue(trace, trace.contains("crypto md5 len=32"));
        assertTrue(trace, trace.contains("timer "));
        assertTrue(trace, trace.contains("http-rejected"));
        assertTrue(trace, trace.contains("Non-public"));
        assertTrue(trace, trace.contains("script-response status=true"));
    }

    @Test
    public void reviewedSampleStaysOnMediatedHttps() throws Exception {
        File sample = new File(InstrumentationRegistry.getInstrumentation().getContext().getFilesDir(), "sample.js");
        assertTrue("stage sample.js with the host script before this test", sample.isFile());
        String script = read(sample);
        IBroker broker = bind();
        String request = "{\"requestKey\":\"s1\",\"data\":{\"source\":\"kw\",\"action\":\"musicUrl\",\"info\":{\"type\":\"128k\",\"musicInfo\":{\"hash\":\"0\",\"songmid\":\"0\"}}}}";
        String trace = broker.runScript(script, request);
        File out = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), "sample-trace.txt");
        java.io.FileOutputStream stream = new java.io.FileOutputStream(out);
        stream.write(trace.getBytes(StandardCharsets.UTF_8));
        stream.close();
        assertTrue(trace, trace.contains("init status=true"));
        assertTrue(trace, trace.contains("sources=kw,kg,tx,wy,mg") || trace.contains("sources=kw"));
        assertFalse(trace.contains("X-Request"));
        assertFalse(trace.contains("http-rejected"));
        assertTrue(trace, trace.contains("host=lxmusicapi.onrender.com"));
    }

    private static String read(File file) throws Exception {
        StringBuilder text = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) text.append(line).append('\n');
        reader.close();
        return text.toString();
    }

    private static IBroker bind() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IBroker> ref = new AtomicReference<IBroker>();
        context.bindService(new Intent(context, BrokerService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ref.set(IBroker.Stub.asInterface(service));
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                ref.set(null);
            }
        }, Context.BIND_AUTO_CREATE);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        return ref.get();
    }
}
