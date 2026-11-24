package lx.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Contract cases that previously ran only on the Linux runner, now inside the isolated process. */
@RunWith(AndroidJUnit4.class)
public class IsolateContractTest {
    @Test
    public void isolateChecksCryptoCancelAndLyricLimit() throws Exception {
        String script = "var md5ok = lx.utils.crypto.md5('a') === '0cc175b9c0f1b6a831c399e269772661';"
            + "var aes = lx.utils.crypto.aesEncrypt('hello', 'aes-128-cbc', [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16], [3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18]);"
            + "var hex = lx.utils.buffer.bufToString(aes, 'hex');"
            + "var aesok = hex === '9f50960af5e4b36afb5a7ec086e712a8';"
            + "lx.request('https://example.com/form', {formData:{a:'b'}, timeout:1000}, function(){});"
            + "var cancel = lx.request('https://example.com/cancel', {timeout:1000}, function(){});"
            + "var threw = false; try { cancel(); cancel(); } catch (e) { threw = true; }"
            + "var lyric = new Array(51202).join('a');"
            + "lx.on('request', function(){ return Promise.resolve({lyric: lyric}); }).then(function(){"
            + "return lx.send('updateAlert', {log: 'md5=' + (md5ok ? 'ok' : 'bad') + ';aes=' + (aesok ? 'ok' : 'bad') + ';cancel2=' + (threw ? 'ok' : 'bad')});"
            + "}).then(function(){ return lx.send('inited', {sources:{local:{type:'music', actions:['musicUrl','lyric'], qualitys:[]}}}); });";
        String request = "{\"requestKey\":\"l1\",\"data\":{\"source\":\"local\",\"action\":\"lyric\",\"info\":{}}}";
        String trace = bind().runScript(script, request);
        assertTrue(trace, trace.contains("alert md5=ok;aes=ok;cancel2=ok"));
        assertTrue(trace, trace.contains("multipart formData is not supported"));
        assertTrue(trace, trace.contains("cancel"));
        assertTrue(trace, trace.contains("script-response status=false error=failed"));
        assertTrue(trace, trace.contains("init status=true"));
        assertFalse(trace.contains("http host="));
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
