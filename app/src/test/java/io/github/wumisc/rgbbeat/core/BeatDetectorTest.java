package io.github.wumisc.rgbbeat.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 节拍检测稳定性测试：冷启动保护、周期脉冲 BPM 估算、冷却抑制连发、能量归一化。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BeatDetectorTest {

    private BeatState state;
    private BeatDetector detector;

    @Before
    public void setUp() {
        state = new BeatState();
        detector = new BeatDetector(state, 1.3f, 280L);
    }

    @Test
    public void warmupPeriodProducesNoBeat() {
        long t = 0;
        for (int i = 0; i < 20; i++) {
            detector.feed(0.5f, t);
            t += 16;
        }
        assertFalse("冷启动期内不应触发节拍", state.beat);
    }

    @Test
    public void periodicImpulsesProduceStableBpm() {
        long t = 0;
        for (int i = 0; i < 200; i++) {
            float e = (i % 30 == 0) ? 8.0f : 0.5f; // 每 30 帧一个脉冲 ≈ 480ms ≈ 125BPM
            detector.feed(e, t);
            t += 16;
        }
        float bpm = state.bpm;
        assertTrue("应有节拍命中并估算出 BPM，实际=" + bpm, bpm > 0f);
        assertTrue("BPM 应在 100~150 之间（480ms 间隔≈125），实际=" + bpm,
                bpm >= 100f && bpm <= 150f);
    }

    @Test
    public void cooldownSuppressesBackToBackHits() {
        long t = 0;
        for (int i = 0; i < 40; i++) { // 预热建立均值
            detector.feed(0.5f, t);
            t += 16;
        }
        int hits = 0;
        boolean prev = false;
        // 第一个强脉冲 → 命中
        detector.feed(8.0f, t);
        if (state.beat && !prev) hits++;
        prev = state.beat;
        // 冷却期（280ms）内连续强脉冲（间隔 50ms）→ 不应再命中
        for (int i = 0; i < 3; i++) {
            t += 50;
            detector.feed(8.0f, t);
            if (state.beat && !prev) hits++;
            prev = state.beat;
        }
        assertEquals("冷却期内强脉冲只应命中 1 次", 1, hits);
    }

    @Test
    public void constantLowEnergyNeverBeats() {
        long t = 0;
        for (int i = 0; i < 200; i++) {
            detector.feed(0.3f, t);
            t += 16;
        }
        assertFalse("恒定低能量不应触发节拍", state.beat);
    }

    @Test
    public void strongSpikeNormalizesEnergyToOne() {
        long t = 0;
        for (int i = 0; i < 30; i++) {
            detector.feed(1.0f, t);
            t += 16;
        }
        detector.feed(20.0f, t);
        assertEquals("强脉冲能量应归一化为 1.0", 1.0f, state.energy, 1e-4);
    }

    @Test
    public void thresholdBelowMinimumIsClamped() {
        BeatDetector d = new BeatDetector(new BeatState(), 0.1f, 10L);
        // 构造器把阈值钳到 >=1.05、冷却钳到 >=60，不应抛异常
        long t = 0;
        for (int i = 0; i < 30; i++) {
            d.feed(0.5f, t);
            t += 16;
        }
        assertFalse(state.beat);
    }

    @Test
    public void nearSilenceNoiseDoesNotBeat() {
        // 接近静音（energy 0.001~0.005，mean≈0，ratio 极高）也不应触发节拍——过滤系统 UI 声/底噪误判
        long t = 0;
        boolean any = false;
        for (int i = 0; i < 300; i++) {
            float e = 0.001f + (i % 5) * 0.001f;
            detector.feed(e, t);
            if (state.beat) any = true;
            t += 16;
        }
        assertFalse("静音底噪（远低于能量门槛）不应触发节拍", any);
    }

    @Test
    public void floorFiltersNoiseButAllowsRealPulse() {
        BeatDetector d = new BeatDetector(state, 1.3f, 280L, 0.02f);
        long t = 0;
        for (int i = 0; i < 40; i++) { d.feed(0.5f, t); t += 16; } // 预热
        // 底噪尖峰：0.003，绝对能量远低于门槛 → 不触发
        d.feed(0.003f, t);
        assertFalse("0.003 底噪尖峰不应触发", state.beat);
        // 真实脉冲：0.5→8.0，绝对能量远超门槛 → 触发
        t += 16; d.feed(8.0f, t);
        assertTrue("真实脉冲应触发", state.beat);
    }
}
