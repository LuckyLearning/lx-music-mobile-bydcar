package lx.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Kugou search and the reviewed playback script run in the app process, not in the isolate's network. */
@RunWith(AndroidJUnit4.class)
public class CatalogBrokerTest {
    @Test
    public void kugouSearchIsParsedOrMarkedUpstream() throws Exception {
        String json = bind().searchKg("hello");
        assertFalse(json, json.contains("\"error\":\""));
        assertTrue(json, json.contains("\"status\":"));
        if (json.contains("\"upstream\":true")) {
            assertTrue(json, json.contains("\"tracks\":[]") || json.contains("\"tracks\":["));
            return;
        }
        assertTrue(json, json.contains("\"name\""));
    }

    @Test
    public void reviewedScriptReturnsOnlyThePublicSample() throws Exception {
        IBroker broker = bind();
        String known = "{\"requestKey\":\"p1\",\"data\":{\"source\":\"kg\",\"action\":\"musicUrl\",\"info\":{\"type\":\"128k\",\"musicInfo\":{\"songmid\":\"public-tone\"}}}}";
        String hit = broker.resolveReviewed(known);
        assertTrue(hit, hit.contains("\"admitted\":\"HTTPS\""));
        assertTrue(hit, hit.contains("https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3"));
        assertTrue(hit, hit.contains("script-response status=true"));
        assertFalse(hit.contains("X-Request"));
        String unknown = "{\"requestKey\":\"p2\",\"data\":{\"source\":\"kg\",\"action\":\"musicUrl\",\"info\":{\"type\":\"128k\",\"musicInfo\":{\"songmid\":\"not-reviewed\"}}}}";
        String miss = broker.resolveReviewed(unknown);
        assertTrue(miss, miss.contains("\"admitted\":\"missing\""));
        assertTrue(miss, miss.contains("error=no reviewed url"));
        assertFalse(miss.contains("interactive-examples"));
    }

    @Test
    public void reviewedSampleReachesThePlayer() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicReference<com.google.common.util.concurrent.ListenableFuture<MediaController>> future =
            new AtomicReference<com.google.common.util.concurrent.ListenableFuture<MediaController>>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                SessionToken token = new SessionToken(target, new ComponentName(target, PlaybackService.class));
                future.set(new MediaController.Builder(target, token).buildAsync());
            }
        });
        final MediaController ready = future.get().get(15, TimeUnit.SECONDS);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> failure = new AtomicReference<String>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ready.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY || state == Player.STATE_ENDED) done.countDown();
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        failure.set(error.getErrorCodeName());
                        done.countDown();
                    }
                });
                ready.setMediaItem(MediaItem.fromUri("https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3"));
                ready.prepare();
                ready.play();
            }
        });
        assertTrue("player did not become ready", done.await(20, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ready.release();
            }
        });
        assertTrue(failure.get(), failure.get() == null);
        target.stopService(new Intent(target, PlaybackService.class));
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
