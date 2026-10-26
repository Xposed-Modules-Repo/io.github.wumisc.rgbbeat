package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * 车辆运动传感器采集（晕车缓解模式的数据源）。
 *
 * 用手机加速度计感知车辆运动，替代音乐频谱作为光效驱动：
 *  - 加减速（纵向 z 轴，去重力）→ motionY：两侧粒子/光带沿行进方向移动
 *  - 转弯/变道（横向 x 轴）→ motionX：圆点向弯道反方向偏移
 *  - 颠簸/摇晃（高频抖动）→ motionJitter：粒子抖动
 *  - 运动强度（加速度合成幅度）→ motionEnergy：呼吸脉动/强调
 *
 * 自动开关：持续运动超阈值一段时间 → active=true（自动开启光效）；静止超时 → active=false（自动关）。
 */
public final class MotionSense implements SensorEventListener {

    private static final String TAG = "RGBBeat/Motion";

    /** 运动判定加速度阈值 m/s²（低于视为静止） */
    private static final float ACTIVE_THRESHOLD = 0.15f;
    /** 持续运动多久(ms)才算"车辆运动中"，避免单次晃动误触发 */
    private static final long ACTIVE_MS = 1200;
    /** 静止多久(ms)才算停止，自动关闭光效 */
    private static final long STILL_MS = 2500;

    /** 帧回调：energy=运动强度, x=横向, y=纵向, jitter=颠簸, active=车辆运动中 */
    public interface Listener {
        void onMotion(float energy, float x, float y, float jitter, boolean active);
    }

    private final Context ctx;
    private final Listener listener;
    private SensorManager sm;
    private boolean running;

    // 平滑后的线性加速度（低通滤波）
    private float ax, ay, az;
    private float lastMag;
    // 静止/运动判定时间戳
    private long activeSince;
    private long stillSince;
    private boolean lastActive;

    public MotionSense(Context c, Listener l) {
        this.ctx = c;
        this.listener = l;
    }

    public void start() {
        if (running) return;
        running = true;
        sm = ctx.getSystemService(SensorManager.class);
        Sensor acc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION); // 去重力，更干净
        if (acc == null) acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc == null) {
            DiagLog.d(TAG, "no accelerometer available");
            running = false;
            return;
        }
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME); // ~50ms
        DiagLog.d(TAG, "motion sensor started: " + acc.getName() + " type=" + acc.getType());
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        try {
            if (!running) return;
            // 线性加速度：横向 x、纵向 y、垂向 z；低通平滑抑制传感器噪声
            float rawX = e.values[0], rawY = e.values[1], rawZ = e.values[2];
            ax += (rawX - ax) * 0.25f;
            ay += (rawY - ay) * 0.25f;
            az += (rawZ - az) * 0.25f;

            // 运动强度：加速度合成幅度（去重力后，静止≈0）
            float mag = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            float energy = Math.min(1f, mag / 3.0f);

            // 颠簸：加速度变化率（高频抖动），乘系数放大
            float jit = Math.abs(mag - lastMag);
            lastMag = mag;
            float jitter = Math.min(1f, jit * 9f);

            // 归一化偏移（÷2 使日常加减速/转弯落在 -1~1）
            float x = clamp(ax / 2.5f);
            float y = clamp(az / 2.5f);

            // 静止/运动判定（带滞回：避免传感器噪声让状态每帧翻转，
            //  进入"运动中"需持续运动 ACTIVE_MS，退出需持续静止 STILL_MS）
            boolean moving = mag > ACTIVE_THRESHOLD;
            long now = SystemClock.elapsedRealtime();
            boolean active;
            if (!lastActive) {
                // 静止 → 运动：需持续运动 ACTIVE_MS 才确认进入车辆运动中
                if (moving) {
                    if (activeSince == 0) activeSince = now;
                    active = (now - activeSince) >= ACTIVE_MS;
                } else {
                    activeSince = 0; // 中途出现静止帧，重新累计
                    active = false;
                }
            } else {
                // 运动 → 静止：需持续静止 STILL_MS 才确认停止（红绿灯短暂停车不算）
                if (!moving) {
                    if (stillSince == 0) stillSince = now;
                    active = (now - stillSince) < STILL_MS;
                } else {
                    stillSince = 0; // 中途仍有运动，重新累计静止
                    active = true;
                }
            }
            if (active != lastActive) {
                lastActive = active;
                // 状态翻转时重置另一侧计时起点
                if (active) stillSince = 0; else activeSince = 0;
            }

            if (listener != null) listener.onMotion(energy, x, y, jitter, active);

            if (active != lastActive) {
                lastActive = active;
                DiagLog.d(TAG, "vehicleActive=" + active + " energy=" + String.format("%.2f", energy)
                        + " x=" + String.format("%.2f", x) + " y=" + String.format("%.2f", y)
                        + " jitter=" + String.format("%.2f", jitter));
            }
        } catch (Throwable t) {
            DiagLog.w(TAG, "motion sense err: " + t);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    public void stop() {
        running = false;
        if (sm != null && listener != null) {
            try { sm.unregisterListener(this); } catch (Throwable ignored) { }
        }
        sm = null;
    }
}
