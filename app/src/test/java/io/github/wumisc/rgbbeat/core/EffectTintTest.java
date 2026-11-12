package io.github.wumisc.rgbbeat.core;

import io.github.wumisc.rgbbeat.data.*;
import io.github.wumisc.rgbbeat.engine.*;
import io.github.wumisc.rgbbeat.render.*;
import io.github.wumisc.rgbbeat.service.*;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 礼物特效统一染色的颜色表回归测试 */
public class EffectTintTest {

    @Test
    public void defaultIsWhitePink() {
        assertEquals(EffectTint.WHITE_PINK, EffectTint.DEFAULT);
        assertEquals(0xFFB7D8, EffectTint.colorOf(null));
        assertEquals(0xFFB7D8, EffectTint.colorOf(EffectTint.WHITE_PINK));
        float[] v = EffectTint.resolve(null);
        assertEquals(1f, v[0], 1e-6f);
        assertEquals(183 / 255f, v[1], 1e-6f);
        assertEquals(216 / 255f, v[2], 1e-6f);
        assertEquals(1f, v[3], 1e-6f);
    }

    /** 白里粉要比心形粉更亮更淡，否则又会糊成一片玫红 */
    @Test
    public void whitePinkIsLighterThanHeart() {
        float[] wp = EffectTint.resolve(EffectTint.WHITE_PINK);
        float[] hp = EffectTint.resolve(EffectTint.HEART);
        float wpSum = wp[0] + wp[1] + wp[2];
        float hpSum = hp[0] + hp[1] + hp[2];
        assertTrue("白里粉亮度应高于心形粉", wpSum > hpSum);
        float wpSat = maxMinSpread(wp);
        float hpSat = maxMinSpread(hp);
        assertTrue("白里粉彩度应低于心形粉", wpSat < hpSat);
        assertEquals("绿通道是白里粉的关键", 183, Math.round(wp[1] * 255));
    }

    private static float maxMinSpread(float[] rgb) {
        float max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        float min = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        return max - min;
    }

    @Test
    public void illegalValueFallsBackToDefault() {
        assertEquals(EffectTint.DEFAULT, EffectTint.normalize(null));
        assertEquals(EffectTint.DEFAULT, EffectTint.normalize("garbage"));
        assertEquals(EffectTint.DEFAULT, EffectTint.normalize(""));
    }

    @Test
    public void originalColor_disablesTint() {
        float[] v = EffectTint.resolve(EffectTint.NONE);
        assertEquals(0f, v[3], 1e-6f);
    }

    @Test
    public void allValues_areUniqueAndProduceValidRgb() {
        assertEquals(9, EffectTint.VALUES.length);
        assertEquals("数组首项必须与默认值一致（设置项顺序依赖它）",
                EffectTint.DEFAULT, EffectTint.VALUES[0]);
        Set<String> seen = new HashSet<>();
        for (String s : EffectTint.VALUES) {
            assertTrue("取值不能重复: " + s, seen.add(s));
            assertEquals(s, EffectTint.normalize(s));
            float[] v = EffectTint.resolve(s);
            assertEquals(4, v.length);
            for (int i = 0; i < 3; i++) {
                assertTrue("RGB 必须在 0..1: " + s, v[i] >= 0f && v[i] <= 1f);
            }
            assertTrue("mix 只能是 0 或 1: " + s, v[3] == 0f || v[3] == 1f);
        }
    }
}
