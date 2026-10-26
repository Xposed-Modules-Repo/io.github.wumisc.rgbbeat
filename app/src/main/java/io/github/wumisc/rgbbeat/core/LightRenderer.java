package io.github.wumisc.rgbbeat.core;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;

/**
 * RGB 光效渲染器：在 LightOverlayView.onDraw 中绘制随音乐律动的屏幕边缘光效。
 *
 * v1.7 视觉重构——从"长方形硬边框"升级为"屏幕周围的光污染氛围光"：
 *  · 四边柔和宽渐变光晕：从边缘向屏幕内侧渐隐（宽度约屏短边 7%），低透明度、
 *    细腻氛围感，形成"整屏边缘都在发光"的晕染，而非贴边的硬矩形框；
 *  · 四角径向柔光：圆润连接各边，消除直角，进一步弱化"长方形"观感；
 *  · 彩虹流光光斑：沿屏幕边缘周长分布 16 个彩色光斑，色相沿周长连续渐变（彩虹），
 *    随音乐能量流动、脉动、节拍增亮——类似机箱风扇 RGB 的五彩光污染，但低饱和柔和不刺眼；
 *  · 透明度真正随能量/节拍呼吸（修复原先能量脉动参数被忽略、亮度恒定的问题）。
 */
public final class LightRenderer {

    private static final String TAG = "RGBBeat/Light";
    private static final long FRAME_MS = 22;      // ~45fps
    // 冷色系四边相邻色相偏移（紫→青→蓝→品红），整体冷色霓虹
    private static final int[] EDGE_OFFSET = {0, 90, 180, 270};

    private final BeatState state;
    private final Prefs prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.Random rand = new java.util.Random();

    // 沿左右竖边上下漂移的细小粒子（随机相位/大小/色差，首帧即初始化）
    private static final int PARTICLES = 16;
    private final float[] pPhase = new float[PARTICLES];
    private final float[] pSize = new float[PARTICLES];
    private final float[] pHue = new float[PARTICLES];

    private View view;
    private Thread thread;
    private volatile boolean running;
    private long lastFrame = 0;
    private float shake = 0f; // 节拍震动强度：命中时置峰，随时间衰减
    private float emphasis = 0f; // 高音强调色脉冲：高音命中时置峰，随时间衰减

    // ---- 烟花特效：先升空、再爆炸的柔和渐变粒子花（替代礼花，更像真烟花）----
    private static final int FW_MAX = 4;          // 同时最多 4 朵
    private static final int FW_PARTICLES = 24;   // 每朵爆炸粒子数
    private static final int RISE_MS = 420;       // 升空时长(ms)
    private static final int BURST_MS = 1300;     // 爆炸散开时长(ms)
    private final long[] fwStart = new long[FW_MAX];
    private final float[] fwCx = new float[FW_MAX], fwCy = new float[FW_MAX]; // cx=发射x, cy=爆炸高度y
    private final float[] fwSpeed = new float[FW_MAX];
    private final float[] fwHue = new float[FW_MAX];
    private final String[] fwText = new String[FW_MAX]; // 爆炸中心显示：歌手名（作者）
    private final float[][] fwDirX = new float[FW_MAX][FW_PARTICLES];
    private final float[][] fwDirY = new float[FW_MAX][FW_PARTICLES];
    private long nextFwAt = 0;
    private long lastFwBeatMs = 0;
    private int fwSlot = 0;

    public LightRenderer(BeatState state, Prefs prefs) {
        this.state = state;
        this.prefs = prefs;
        for (int i = 0; i < PARTICLES; i++) {
            pPhase[i] = rand.nextFloat();
            pSize[i] = 3f + rand.nextFloat() * 4f;
            pHue[i] = rand.nextFloat() * 360f;
        }
    }

    public void attach(View v) {
        this.view = v;
    }

    public void start() {
        if (running) return;
        running = true;
        lastFrame = 0;
        thread = new Thread(this::loop, "rgbbeat-render");
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(300); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    public boolean isRunning() { return running; }

    // ---------- 主循环：更新色相并触发重绘 ----------

    private void loop() {
        // 渲染线程提为 DISPLAY 优先级：切后台/回桌面时避免被系统大幅降频，光效持续流动
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        } catch (Throwable ignored) { }
        while (running) {
            try {
                long now = SystemClock.elapsedRealtime();
                if (lastFrame > 0) {
                    float dt = (now - lastFrame) / 1000f;
                    float sp = prefs.speed();
                    state.hue = (state.hue + sp * 0.6f * dt) % 360f;
                    // 跑马灯/流光进度：随速度推进，节拍/能量越高流动越快
                    state.marqueeT = (state.marqueeT + sp * 0.03f * dt * (1f + state.energy * 2f)) % 1f;
                }
                lastFrame = now;
                if (view != null) {
                    view.postInvalidate();
                }
            } catch (Throwable t) {
                DiagLog.w(TAG, "render loop error: " + t);
            }
            try { Thread.sleep(FRAME_MS); } catch (InterruptedException e) { return; }
        }
    }

    // ---------- 绘制入口（LightOverlayView.onDraw 调用） ----------

    public void draw(Canvas canvas, int w, int h) {
        if (w <= 0 || h <= 0) return;

        float energy = state.energy;
        boolean beat = state.beat;
        float hue = state.hue;
        float brightness = prefs.brightness();

        // 用户自定义主色：固定色相（-1 时保持跟随节奏自动彩虹）
        float userHue = prefs.lightColorHue();
        if (userHue >= 0f) hue = userHue;

        // 烟花特效叠加在所有模式上：节拍命中升空爆炸（爆心炸出作者名）、空闲周期绽放
        drawFireworks(canvas, w, h, hue, brightness);

        // 晕车缓解模式：左右光带/粒子跟随车辆运动（加减速/转弯/颠簸），自动开启
        if ("motion".equals(state.dataMode)) {
            drawMotion(canvas, w, h, hue, brightness);
            return;
        }
        // 融合模式：以晕车缓解光效为基底，叠加音乐节拍/能量增强（听音乐也能看晕车）
        if ("fusion".equals(state.dataMode)) {
            drawFusion(canvas, w, h, hue, brightness);
            return;
        }

        // 节拍震动：命中时给边缘光斑/亮度注入抖动，随节拍"震动"、快速衰减
        if (beat) shake = 1f; else shake *= 0.82f;
        // 高音强调色脉冲：高音且有声时，整圈向强调色相靠拢并明显变亮（人声/旋律高音强调色）
        boolean hi = state.pitchLevel > 0.55f && (state.beat || state.energy > 0.25f);
        if (hi) emphasis = 1f; else emphasis *= 0.82f;

        boolean edgeActive;
        boolean kbActive;
        String mode = state.mode;
        if ("marquee".equals(mode)) {
            drawMarquee(canvas, w, h, hue, energy, beat, brightness);
            return;
        } else if ("keyboard".equals(mode)) {
            edgeActive = false;
            kbActive = state.keyboardVisible;
        } else if ("edge".equals(mode)) {
            edgeActive = true;
            kbActive = false;
        } else { // auto
            edgeActive = !state.keyboardVisible;
            kbActive = state.keyboardVisible;
        }

        if (edgeActive) {
            drawEdge(canvas, w, h, hue, energy, beat, brightness);
        } else if (kbActive && state.keyboardHeightPx > 0) {
            int kbTop = h - state.keyboardHeightPx;
            drawKeyboard(canvas, w, h, kbTop, hue, energy, brightness);
        }
    }

    /** 左右竖向流动光带：晕车缓解式柔和呼吸脉动 + 高音强调色 + 细小粒子沿边上下飘（无上下横带，非老化静态） */
    private void drawEdge(Canvas c, int w, int h, float hue, float energy, boolean beat, float b) {
        float shortSide = Math.min(w, h);
        float haloW = shortSide * 0.07f; // 竖向光带宽度（略窄，突出流动与粒子）
        float t = SystemClock.elapsedRealtime() / 1000f; // 秒级时钟驱动慢速呼吸
        float pitch = state.pitchLevel;

        // 呼吸脉动：晕车缓解式轻柔起伏（即使无音乐也持续呼吸），叠加能量/节拍/震动/高音强调
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 1.5f);
        float base = 0.16f + energy * 0.30f + (beat ? 0.18f : 0f)
                + breath * 0.10f + shake * (0.20f + 0.45f * pitch) * 0.12f;
        base += emphasis * 0.30f; // 高音强调额外增亮
        base = Math.min(base, 0.92f);

        // 高音强调色相：常态 hue 向主导音高强调色 pitchHue 靠拢
        float effHue = (hue * (1f - emphasis) + state.pitchHue * emphasis) % 360f;
        if (effHue < 0) effHue += 360f;

        // 光带内上下流动流光位置（垂直循环，限制在中段避免边缘整带点亮）
        float hh = haloW * 1.8f;
        float wy = hh + (state.marqueeT % 1f) * Math.max(0f, h - 2f * hh);

        // 1) 左右竖向光带：边缘→中心渐隐 + 带内上下流动流光
        drawSideBand(c, w, h, haloW, effHue, energy, b, base, wy, hh);

        // 2) 细小粒子沿左右竖边上下漂移，随节拍跳动；高音出现强调色亮点
        drawSideParticles(c, w, h, haloW, effHue, energy, beat, b, base);
    }

    /**
     * 晕车缓解绘制：左右竖向光带 + 粒子，随车辆运动变化——
     *  · 加减速（motionY）→ 粒子沿行进方向上下移动
     *  · 转弯/变道（motionX）→ 粒子向弯道反方向横向偏移
     *  · 颠簸/摇晃（motionJitter）→ 粒子抖动散开
     *  · 运动强度（motionEnergy）→ 呼吸脉动/亮度
     */
    private void drawMotion(Canvas c, int w, int h, float hue, float b) {
        float shortSide = Math.min(w, h);
        float haloW = shortSide * 0.07f;
        float t = SystemClock.elapsedRealtime() / 1000f;
        float energy = state.motionEnergy;
        float x = state.motionX, y = state.motionY, jitter = state.motionJitter;

        // 呼吸脉动随运动强度：车辆越颠簸/加减速越明显，光越强
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 1.8f);
        float base = 0.14f + energy * 0.5f + breath * 0.10f;
        base = Math.min(base, 0.85f);

        // 色相随纵向加减速轻微偏转，增加"在动"的灵动感
        float effHue = (hue + y * 70f) % 360f;
        if (effHue < 0) effHue += 360f;

        float hh = haloW * 1.8f;
        float waveY = hh + (state.marqueeT % 1f) * Math.max(0f, h - 2f * hh);
        drawSideBand(c, w, h, haloW, effHue, energy, b, base, waveY, hh);

        drawMotionParticles(c, w, h, haloW, effHue, b, base, x, y, jitter);
    }

    /** 融合绘制：晕车缓解光效为基底（随车辆运动变化），叠加音乐节拍/能量增强亮度与脉动 */
    private void drawFusion(Canvas c, int w, int h, float hue, float b) {
        float shortSide = Math.min(w, h);
        float haloW = shortSide * 0.07f;
        float t = SystemClock.elapsedRealtime() / 1000f;
        float energy = state.motionEnergy;
        float x = state.motionX, y = state.motionY, jitter = state.motionJitter;

        // 音乐能量与节拍增强亮度/脉动：听音乐时更亮、更灵动
        float musicBoost = state.energy * 0.35f + (state.beat ? 0.15f : 0f);
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 1.8f);
        float base = 0.14f + energy * 0.5f + musicBoost + breath * 0.10f;
        base = Math.min(base, 0.9f);

        // 色相随纵向加减速轻微偏转，叠加音乐主导音高强调色
        float effHue = (hue + y * 70f) % 360f;
        if (effHue < 0) effHue += 360f;

        float hh = haloW * 1.8f;
        float waveY = hh + (state.marqueeT % 1f) * Math.max(0f, h - 2f * hh);
        drawSideBand(c, w, h, haloW, effHue, energy, b, base, waveY, hh);

        drawMotionParticles(c, w, h, haloW, effHue, b, base, x, y, jitter);
    }

    /** 晕车粒子：沿左右竖边上下漂移，随加减速/转弯偏移、颠簸抖动 */
    private void drawMotionParticles(Canvas c, int w, int h, float haloW, float effHue,
                                     float b, float base, float x, float y, float jitter) {
        float energy = state.motionEnergy;
        float amp = jitter * 26f; // 颠簸抖动幅度
        float yOff = y * h * 0.22f; // 加减速纵向偏移
        float xOff = -x * h * 0.10f; // 转弯反方向偏移（圆点向弯道反方向移，缓解晕车）
        for (int i = 0; i < PARTICLES; i++) {
            float py = ((state.marqueeT + pPhase[i]) % 1f) * h + yOff;
            float px = (rand.nextFloat() - 0.5f) * 2f * amp;
            float pj = (rand.nextFloat() - 0.5f) * 2f * amp;
            float r = pSize[i] * (1f + energy * 1.2f + jitter * 0.8f);
            float a = Math.min(0.95f, base + 0.30f + jitter * 0.15f);
            float dotHue = (effHue + pHue[i]) % 360f;
            drawGlowDot(c, haloW * 0.55f + xOff + px, py + pj, r, dotHue, energy, b, a);
            drawGlowDot(c, w - haloW * 0.55f + xOff + px, py + pj, r, dotHue, energy, b, a);
        }
        // 转弯/加减速时的强调亮点：向反方向更醒目地偏移，突出运动方向感
        if (Math.abs(x) > 0.25f || Math.abs(y) > 0.25f) {
            float ey = (h * 0.5f) + yOff * 1.6f;
            float er = haloW * (0.7f + Math.abs(x) * 0.6f + Math.abs(y) * 0.6f);
            float ea = Math.min(1f, 0.5f + Math.abs(x) * 0.4f + Math.abs(y) * 0.4f);
            float dlx = haloW * 0.5f + xOff * 1.5f;
            float drx = w - haloW * 0.5f + xOff * 1.5f;
            drawGlowDot(c, dlx, ey, er, (effHue + 200f) % 360f, energy, b, ea);
            drawGlowDot(c, drx, ey, er, (effHue + 200f) % 360f, energy, b, ea);
        }
    }

    /** 左右竖向光带：本体（边缘→中心水平渐隐）+ 带内上下流动的流光亮带 */    private void drawSideBand(Canvas c, int w, int h, float haloW, float effHue,
                              float energy, float b, float base, float waveY, float hh) {
        // 左右光带本体
        drawBand(c, 0, 0, haloW, h,
                new LinearGradient(0, 0, haloW, 0,
                        edgeColor(effHue, 180, energy, b, base), transparent(effHue, 180), Shader.TileMode.CLAMP));
        drawBand(c, w - haloW, 0, haloW, h,
                new LinearGradient(w, 0, w - haloW, 0,
                        edgeColor(effHue, 270, energy, b, base), transparent(effHue, 270), Shader.TileMode.CLAMP));

        // 带内上下流动的流光亮带（随 marqueeT 垂直循环，亮、上下渐隐，营造"光波流动"）
        float wa = Math.min(0.95f, base + 0.22f);
        drawBand(c, 0, 0, haloW, h,
                new LinearGradient(0, waveY - hh, 0, waveY + hh,
                        edgeColor(effHue, 180, energy, b, wa), transparent(effHue, 180), Shader.TileMode.CLAMP));
        drawBand(c, w - haloW, 0, haloW, h,
                new LinearGradient(w, waveY - hh, w, waveY + hh,
                        edgeColor(effHue, 270, energy, b, wa), transparent(effHue, 270), Shader.TileMode.CLAMP));
    }

    /** 细小粒子：沿左右竖边上下漂移，节拍时跳动；高音时出现特别明亮的强调色亮点 */
    private void drawSideParticles(Canvas c, int w, int h, float haloW, float effHue,
                                   float energy, boolean beat, float b, float base) {
        float pitch = state.pitchLevel;
        float amp = shake * (0.4f + 0.9f * pitch) * 5f; // 节拍跳动位移
        for (int i = 0; i < PARTICLES; i++) {
            float y = ((state.marqueeT + pPhase[i]) % 1f) * h;
            float jx = (rand.nextFloat() - 0.5f) * 2f * amp;
            float jy = (rand.nextFloat() - 0.5f) * 2f * amp;
            float r = pSize[i] * (1f + energy * 1.4f + (beat ? 0.6f : 0f));
            float a = Math.min(0.95f, base + 0.30f + (beat ? 0.20f : 0f));
            float dotHue = (effHue + pHue[i]) % 360f; // 冷色系内细微色差
            float lx = haloW * 0.55f;
            float rx = w - haloW * 0.55f;
            drawGlowDot(c, lx + jx, y + jy, r, dotHue, energy, b, a);
            drawGlowDot(c, rx + jx, y + jy, r, dotHue, energy, b, a);
        }
        // 高音强调色亮点：高音时沿左右边各一个明亮强调色光点，突出灵动动感
        if (emphasis > 0.05f) {
            float ey = ((state.marqueeT * 1.4f + 0.35f) % 1f) * h;
            float er = haloW * (0.5f + emphasis * 0.6f);
            float ea = Math.min(1f, 0.55f + emphasis * 0.40f);
            drawGlowDot(c, haloW * 0.5f, ey, er, state.pitchHue, energy, b, ea);
            drawGlowDot(c, w - haloW * 0.5f, ey, er, state.pitchHue, energy, b, ea);
        }
    }

    /** 柔光圆点：中心亮、边缘渐隐 */
    private void drawGlowDot(Canvas c, float cx, float cy, float radius,
                             float hue, float energy, float b, float alpha) {
        if (radius < 1f) return;
        paint.setShader(new RadialGradient(cx, cy, radius,
                edgeColor(hue, 0, energy, b, alpha),
                transparent(hue, 0), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
    }

    /** 发射一朵烟花：光柱从屏幕底部升空到爆炸高度，随后粒子径向爆开 */
    private void spawnFirework(int w, int h) {
        int slot = fwSlot;
        fwSlot = (fwSlot + 1) % FW_MAX;
        fwStart[slot] = SystemClock.elapsedRealtime();
        fwCx[slot] = w * (0.15f + rand.nextFloat() * 0.7f);       // 发射横坐标
        fwCy[slot] = h * (0.18f + rand.nextFloat() * 0.28f);      // 爆炸高度：屏幕偏上
        fwSpeed[slot] = 0.5f + rand.nextFloat() * 0.45f;          // 爆开速度
        float base = prefs.lightColorHue();
        fwHue[slot] = base >= 0 ? base : rand.nextFloat() * 360f;
        // 爆炸中心显示"作者名字"：优先歌手，无则歌名（跨线程 volatile 读）
        String nm = state.artist;
        if (nm == null || nm.isEmpty()) nm = state.title;
        fwText[slot] = nm;
        for (int j = 0; j < FW_PARTICLES; j++) {
            float a = (float) (Math.PI * 2 * j / FW_PARTICLES) + rand.nextFloat() * 0.4f;
            float r = 0.75f + rand.nextFloat() * 0.5f;
            fwDirX[slot][j] = (float) Math.cos(a) * r;
            fwDirY[slot][j] = (float) Math.sin(a) * r;
        }
    }

    /** 烟花绘制：先升空（光柱拖尾），再爆炸（径向扩散+轻重力+渐隐） */
    private void drawFireworks(Canvas c, int w, int h, float hue, float b) {
        long now = SystemClock.elapsedRealtime();
        if (state.beat && now - lastFwBeatMs > 1200) {
            lastFwBeatMs = now;
            spawnFirework(w, h);
        } else if (now >= nextFwAt) {
            nextFwAt = now + 2800;
            spawnFirework(w, h);
        }
        float shortSide = Math.min(w, h);
        for (int i = 0; i < FW_MAX; i++) {
            if (fwStart[i] <= 0) continue;
            long el = now - fwStart[i];
            float cx = fwCx[i], burstY = fwCy[i];
            if (el < RISE_MS) {
                // 上升段：底部升到爆炸高度，头部一颗亮星 + 连珠拖尾
                float p = el / (float) RISE_MS;
                float y = h * 1.02f - p * (h * 1.02f - burstY);
                float size = shortSide * 0.014f;
                for (int k = 0; k < 4; k++) {
                    float ty = y - k * size * 1.2f;
                    if (ty < -size) break;
                    float sz = size * (1f - k * 0.16f);
                    float a = Math.max(0f, (1f - p * 0.55f) * (0.85f - k * 0.13f));
                    drawGlowDot(c, cx, ty, sz, fwHue[i], 0f, b, a);
                }
            } else {
                // 爆炸段：粒子沿径向快速散开 + 轻重力 + 渐隐；爆炸中心炸出"作者名字"
                float b2 = (el - RISE_MS) / (float) BURST_MS;
                if (b2 > 1f) { fwStart[i] = 0; continue; }
                for (int j = 0; j < FW_PARTICLES; j++) {
                    float r = fwSpeed[i] * b2 * shortSide * 0.55f;
                    float px = cx + fwDirX[i][j] * r;
                    float py = burstY + fwDirY[i][j] * r + b2 * b2 * shortSide * 0.12f;
                    if (px < 0 || px > w || py < 0 || py > h) continue;
                    float size = shortSide * (0.007f + b2 * 0.022f);         // 先小后大
                    float a = Math.max(0f, Math.min(1f, (1f - b2) * 0.9f));  // 渐隐
                    float fh = fwHue[i] + (j % 7) * 26f - 78f;               // 同色系微差更立体
                    if (fh < 0) fh += 360f;
                    drawGlowDot(c, px, py, size, fh, 0f, b, a);
                }
                // 爆炸中心的作者名：随爆炸轻微放大 + 上浮 + 先亮后渐隐
                if (fwText[i] != null && !fwText[i].isEmpty()) {
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                    paint.setTextSize(shortSide * (0.045f + 0.025f * b2));
                    float ta = Math.max(0f, Math.min(1f, (1f - b2) * 0.95f));
                    paint.setColor(edgeColor(fwHue[i], 0f, 0f, b, ta));
                    float ty = burstY - shortSide * 0.02f - b2 * shortSide * 0.012f;
                    c.drawText(fwText[i], cx, ty, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                    paint.setTypeface(Typeface.DEFAULT);
                }
            }
        }
    }

    /** 键盘环绕：围绕键盘矩形区域的上下左右四条柔光带（沿用，透明度随能量呼吸） */
    private void drawKeyboard(Canvas c, int w, int h, int kbTop, float hue, float energy, float b) {
        float alpha = 0.12f + energy * 0.22f + (state.beat ? 0.14f : 0f);
        alpha = Math.min(alpha, 0.45f);
        float eW = Math.max(4f, Math.min(w, h) * 0.05f);
        int[] off = EDGE_OFFSET;
        drawBand(c, 0, Math.max(0, kbTop - eW), w, eW,
                new LinearGradient(0, kbTop, 0, kbTop - eW,
                        edgeColor(hue, off[0], energy, b, alpha), transparent(hue, off[0]), Shader.TileMode.CLAMP));
        drawBand(c, 0, h - eW, w, eW,
                new LinearGradient(0, h, 0, h - eW,
                        edgeColor(hue, off[1], energy, b, alpha), transparent(hue, off[1]), Shader.TileMode.CLAMP));
        drawBand(c, 0, kbTop, eW, h - kbTop,
                new LinearGradient(0, 0, eW, 0,
                        edgeColor(hue, off[2], energy, b, alpha), transparent(hue, off[2]), Shader.TileMode.CLAMP));
        drawBand(c, w - eW, kbTop, eW, h - kbTop,
                new LinearGradient(w, 0, w - eW, 0,
                        edgeColor(hue, off[3], energy, b, alpha), transparent(hue, off[3]), Shader.TileMode.CLAMP));
    }

    /** 跑马灯：一段霓虹光带沿屏幕边缘顺时针流动，头部亮、尾部渐暗，色相沿光带渐变 */
    private void drawMarquee(Canvas c, int w, int h, float hue, float energy, boolean beat, float b) {
        float perim = 2f * (w + h);
        float bandLen = perim * 0.38f;
        float thick = Math.max(5f, Math.min(w, h) * 0.05f);
        float t = state.marqueeT;
        float seg = bandLen / 28f;
        for (int i = 0; i < 28; i++) {
            float f = (float) i / 27f;
            float arc = (t * perim + f * bandLen) % perim;
            float bandA = (0.30f + energy * 0.25f + (beat ? 0.2f : 0f)) * (1f - f * 0.8f);
            bandA = Math.min(bandA, 0.7f);
            float hueAt = (hue + f * 150f) % 360f;
            drawEdgeDot(c, w, h, arc, seg, thick, hueAt, energy, b, bandA);
        }
    }

    private void drawEdgeDot(Canvas c, int w, int h, float arc, float len, float thick,
                             float hue, float energy, float b, float alpha) {
        float x, y;
        int side;
        if (arc <= w) { x = arc; y = 0; side = 0; }
        else if (arc <= w + h) { x = w; y = arc - w; side = 1; }
        else if (arc <= 2 * w + h) { x = w - (arc - w - h); y = h; side = 2; }
        else { x = 0; y = h - (arc - 2 * w - h); side = 3; }
        x = Math.max(0, Math.min(w, x));
        y = Math.max(0, Math.min(h, y));
        int color = ColorEngine.edgeColor(hue, energy, b, alpha);
        paint.setColor(color);
        paint.setShader(null);
        switch (side) {
            case 0: c.drawRect(x, 0, Math.min(w, x + len), thick, paint); break;
            case 1: c.drawRect(w - thick, y, w, Math.min(h, y + len), paint); break;
            case 2: c.drawRect(x, h - thick, Math.min(w, x + len), h, paint); break;
            default: c.drawRect(0, y, thick, Math.min(h, y + len), paint); break;
        }
    }

    private void drawBand(Canvas c, float l, float t, float width, float height, Shader shader) {
        paint.setShader(shader);
        c.drawRect(l, t, l + width, t + height, paint);
        paint.setShader(null);
    }

    /** 色：alpha = alphaScale × brightness，透明度随能量/节拍/震动真正呼吸；冷色霓虹（紫/青/蓝/品红） */
    private int edgeColor(float hue, float off, float energy, float b, float alphaScale) {
        return ColorEngine.edgeColor(hue + off, energy, b, alphaScale);
    }

    private int transparent(float hue, float off) {
        return ColorEngine.edgeColor(hue + off, 0f, 0f, 0f);
    }
}
