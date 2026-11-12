package io.github.wumisc.rgbbeat.core;

import io.github.wumisc.rgbbeat.data.*;
import io.github.wumisc.rgbbeat.engine.*;
import io.github.wumisc.rgbbeat.render.*;
import io.github.wumisc.rgbbeat.service.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 回归测试：三段礼物特效必须都能轮到。
 * 原实现在 stop() 里把 currentEffect 重置为 -1，每次 (current+1)%3 永远等于 0，
 * 结果「新增的另外两段特效」永远播不出来。
 */
public class EffectPickerTest {

    @Test
    public void cycleVisitsAllThreeEffectsRepeatedly() {
        int current = -1;
        int[] expected = {0, 1, 2, 0, 1, 2, 0, 1, 2};
        for (int want : expected) {
            current = EffectPicker.resolve(EffectPicker.CYCLE, current);
            assertEquals("轮播必须依次覆盖三段特效", want, current);
        }
    }

    @Test
    public void fixedSelectionIsStable() {
        assertEquals(0, EffectPicker.resolve(EffectPicker.ORBIT, -1));
        assertEquals(0, EffectPicker.resolve(EffectPicker.ORBIT, 2));
        assertEquals(1, EffectPicker.resolve(EffectPicker.OCEAN, 2));
        assertEquals(2, EffectPicker.resolve(EffectPicker.PHOENIX, 0));
    }

    @Test
    public void unknownValuesFallBackToCycle() {
        assertEquals(EffectPicker.CYCLE, EffectPicker.normalize(null));
        assertEquals(EffectPicker.CYCLE, EffectPicker.normalize("garbage"));
        assertEquals(EffectPicker.OCEAN, EffectPicker.normalize(EffectPicker.OCEAN));
        assertEquals(0, EffectPicker.resolve("garbage", -1));
    }

    @Test
    public void outOfRangeCurrentNeverProducesInvalidIndex() {
        for (int cur : new int[]{-5, -1, 0, 2, 3, 99}) {
            int idx = EffectPicker.resolve(EffectPicker.CYCLE, cur);
            assertTrue("索引必须落在 0..2", idx >= 0 && idx < EffectPicker.COUNT);
        }
    }
}
