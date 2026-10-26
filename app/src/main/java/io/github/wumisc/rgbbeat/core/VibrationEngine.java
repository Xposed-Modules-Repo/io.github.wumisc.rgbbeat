package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * 马达震动：节拍命中时触发短震动，强度随能量与设置动态调整。
 *
 * 兼容性要点：createOneShot(ms, amplitude) 依赖设备的幅度控制能力；
 * 在无 hasAmplitudeControl() 的 ROM（HyperOS/部分国产定制）上带 amplitude 的调用
 * 可能抛异常被静默吞掉，导致"从始至终不震动"。这里按能力分三条路径兜底。
 */
public final class VibrationEngine {

    private static final String TAG = "RGBBeat/Vib";

    private final Vibrator vibrator;
    private long lastMotionVibeMs = 0; // 晕车震动节流
    private long lastBeatVibeMs = 0;   // 节拍震动节流：避免连续命中时马达持续嗡嗡

    public VibrationEngine(Context context) {
        vibrator = context.getSystemService(Vibrator.class);
    }

    /**
     * 晕车缓解模式：随车辆运动轻微震动（颠簸/加减速有感知但不刺激）。
     * 传感器 ~50ms 一帧，这里节流到 ~260ms 一次，避免马达连续空转。
     */
    public void onMotion(float energy, boolean enabled, int intensity) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastMotionVibeMs < 260) return;
        lastMotionVibeMs = now;
        long ms = Math.max(15L, Math.min(60L, (long) (20 + energy * 40)));
        int amp = (int) Math.max(30, Math.min(180, intensity * 0.8f * (0.3f + energy) * 1.5f));
        DiagLog.d(TAG, "onMotion energy=" + String.format("%.2f", energy)
                + " ms=" + ms + " amp=" + amp);
        try {
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amp));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception e) {
            try { vibrator.vibrate(ms); } catch (Exception ignored) { }
        }
    }

    /**
     * 节拍命中回调：精准跟随节拍，强度足（用户要求"精准节拍、别削弱"）。
     * 节流降到 60ms 只防同帧重叠，让每个节拍都能震到；
     * 时长 45~130ms、振幅 80~255 充分反馈，高音/强能量更足。
     * @param energy    归一化能量
     * @param pitch     主导音高强度 0~1（按音符 A~E 高低映射）：高音→震动更强
     */
    public void onBeat(float energy, float pitch, boolean enabled, int intensity) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) {
            DiagLog.d(TAG, "onBeat skipped: enabled=" + enabled
                    + " vibNull=" + (vibrator == null) + " hasVib=" + (vibrator != null && vibrator.hasVibrator()));
            return;
        }
        // 节流：仅防同一帧重复触发，60ms 内不重复，确保精准跟随节拍
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastBeatVibeMs < 60) return;
        lastBeatVibeMs = now;
        // 时长：跟随能量 45~130ms，反馈清晰不疲软
        long ms = Math.max(45L, Math.min(130L, (long) (45 + 85 * energy)));
        // 幅度：跟随能量/音高，冲足到 80~255，高音强化
        float p = Math.max(0.1f, Math.min(1f, pitch));
        int amp = (int) Math.max(80, Math.min(255,
                intensity * 1.2f * (0.5f + 0.5f * p) * (0.5f + 0.5f * energy)));
        DiagLog.d(TAG, "onBeat energy=" + String.format("%.2f", energy)
                + " pitch=" + String.format("%.2f", pitch)
                + " inten=" + intensity + " ms=" + ms + " amp=" + amp
                + " ampCtl=" + vibrator.hasAmplitudeControl());
        try {
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amp));
            } else {
                // 无幅度控制设备：不带 amplitude，退回传统 vibrate(ms)
                vibrator.vibrate(ms);
            }
        } catch (Exception e) {
            DiagLog.d(TAG, "createOneShot failed, fallback to plain vibrate: " + e);
            // 最后兜底：极旧设备/异常 ROM 退回传统 vibrate
            try {
                vibrator.vibrate(ms);
            } catch (Exception ignored) {
                DiagLog.d(TAG, "plain vibrate also failed: " + ignored);
            }
        }
    }

    public void release() {
        try { vibrator.cancel(); } catch (Exception ignored) { }
    }
}
