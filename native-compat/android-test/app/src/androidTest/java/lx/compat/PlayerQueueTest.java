package lx.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The playback service keeps the queue after a controller disconnects. */
@RunWith(AndroidJUnit4.class)
public class PlayerQueueTest {
    @After
    public void stopPlayback() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.stopService(new Intent(target, PlaybackService.class));
    }

    @Test
    public void queueSurvivesControllerReconnect() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.getSharedPreferences(ClientPrefs.FILE, Context.MODE_PRIVATE).edit().clear().commit();
        MediaController first = connect(target);
        onMain(new Runnable() {
            @Override
            public void run() {
                first.setMediaItem(new MediaItem.Builder().setMediaId("tone").setUri("asset:///tone.wav").build());
                first.prepare();
                first.pause();
            }
        });
        final int[] count = new int[1];
        final boolean[] playing = new boolean[1];
        onMain(new Runnable() {
            @Override
            public void run() {
                count[0] = first.getMediaItemCount();
                playing[0] = first.isPlaying();
                first.release();
            }
        });
        assertEquals(1, count[0]);
        assertFalse(playing[0]);

        MediaController second = connect(target);
        final String[] id = new String[1];
        onMain(new Runnable() {
            @Override
            public void run() {
                count[0] = second.getMediaItemCount();
                id[0] = second.getMediaItemAt(0).mediaId;
                playing[0] = second.isPlaying();
                second.release();
            }
        });
        assertEquals(1, count[0]);
        assertEquals("tone", id[0]);
        assertFalse(playing[0]);
    }

    @Test
    public void bootWithoutAutoplayDoesNotStartAudio() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.getSharedPreferences(ClientPrefs.FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(ClientPrefs.BOOT, true)
            .putBoolean(ClientPrefs.AUTOPLAY, false)
            .commit();
        Intent start = new Intent(target, PlaybackService.class).putExtra(PlaybackService.EXTRA_FROM_BOOT, true);
        target.startService(start);
        Thread.sleep(500);
        MediaController controller = connect(target);
        final boolean[] playing = new boolean[1];
        final int[] state = new int[1];
        onMain(new Runnable() {
            @Override
            public void run() {
                playing[0] = controller.isPlaying();
                state[0] = controller.getPlaybackState();
                controller.release();
            }
        });
        assertFalse(playing[0]);
        assertTrue(state[0] == Player.STATE_IDLE || state[0] == Player.STATE_READY);
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static MediaController connect(Context target) throws Exception {
        AtomicReference<ListenableFuture<MediaController>> future = new AtomicReference<ListenableFuture<MediaController>>();
        CountDownLatch started = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                SessionToken token = new SessionToken(target, new ComponentName(target, PlaybackService.class));
                future.set(new MediaController.Builder(target, token).buildAsync());
                started.countDown();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        return future.get().get(15, TimeUnit.SECONDS);
    }
}
