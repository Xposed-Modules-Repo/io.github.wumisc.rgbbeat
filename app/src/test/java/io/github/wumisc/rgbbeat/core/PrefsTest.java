package io.github.wumisc.rgbbeat.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * 偏好默认值稳定性测试：防止误改默认参数导致开机行为漂移。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrefsTest {

    private Prefs prefs() {
        Context ctx = RuntimeEnvironment.getApplication();
        return new Prefs(ctx);
    }

    @Test
    public void defaultsAreStable() {
        Prefs p = prefs();
        assertEquals("auto", p.audioSource());
        assertEquals(2.0f, p.sensitivity(), 1e-4);
        assertEquals(280L, p.cooldownMs());
        assertEquals(180, p.brightness());
        assertEquals("auto", p.mode());
        assertEquals(-1f, p.lightColorHue(), 1e-4);
        assertEquals("fusion", p.dataMode());
        assertTrue(p.carsickEnabled());
        assertTrue(p.motionTriggerEnabled());
        assertTrue(p.carsickToast());
        assertFalse(p.musicAutoEnabled());
        assertTrue(p.vibrationEnabled());
        assertEquals(120, p.vibrationIntensity());
        assertTrue(p.animEnabled());
        assertTrue(p.autoStart());
        assertFalse(p.triggerEnabled());
    }

    @Test
    public void defaultsUseEmptyBlacklist() {
        assertTrue(prefs().musicBlacklist().isEmpty());
        assertTrue(prefs().triggerApps().isEmpty());
    }
}
