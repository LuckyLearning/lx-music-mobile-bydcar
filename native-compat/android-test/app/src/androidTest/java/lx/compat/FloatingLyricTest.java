package lx.compat;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Shows the overlay from the test app process. API 21 can use the older phone window type. */
@RunWith(AndroidJUnit4.class)
public class FloatingLyricTest {
    @Test
    public void overlayShowsTheCurrentLine() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final boolean[] shown = new boolean[1];
        final String[] failure = new String[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                FloatingLyric lyric = new FloatingLyric(target);
                try {
                    lyric.show("歌词测试");
                    shown[0] = lyric.visible();
                    lyric.hide();
                } catch (RuntimeException error) {
                    failure[0] = error.getClass().getSimpleName();
                }
            }
        });
        assertTrue(failure[0] == null ? "overlay was not added" : failure[0], shown[0]);
    }
}
