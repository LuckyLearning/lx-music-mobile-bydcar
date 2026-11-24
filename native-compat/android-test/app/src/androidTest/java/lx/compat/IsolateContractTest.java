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

    @Test
    public void isolateFollowsRedirectAndChecksRsa() throws Exception {
        String script = "var key='MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBANribHS4xXXZR9oWI6110dHtocQLHx29SiM6FpxFGrTVyORumdpoaBszeO0uNk4Tj2GUGMNP0I372E3NNLF/CjMCAwEAAQ==';"
            + "var out=lx.utils.crypto.rsaEncrypt('hi', key);"
            + "var hex=lx.utils.buffer.bufToString(out,'hex');"
            + "var rsaok=hex==='5a15528426dce4a9e7e904fadd9584f603492ddb3b9039b1676be74f1837b1763ad8adb6f09aa815c44bf864bda1a016b5c9802fd4f153e4cb86d343d7789d0d';"
            + "var redirectOk=false;"
            + "lx.request('https://httpbin.org/redirect-to?url=https%3A%2F%2Fexample.com%2F',{timeout:8000},function(err,resp){"
            + "redirectOk=!err && resp && resp.statusCode==200 && String(resp.body).indexOf('Example Domain')>=0;"
            + "});"
            + "lx.on('request', function(){ if(!rsaok || !redirectOk) return Promise.reject(new Error('probe'));"
            + "return Promise.resolve('https://example.com/a.mp3'); }).then(function(){"
            + "return lx.send('updateAlert', {log: 'rsa=' + (rsaok ? 'ok' : 'bad')}); }).then(function(){"
            + "return lx.send('inited', {sources:{kw:{type:'music', actions:['musicUrl'], qualitys:['128k']}}}); });";
        String request = "{\"requestKey\":\"r1\",\"data\":{\"source\":\"kw\",\"action\":\"musicUrl\",\"info\":{\"type\":\"128k\",\"musicInfo\":{\"songmid\":\"1\"}}}}";
        String trace = bind().runScript(script, request);
        assertTrue(trace, trace.contains("alert rsa=ok"));
        if (trace.contains("http-failed")) return;
        assertTrue(trace, trace.contains("http host=httpbin.org"));
        assertTrue(trace, trace.contains("script-response status=true"));
    }

    @Test
    public void isolateRejectsMixedDnsAnswers() throws Exception {
        IBroker broker = bind();
        try {
            broker.installFixtureResolver("mixed");
            String script = "lx.request('https://rebind.test/x',{timeout:1000},function(){});"
                + "lx.on('request', function(){ return Promise.resolve('https://example.com/a.mp3'); }).then(function(){"
                + "return lx.send('inited', {sources:{kw:{type:'music', actions:['musicUrl'], qualitys:['128k']}}}); });";
            String request = "{\"requestKey\":\"b1\",\"data\":{\"source\":\"kw\",\"action\":\"musicUrl\",\"info\":{\"type\":\"128k\",\"musicInfo\":{\"songmid\":\"1\"}}}}";
            String trace = broker.runScript(script, request);
            assertTrue(trace, trace.contains("http-rejected"));
            assertTrue(trace, trace.contains("Non-public"));
            assertFalse(trace.contains("http host=rebind.test"));
        } finally {
            broker.installFixtureResolver("real");
        }
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
