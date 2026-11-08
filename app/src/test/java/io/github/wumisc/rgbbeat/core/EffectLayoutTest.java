package io.github.wumisc.rgbbeat.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 礼物特效「完整居中、不裁切、不变形」的回归测试。
 * 三段素材都是横屏，之前直接铺到竖屏 MATCH_PARENT 的 TextureView 导致只能看到局部。
 */
public class EffectLayoutTest {

    @Test
    public void wideVideoOnPortraitScreen_fitsWidthAndCenters() {
        float[] s = EffectLayout.fit(1080, 2400, 992, 720);
        assertEquals("横屏视频在竖屏上宽度撑满", 1f, s[0], 1e-6f);
        assertTrue("高度按原始比例缩小", s[1] > 0f && s[1] < 1f);
        assertEquals("保持原始宽高比",
                (1080f / 2400f) / (992f / 720f), s[1], 1e-5f);
    }

    @Test
    public void tallVideoOnWideScreen_fitsHeight() {
        float[] s = EffectLayout.fit(2400, 1080, 720, 1280);
        assertEquals(1f, s[1], 1e-6f);
        assertTrue(s[0] > 0f && s[0] < 1f);
    }

    @Test
    public void matchingAspect_fillsExactly() {
        float[] s = EffectLayout.fit(1080, 1920, 540, 960);
        assertEquals(1f, s[0], 1e-6f);
        assertEquals(1f, s[1], 1e-6f);
    }

    @Test
    public void invalidSizes_neverCropOrCrash() {
        int[][] bad = {{0, 0, 992, 720}, {1080, 2400, 0, 0}, {-1, 100, 100, 100},
                {1080, 2400, -5, 720}};
        for (int[] b : bad) {
            float[] s = EffectLayout.fit(b[0], b[1], b[2], b[3]);
            assertEquals(1f, s[0], 1e-6f);
            assertEquals(1f, s[1], 1e-6f);
        }
    }

    @Test
    public void allThreeAssets_fitCompletelyOnPhone() {
        int[][] assets = {{992, 720}, {1568, 720}, {896, 528}};
        int[][] screens = {{1080, 2400}, {720, 1600}, {1440, 3200}, {2400, 1080}};
        for (int[] sc : screens) {
            for (int[] a : assets) {
                float[] s = EffectLayout.fit(sc[0], sc[1], a[0], a[1]);
                assertTrue("不得放大裁切: " + s[0] + "," + s[1],
                        s[0] <= 1f + 1e-6f && s[1] <= 1f + 1e-6f);
                assertTrue("至少要有一维刚好撑满",
                        Math.abs(Math.max(s[0], s[1]) - 1f) < 1e-6f);
                assertTrue("不能出现 0 或负尺寸", s[0] > 0f && s[1] > 0f);
            }
        }
    }
}
