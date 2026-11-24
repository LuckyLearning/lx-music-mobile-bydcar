package lx.compat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import java.io.File;

/**
 * Isolated runtime. exported=false and isolatedProcess=true are in the manifest.
 * This process does not receive a policy object and cannot open the broker's allowlist.
 */
public class SourceRuntimeService extends Service implements GateNative.NativeCallback {
    private int generation = 1;
    private int droppedLate = 0;
    private volatile boolean running = false;
    private long session;
    private IScriptHost parent;

    @Override
    public String rpc(String message) {
        IScriptHost host = parent;
        if (host == null) return "{\"ok\":false}";
        try {
            return host.handle(message);
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    private final ISourceRuntime.Stub binder = new ISourceRuntime.Stub() {
        @Override
        public void setHost(IScriptHost host) {
            parent = host;
            if (session != 0) {
                GateNative.sessionClose(session);
                session = 0;
            }
            session = GateNative.sessionOpen(SourceRuntimeService.this);
        }

        @Override
        public String command(String commandJson) {
            if (session == 0) return "{\"op\":\"result\",\"id\":0,\"ok\":false,\"error\":\"rejected\"}";
            running = true;
            Thread killer = startKiller(12000);
            try {
                return GateNative.sessionCommand(session, commandJson);
            } finally {
                running = false;
                killer.interrupt();
            }
        }

        @Override
        public int eval(int gen, String source, int memoryBytes, int cpuMs) {
            if (gen != generation) {
                droppedLate++;
                return 73;
            }
            return run(source, memoryBytes, cpuMs, false);
        }

        @Override
        public String report(int gen, String source, int memoryBytes, int cpuMs) {
            if (gen != generation) {
                droppedLate++;
                return "73";
            }
            running = true;
            Thread killer = startKiller(cpuMs);
            try {
                return GateNative.evalReport(source, memoryBytes, cpuMs);
            } finally {
                running = false;
                killer.interrupt();
            }
        }

        @Override
        public int pid() {
            return Process.myPid();
        }

        @Override
        public String loadedAbi() {
            return AbiProbe.loadedLibraryAbi();
        }

        @Override
        public boolean privateFilesReadable() {
            return new File("/data/data/cn.toside.music.mobile.nativegate.test").canRead();
        }

        @Override
        public int generation() {
            return generation;
        }

        @Override
        public void bumpGeneration() {
            generation++;
        }

        @Override
        public void selfKill() {
            Process.killProcess(Process.myPid());
        }

        @Override
        public int droppedLate() {
            return droppedLate;
        }
    };

    private int run(String source, int memoryBytes, int cpuMs, boolean unused) {
        running = true;
        Thread killer = startKiller(cpuMs);
        try {
            return GateNative.evalSynthetic(source, memoryBytes, cpuMs);
        } finally {
            running = false;
            killer.interrupt();
        }
    }

    private Thread startKiller(final int cpuMs) {
        Thread killer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(cpuMs + 1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                if (running) Process.killProcess(Process.myPid());
            }
        });
        killer.setDaemon(true);
        killer.start();
        return killer;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (session != 0) {
            GateNative.sessionClose(session);
            session = 0;
        }
        super.onDestroy();
    }
}
