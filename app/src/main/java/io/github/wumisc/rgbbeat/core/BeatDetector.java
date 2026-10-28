package io.github.wumisc.rgbbeat.core;


import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 节拍检测：基于低频能量突变的 onset 检测。
 * 输入为每帧的原始能量（bass 段幅度或 RMS），通过"当前能量 vs 滑动均值 × 阈值"判定命中，
 * 并带冷却时间避免连续误触发；同时滑动估算 BPM。
 * 冷启动保护：滑动均值尚未建立的前几帧不触发，避免开机瞬间的假命中（"只有开头震一下"）。
 */
public final class BeatDetector {

    private static final String TAG = "RGBBeat/Beat";
    private static final int WINDOW = 64;          // 滑动窗口帧数（约 1s @60fps）
    private static final int WARMUP = 16;          // 冷启动保护帧数（约 0.27s）
    private static final int BPM_WINDOW = 20;      // 用于估算 BPM 的命中间隔条数

    private final BeatState state;
    private final float threshold;                 // 触发倍率（如 1.3）
    private final long cooldownMs;
    private final float energyFloor;               // 绝对最小能量门槛：低于此不触发，过滤静音/系统 UI 声误判

    private final Deque<Float> window = new ArrayDeque<>(WINDOW + 1);
    private final Deque<Long> hits = new ArrayDeque<>(BPM_WINDOW + 1);
    private float mean = 0f;
    private float maxEnergy = 1f;
    private long lastHit = -1;

    public BeatDetector(BeatState state, float threshold, long cooldownMs) {
        this(state, threshold, cooldownMs, 0.015f);
    }

    public BeatDetector(BeatState state, float threshold, long cooldownMs, float energyFloor) {
        this.state = state;
        this.threshold = Math.max(1.05f, threshold);
        this.cooldownMs = Math.max(60L, cooldownMs);
        this.energyFloor = Math.max(0f, energyFloor);
    }

    /** 每帧调用一次，nowMs 为单调时钟（SystemClock.elapsedRealtime） */
    public void feed(float energyRaw, long nowMs) {
        // 滑动均值
        if (window.size() >= WINDOW) window.poll();
        window.add(energyRaw);
        float sum = 0f;
        for (float v : window) sum += v;
        mean = sum / window.size();

        // 动态峰值归一化
        if (energyRaw > maxEnergy) maxEnergy = energyRaw;
        else maxEnergy = Math.max(maxEnergy * 0.9995f, 1f);

        // onset 判定：冷启动保护（均值未建立时不触发）+ 绝对能量门槛（过滤静音/系统 UI 声）+ 阈值 + 冷却
        boolean hit = window.size() >= WARMUP
                && energyRaw >= energyFloor
                && energyRaw > mean * threshold
                && (lastHit < 0 || nowMs - lastHit >= cooldownMs);
        if (hit) {
            lastHit = nowMs;
            if (hits.size() >= BPM_WINDOW) hits.poll();
            hits.add(nowMs);
            DiagLog.d(TAG, "hit energy=" + String.format("%.3f", energyRaw)
                    + " mean=" + String.format("%.3f", mean)
                    + " ratio=" + String.format("%.2f", mean > 0 ? energyRaw / mean : 0)
                    + " bpm=" + estimateBpm());
        }
        state.beat = hit;

        // 归一化能量 0~1：能量相对均值/阈值越高越亮
        float ratio = mean > 0 ? energyRaw / (mean * threshold) : 0f;
        state.energy = clamp(ratio, 0f, 1f);
        state.intensity = clamp(ratio, 0f, 1f);
        state.bpm = estimateBpm();
    }

    private float estimateBpm() {
        if (hits.size() < 2) return 0f;
        // hits 存的是命中时刻(long)，必须转 Long 数组；转 Float[] 会抛 ArrayStoreException
        Long[] arr = hits.toArray(new Long[0]);
        double total = 0;
        for (int i = 1; i < arr.length; i++) total += arr[i] - arr[i - 1];
        double avgMs = total / (arr.length - 1);
        if (avgMs <= 0) return 0f;
        return (float) Math.min(240, Math.max(40, 60000.0 / avgMs));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
