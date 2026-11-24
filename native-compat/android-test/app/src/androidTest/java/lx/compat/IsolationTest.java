package lx.compat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class IsolationTest {
    @Test
    public void serviceIsNotExportedAndIsIsolated() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ServiceInfo info = context.getPackageManager().getServiceInfo(
            new ComponentName(context, SourceRuntimeService.class), PackageManager.GET_META_DATA);
        assertFalse(info.exported);
        assertTrue((info.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0);
        assertEquals("cn.toside.music.mobile.nativegate.test", context.getPackageName());
    }

    @Test
    public void brokerIgnoresScriptPolicy() {
        HostBroker broker = new HostBroker(Collections.singleton("legacy.example"));
        assertEquals("rejected-cleartext", broker.admitScriptRequest("http://example.com/a", "allow-all"));
        assertEquals("scoped-cleartext", broker.admitScriptRequest("http://legacy.example/a", "deny"));
        assertEquals("rejected-private", broker.admitScriptRequest("https://127.0.0.1/a", "allow-all"));
        assertEquals("https", broker.admitScriptRequest("https://example.com/a", "http"));
        int gen = broker.generation();
        assertTrue(broker.acceptResult(gen));
        broker.bump();
        assertFalse(broker.acceptResult(gen));
        assertEquals(1, broker.dropped());
    }

    @Test
    public void isolatedProbeHasNoHostGlobalsAndCannotReadAppFiles() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ISourceRuntime> ref = new AtomicReference<ISourceRuntime>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ref.set(ISourceRuntime.Stub.asInterface(service));
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                ref.set(null);
            }
        };
        context.bindService(new Intent(context, SourceRuntimeService.class), connection, Context.BIND_AUTO_CREATE);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        ISourceRuntime runtime = ref.get();
        try {
            int gen = runtime.generation();
            String report = runtime.report(gen, "String(typeof fetch)+','+String(typeof require)", 8 * 1024 * 1024, 800);
            assertEquals("0\nundefined,undefined", report);
            assertFalse(runtime.privateFilesReadable());
            assertEquals(73, runtime.eval(gen + 9, "1+1", 8 * 1024 * 1024, 500));
            assertTrue(runtime.droppedLate() >= 1);
            int loop = runtime.eval(gen, "while(true){}", 8 * 1024 * 1024, 400);
            assertEquals(70, loop);
        } finally {
            context.unbindService(connection);
        }
    }

    @Test
    public void processDeathDropsTheBinder() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch died = new CountDownLatch(1);
        final AtomicReference<ISourceRuntime> ref = new AtomicReference<ISourceRuntime>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ref.set(ISourceRuntime.Stub.asInterface(service));
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                died.countDown();
            }
        };
        context.bindService(new Intent(context, SourceRuntimeService.class), connection, Context.BIND_AUTO_CREATE);
        assertTrue(connected.await(10, TimeUnit.SECONDS));
        int firstPid = ref.get().pid();
        try {
            ref.get().selfKill();
        } catch (android.os.DeadObjectException expected) {
            // The isolated process exits inside this transaction, so the reply never returns.
        }
        assertTrue(died.await(10, TimeUnit.SECONDS));
        context.unbindService(connection);
        final CountDownLatch again = new CountDownLatch(1);
        final AtomicReference<ISourceRuntime> next = new AtomicReference<ISourceRuntime>();
        ServiceConnection restart = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                next.set(ISourceRuntime.Stub.asInterface(service));
                again.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        context.bindService(new Intent(context, SourceRuntimeService.class), restart, Context.BIND_AUTO_CREATE);
        assertTrue(again.await(10, TimeUnit.SECONDS));
        try {
            assertTrue(next.get().pid() != firstPid);
            HostBroker broker = new HostBroker(Collections.<String>emptySet());
            broker.bump();
            assertFalse(broker.acceptResult(1));
        } finally {
            context.unbindService(restart);
        }
    }
}
