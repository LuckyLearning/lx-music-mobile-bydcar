package lx.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Shows the overlay from the test app process and checks the text that was actually set. */
@RunWith(AndroidJUnit4.class)
public class FloatingLyricTest {
    @Test
    public void overlayShowsTheCurrentLine() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String[] line = new String[1];
        final String[] failure = new String[1];
        final FloatingLyric[] overlay = new FloatingLyric[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                FloatingLyric lyric = new FloatingLyric(target);
                overlay[0] = lyric;
                try {
                    lyric.show("歌词测试行");
                    line[0] = lyric.text();
                } catch (RuntimeException error) {
                    failure[0] = error.getClass().getSimpleName() + " " + error.getMessage();
                }
            }
        });
        assertTrue(failure[0] == null ? "overlay was not added" : failure[0], failure[0] == null);
        assertEquals("歌词测试行", line[0]);
        Thread.sleep(4000);
        Bitmap shot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (shot != null) {
            Context test = InstrumentationRegistry.getInstrumentation().getContext();
            File file = new File(test.getFilesDir(), "lyric-overlay.png");
            FileOutputStream out = new FileOutputStream(file);
            shot.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                overlay[0].hide();
            }
        });
    }
}
