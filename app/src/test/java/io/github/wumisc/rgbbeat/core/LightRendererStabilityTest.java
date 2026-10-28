package io.github.wumisc.rgbbeat.core;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

/**
 * 渲染稳定性测试：跨越全部光效模式/数据模式/键盘状态连续渲染 300 帧，
 * 覆盖烟花生命周期（升空→爆炸→消散），断言任意帧不崩溃、不抛异常。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LightRendererStabilityTest {

    private static final int W = 1080;
    private static final int H = 2340;

    private BeatState state;
    private LightRenderer renderer;
    private Canvas canvas;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        state = new BeatState();
        Prefs prefs = new Prefs(ctx);
        renderer = new LightRenderer(state, prefs);
        canvas = new Canvas(Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888));
    }

    @Test
    public void renders300FramesAcrossAllModesWithoutCrash() {
        String[] modes = {"edge", "marquee", "keyboard", "auto"};
        String[] dataModes = {"music", "motion", "fusion"};
        int frames = 0;
        for (int i = 0; i < 300; i++) {
            state.energy = (i % 40) / 40f;
            state.beat = (i % 20 == 0);
            state.hue = (i * 7) % 360;
            state.marqueeT = (i % 100) / 100f;
            state.intensity = state.energy;
            state.bpm = 120f;
            state.pitchLevel = 0.2f + (i % 5) * 0.15f;
            state.pitchHue = (i * 13) % 360;
            state.mode = modes[i % modes.length];
            state.dataMode = dataModes[i % dataModes.length];
            state.keyboardVisible = (i % 3 == 0);
            state.keyboardHeightPx = state.keyboardVisible ? 700 : 0;
            state.motionEnergy = (i % 10) / 10f;
            state.motionX = ((i % 7) - 3) / 3f;
            state.motionY = ((i % 5) - 2) / 2f;
            state.motionJitter = (i % 4) / 4f;
            state.vehicleActive = (i % 3 == 1);
            state.title = "稳定性测试歌曲";
            state.artist = "RGBBeat 测试歌手";
            state.lyricReady = (i % 2 == 0);
            state.lyricNow = "歌词行 A";
            state.lyricPrev = "歌词行 B";
            state.lyricNext = "歌词行 C";

            renderer.draw(canvas, W, H);
            // 推进 Shadow 时钟，驱动烟花升空→爆炸→消散的完整生命周期
            RuntimeEnvironment.getMasterScheduler().advanceBy(33, TimeUnit.MILLISECONDS);
            frames++;
        }
        assertEquals("应完整渲染 300 帧", 300, frames);
    }

    @Test
    public void rendersWithZeroEnergyAndSilence() {
        state.energy = 0f;
        state.beat = false;
        state.mode = "auto";
        state.dataMode = "music";
        state.keyboardVisible = false;
        renderer.draw(canvas, W, H);
        // 静默/空闲状态也不崩溃
        renderer.draw(canvas, W, H);
    }
}
