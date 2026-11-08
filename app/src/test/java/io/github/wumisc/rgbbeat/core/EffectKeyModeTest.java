package io.github.wumisc.rgbbeat.core;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 背景抠除模式的取值与 shader 分支映射回归测试 */
public class EffectKeyModeTest {

    @Test
    public void nullAndIllegalValues_defaultToAuto() {
        assertEquals(EffectKeyMode.AUTO, EffectKeyMode.normalize(null));
        assertEquals(EffectKeyMode.AUTO, EffectKeyMode.normalize("garbage"));
        assertEquals(EffectKeyMode.AUTO, EffectKeyMode.normalize(""));
        assertEquals(EffectKeyMode.AUTO, EffectKeyMode.normalize("AUTO"));
        assertEquals(EffectKeyMode.MODE_AUTO, EffectKeyMode.toShaderMode(null));
        assertEquals(EffectKeyMode.MODE_AUTO, EffectKeyMode.toShaderMode("garbage"));
    }

    @Test
    public void allModes_mapToShaderBranches() {
        assertEquals(EffectKeyMode.MODE_AUTO, EffectKeyMode.toShaderMode(EffectKeyMode.AUTO));
        assertEquals(EffectKeyMode.MODE_DARK, EffectKeyMode.toShaderMode(EffectKeyMode.DARK));
        assertEquals(EffectKeyMode.MODE_LIGHT, EffectKeyMode.toShaderMode(EffectKeyMode.LIGHT));
        assertEquals(EffectKeyMode.MODE_OFF, EffectKeyMode.toShaderMode(EffectKeyMode.OFF));
    }

    @Test
    public void values_areUniqueAndNormalizable() {
        assertEquals(4, EffectKeyMode.VALUES.length);
        assertEquals("默认必须是自动模式", EffectKeyMode.AUTO, EffectKeyMode.VALUES[0]);
        Set<String> seen = new HashSet<>();
        for (String v : EffectKeyMode.VALUES) {
            assertEquals(v, EffectKeyMode.normalize(v));
            assertTrue("设置项取值不能重复: " + v, seen.add(v));
        }
    }
}
