package io.github.wumisc.rgbbeat.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 色彩引擎稳定性测试：透明度随亮度缩放、钳制与透明底、色相回绕。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ColorEngineTest {

    private static int alpha(int c) { return (c >>> 24) & 0xFF; }

    @Test
    public void alphaScalesWithBrightness() {
        // brightness=180 → alpha = 255 * 1.0 * (180/255) = 180
        assertEquals(180, alpha(ColorEngine.edgeColor(0f, 0f, 180f, 1f)));
    }

    @Test
    public void zeroAlphaScaleIsFullyTransparent() {
        assertEquals(0, alpha(ColorEngine.edgeColor(120f, 0f, 255f, 0f)));
    }

    @Test
    public void brightnessAbove255ClampsAlpha() {
        assertEquals(255, alpha(ColorEngine.edgeColor(0f, 0f, 300f, 1f)));
    }

    @Test
    public void negativeHueWrapsToPositive() {
        int c1 = ColorEngine.edgeColor(-30f, 0f, 255f, 0.5f);
        int c2 = ColorEngine.edgeColor(330f, 0f, 255f, 0.5f);
        assertEquals("负色相应回绕到 330°", c1, c2);
    }

    @Test
    public void energyParameterDoesNotChangeColor() {
        int c1 = ColorEngine.edgeColor(0f, 0.1f, 255f, 0.5f);
        int c2 = ColorEngine.edgeColor(0f, 0.9f, 255f, 0.5f);
        assertEquals("能量参数不参与颜色计算", c1, c2);
    }

    @Test
    public void solidColorIsOpaque() {
        int c = ColorEngine.solidColor(200f);
        assertEquals(255, alpha(c));
    }
}
