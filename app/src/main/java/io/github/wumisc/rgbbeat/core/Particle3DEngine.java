package io.github.wumisc.rgbbeat.core;

import java.util.Random;

/**
 * 3D 粒子氛围引擎（纯 Java 核心，无 Android 依赖，便于单元测试与桌面预览复用）。
 *
 * 结构：
 *  - 形状点云：玫瑰 / 爱心 用 2D 心形参数曲线叠加深度波生成 3D 点云；歌手名点云由
 *    外部 {@link CloudSource} 按需提供（Android 用 Paint+Bitmap 采样，预览用 Java2D）。
 *  - 主角粒子：N 个粒子从随机位置加速收敛到目标点云，随时间绕 Y 轴旋转 + 呼吸缩放，
 *    透视投影（近大远小、近亮远暗）。
 *  - 变形（Morph）：换歌时目标点云在玫瑰↔爱心↔歌手名间插值漂移（基于歌曲切换检测）。
 *  - 强反应：节拍→粒子雨（顶部飘落）；高音/强拍→粒子向外炸开再重组。
 *  - 背景星场：星际穿越感——粒子沿 Z 轴向观察者飞行，能量越高速率越快。
 *
 * 两档性能：高画质（形状 3000 + 星场 1000）/ 节电（形状 700 + 星场 300），算法同一套。
 */
public final class Particle3DEngine {

    // ---- 形状点云生成（纯数学）----
    /** 心形曲线 2D 点（x∈[-16,16], y∈[-17,12]），z 叠加深度波形成 3D 花体 */
    private static void heart2D(double t, double[] xy) {
        double x = 16 * Math.pow(Math.sin(t), 3);
        double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
        xy[0] = x; xy[1] = y;
    }

    /** 玫瑰：五瓣花曲线（r=16·cos5θ），z 叠花瓣层次深度 → 真花外观，区别于心形 */
    public static float[][] roseCloud(int n, float scale) {
        float[][] p = new float[n][3];
        for (int i = 0; i < n; i++) {
            double t = Math.PI * i / (n - 1); // 0..π（五瓣）
            double r = Math.abs(16 * Math.cos(5 * t));
            p[i][0] = (float) (r * Math.cos(t) * scale);
            p[i][1] = (float) (r * Math.sin(t) * scale);
            p[i][2] = (float) (Math.cos(5 * t) * scale * 0.5);
        }
        return p;
    }

    /** 爱心：心形曲线 + 更浅深度（辨识度优先） */
    public static float[][] heartCloud(int n, float scale) {
        float[][] p = new float[n][3];
        double[] xy = new double[2];
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            heart2D(t, xy);
            p[i][0] = (float) (xy[0] * scale);
            p[i][1] = (float) (-xy[1] * scale);
            p[i][2] = (float) (Math.sin(t) * scale * 0.22);
        }
        return p;
    }

    /** 五角星：十顶点外/内交替轮廓，深度行波 */
    public static float[][] starCloud(int n, float scale) {
        float[][] p = new float[n][3];
        int verts = 10;
        double R = 16, r = 6.5;
        double[][] v = new double[verts][2];
        for (int i = 0; i < verts; i++) {
            double a = -Math.PI / 2 + i * Math.PI / verts; // 顶点朝上
            double rad = (i % 2 == 0) ? R : r;
            v[i][0] = rad * Math.cos(a);
            v[i][1] = rad * Math.sin(a);
        }
        for (int i = 0; i < n; i++) {
            double t = i * verts / (double) n;
            int i0 = (int) t; if (i0 >= verts) i0 = verts - 1;
            double f = t - i0;
            int i1 = (i0 + 1) % verts;
            double x = v[i0][0] + (v[i1][0] - v[i0][0]) * f;
            double y = v[i0][1] + (v[i1][1] - v[i0][1]) * f;
            p[i][0] = (float) (x * scale);
            p[i][1] = (float) (y * scale);
            p[i][2] = (float) (Math.sin(t * Math.PI / verts) * scale * 0.45);
        }
        return p;
    }

    /** 菱形：四边轮廓，深度行波 */
    public static float[][] diamondCloud(int n, float scale) {
        float[][] p = new float[n][3];
        double[][] v = {{0, -16}, {11, 0}, {0, 16}, {-11, 0}};
        for (int i = 0; i < n; i++) {
            double t = i * 4.0 / n;
            int i0 = (int) t; if (i0 >= 4) i0 = 3;
            double f = t - i0;
            int i1 = (i0 + 1) % 4;
            double x = v[i0][0] + (v[i1][0] - v[i0][0]) * f;
            double y = v[i0][1] + (v[i1][1] - v[i0][1]) * f;
            p[i][0] = (float) (x * scale);
            p[i][1] = (float) (y * scale);
            p[i][2] = (float) (Math.sin(t * Math.PI / 2) * scale * 0.35);
        }
        return p;
    }

    /** 音符（四分音符）：音符头圆 + 符干 + 符尾，辨识度高 */
    public static float[][] noteCloud(int n, float scale) {
        float[][] p = new float[n][3];
        int headN = (int) (n * 0.45), stemN = (int) (n * 0.25), flagN = n - headN - stemN;
        double hx = 0, hy = -2, hr = 3.8;   // 音符头
        for (int i = 0; i < headN; i++) {
            double a = 2 * Math.PI * i / headN;
            p[i][0] = (float) ((hx + hr * Math.cos(a)) * scale);
            p[i][1] = (float) ((hy + hr * Math.sin(a)) * scale);
            p[i][2] = (float) (Math.sin(a) * scale * 0.25);
        }
        double sx = hr, sy0 = hy - hr, sy1 = -14; // 符干（竖线）
        for (int i = 0; i < stemN; i++) {
            double f = i / (double) stemN;
            p[headN + i][0] = (float) (sx * scale);
            p[headN + i][1] = (float) ((sy0 + (sy1 - sy0) * f) * scale);
            p[headN + i][2] = (float) (Math.sin(f * 3) * scale * 0.2);
        }
        for (int i = 0; i < flagN; i++) {   // 符尾（右弯曲线）
            double f = i / (double) Math.max(1, flagN - 1);
            p[headN + stemN + i][0] = (float) ((sx + 5 * Math.sin(f * Math.PI / 2)) * scale);
            p[headN + stemN + i][1] = (float) ((sy1 + 4 * Math.sin(f * Math.PI / 2)) * scale);
            p[headN + stemN + i][2] = (float) (Math.sin(f * Math.PI) * scale * 0.25);
        }
        return p;
    }

    /** 外部点云来源：歌手名等需字体栅格化的形状 */
    public interface CloudSource {
        float[][] pointsFor(String text, int n);
    }

    // ---- 状态 ----
    private final int nShape;          // 主角粒子数
    private final int nStar;           // 星场粒子数
    private final Random rand;

    // 主角粒子：3D 位置 / 速度 / 目标
    private final float[] x, y, z, vx, vy, vz, tx, ty, tz;
    private final float[] hueOff;      // 每粒子色相差
    private final float[] sizeBase;    // 每粒子基础大小

    // 变形插值：旧目标→新目标
    private float[][] targetA, targetB; // 当前 / 下一目标点云
    private float morphT = 1f;          // 0..1 插值进度
    private float morphDur = 1.2f;      // 秒

    // 屏幕输出（step 后供渲染器读取）
    public final float[] sx, sy;        // 投影后屏幕坐标
    public final float[] sSize;         // 屏幕大小
    public final float[] sAlpha;        // 透明度
    public final float[] sDepth;        // 深度（近大远小）

    // 星场输出
    private final float[] starX, starY, starZ;
    public final float[] ssx, ssy, ssSize, ssAlpha;

    // 粒子雨（节拍触发）：垂直下落临时粒子
    public static final int RAIN_MAX = 60;
    private final float[] rainX, rainY, rainV, rainLife, rainHue;
    private int rainCount = 0;

    // 呼吸 & 旋转
    private float breathPhase = 0f;
    private float rotY = 0f;
    private float rotSpeed = 0.12f;  // 摇摆幅度（rad），小幅度保持形状正面可读
    private float tiltX = 0.06f;     // 俯仰（rad），极缓俯仰保留立体纵深又不压扁形状

    /** 调整摇摆幅度（rad，越大左右摆幅越大） */
    public void setRotSpeed(float rad) { rotSpeed = rad; }

    /** 调整俯仰角（rad，越大越"躺"） */
    public void setTiltX(float rad) { tiltX = rad; }

    private boolean exploded = false;  // 高音炸开状态

    /** @param high  true=高画质(3000+1000) false=节电(700+300) */
    public Particle3DEngine(boolean high, long seed) {
        nShape = high ? 3000 : 700;
        nStar = high ? 1000 : 300;
        rand = new Random(seed);

        x = new float[nShape]; y = new float[nShape]; z = new float[nShape];
        vx = new float[nShape]; vy = new float[nShape]; vz = new float[nShape];
        tx = new float[nShape]; ty = new float[nShape]; tz = new float[nShape];
        hueOff = new float[nShape]; sizeBase = new float[nShape];
        sx = new float[nShape]; sy = new float[nShape];
        sSize = new float[nShape]; sAlpha = new float[nShape]; sDepth = new float[nShape];

        starX = new float[nStar]; starY = new float[nStar]; starZ = new float[nStar];
        ssx = new float[nStar]; ssy = new float[nStar]; ssSize = new float[nStar]; ssAlpha = new float[nStar];
        rainX = new float[RAIN_MAX]; rainY = new float[RAIN_MAX]; rainV = new float[RAIN_MAX];
        rainLife = new float[RAIN_MAX]; rainHue = new float[RAIN_MAX];

        for (int i = 0; i < nShape; i++) {
            hueOff[i] = rand.nextFloat() * 360f;
            sizeBase[i] = 0.6f + rand.nextFloat() * 1.1f;
            // 初始位置随机铺满屏幕
            x[i] = (rand.nextFloat() - 0.5f) * 40f;
            y[i] = (rand.nextFloat() - 0.5f) * 40f;
            z[i] = (rand.nextFloat() - 0.5f) * 20f;
        }
        for (int i = 0; i < nStar; i++) initStar(i);
        // 默认形状：玫瑰
        setTarget(roseCloud(nShape, 1f), true);
    }

    private void initStar(int i) {
        starX[i] = (rand.nextFloat() - 0.5f) * 30f;
        starY[i] = (rand.nextFloat() - 0.5f) * 30f;
        starZ[i] = 10f + rand.nextFloat() * 40f; // 深处
    }

    public int shapeCount() { return nShape; }
    public int starCount() { return nStar; }

    /** 切换到新形状点云（Morph：粒子从当前形态漂移到新形态） */
    public void morphTo(float[][] cloud) {
        // 先读出当前插值后的目标点云（旧 targetA/targetB 不变，安全读取）
        float[][] cur = currentCloud();
        targetA = cur;
        targetB = cloud;
        morphT = 0f;
    }

    /** 读取当前（含进行中插值）的目标点云 */
    private float[][] currentCloud() {
        float[][] out = new float[nShape][3];
        for (int i = 0; i < nShape; i++) {
            float[] c = currentTarget(i);
            out[i][0] = c[0]; out[i][1] = c[1]; out[i][2] = c[2];
        }
        return out;
    }

    private void setTarget(float[][] cloud, boolean instant) {
        targetA = new float[nShape][];
        for (int i = 0; i < nShape; i++) targetA[i] = cloud[i].clone();
        targetB = cloud;
        morphT = instant ? 1f : 0f;
    }

    private float[] currentTarget(int i) {
        if (morphT >= 1f) return targetB[i];
        float k = morphT;
        float[] a = targetA[i], b = targetB[i];
        return new float[]{
                a[0] + (b[0] - a[0]) * k,
                a[1] + (b[1] - a[1]) * k,
                a[2] + (b[2] - a[2]) * k};
    }

    // ---- 对外反应接口 ----
    /** 节拍粒子雨：顶部洒落明亮粒子 */
    public void rain(float energy) {
        int want = 4 + (int) (energy * 14f);
        for (int i = 0; i < RAIN_MAX && want-- > 0; i++) {
            if (rainLife[i] <= 0f) {
                rainX[i] = (rand.nextFloat() - 0.5f) * 46f;
                rainY[i] = 24f + rand.nextFloat() * 4f; // 屏幕顶
                rainV[i] = 6f + rand.nextFloat() * 6f;
                rainLife[i] = 1.2f + rand.nextFloat() * 0.8f;
                rainHue[i] = rand.nextFloat() * 360f;
            }
        }
    }

    /** 高音 / 强拍炸开：粒子向外飞，随后被吸引重组 */
    public void explode() {
        exploded = true;
        float cx = 0f, cy = 0f;
        for (int i = 0; i < nShape; i++) { cx += x[i]; cy += y[i]; }
        cx /= nShape; cy /= nShape;
        float k = 8f;
        for (int i = 0; i < nShape; i++) {
            float dx = x[i] - cx, dy = y[i] - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 1f;
            vx[i] += dx / d * k * (0.5f + rand.nextFloat());
            vy[i] += dy / d * k * (0.5f + rand.nextFloat());
            vz[i] += (rand.nextFloat() - 0.5f) * 4f;
        }
    }

    /** 主步进：dt 秒。energy/beat/pitch 驱动反应 */
    public void step(float dt, float breathPhaseIn, float energy, boolean beat, float pitch,
                      float fov, float worldBase, float worldAmp, float cx, float cy) {
        this.breathPhase = breathPhaseIn;
        float breath = worldBase + worldAmp * (float) Math.sin(breathPhase);
        // 轻微摇摆（非整圈自转）：保持花朵面向观者，透视 + 俯仰提供 3D 纵深，形状始终可辨识
        rotY = rotSpeed * (float) Math.sin(breathPhase);
        float cosY = (float) Math.cos(rotY), sinY = (float) Math.sin(rotY);
        float cosX = (float) Math.cos(tiltX), sinX = (float) Math.sin(tiltX); // 俯仰增强立体感

        if (morphT < 1f) morphT = Math.min(1f, morphT + dt / morphDur);

        // 高音炸开后自动回收（吸引力把粒子拉回，exploded 仅用于短暂增亮）
        exploded = false;

        for (int i = 0; i < nShape; i++) {
            float[] t = currentTarget(i);
            // 吸引力（加速收敛到形状点云）+ 阻尼；breath 仅在投影时应用，避免双重缩放
            float ax = (t[0] - x[i]) * 4.0f;
            float ay = (t[1] - y[i]) * 4.0f;
            float az = (t[2] - z[i]) * 4.0f;
            vx[i] += ax * dt; vy[i] += ay * dt; vz[i] += az * dt;
            vx[i] *= 0.90f; vy[i] *= 0.90f; vz[i] *= 0.90f;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;

            // 绕 Y 轴 + 俯仰旋转（对世界坐标做旋转再投影）
            float wx = x[i], wy = y[i], wz = z[i];
            float x1 = wx * cosY + wz * sinY;
            float z1 = -wx * sinY + wz * cosY;
            float y1 = wy * cosX - z1 * sinX;
            float z2 = wy * sinX + z1 * cosX;

            float zden = fov + z2;
            float scale;
            if (zden < 0.35f) {
                // 粒子转到相机后方/贴脸：隐藏（alpha=0），让它从花后穿过去，避免近距放大糊片
                scale = 0.3f;
                sAlpha[i] = 0f;
            } else {
                scale = fov / zden;
                if (scale > 6f) scale = 6f;
                sAlpha[i] = (0.35f + 0.55f * scale) * (0.75f + 0.35f * energy);
            }
            sx[i] = cx + x1 * scale * breath;
            sy[i] = cy + y1 * scale * breath;
            sDepth[i] = scale; // 1 近 ~0 远
            sSize[i] = sizeBase[i] * (0.7f + 0.9f * scale) * (1f + energy * 0.5f);
            if (exploded) sAlpha[i] = Math.min(1f, sAlpha[i] + 0.3f);
        }

        // 星场：星际穿越
        float starSpeed = (5f + energy * 14f) * dt;
        for (int i = 0; i < nStar; i++) {
            starZ[i] -= starSpeed;
            if (starZ[i] < 2f) initStar(i);
            float sc = fov / starZ[i];
            ssx[i] = cx + starX[i] * sc * 0.6f;
            ssy[i] = cy + starY[i] * sc * 0.6f;
            ssSize[i] = 1.2f + 2.6f / starZ[i];
            ssAlpha[i] = Math.min(1f, 0.9f / starZ[i]);
        }

        // 粒子雨：垂直下落 + 渐隐
        for (int i = 0; i < RAIN_MAX; i++) {
            if (rainLife[i] <= 0f) continue;
            rainY[i] -= rainV[i] * dt;
            rainLife[i] -= dt;
            if (rainY[i] < -26f || rainLife[i] <= 0f) { rainLife[i] = 0f; rainCount = 0; }
        }
    }

    /** 渲染器遍历主角粒子：hueBase 为该粒子最终色相 */
    public float particleHue(int i, float hueBase) {
        return (hueBase + hueOff[i]) % 360f;
    }

    /** 渲染器读取粒子雨（x,y 世界坐标，需自行投影；此处给 y 已直接用于屏幕竖直坐标近似） */
    public float rainX(int i) { return rainX[i]; }
    public float rainY(int i) { return rainY[i]; }
    public float rainLife(int i) { return rainLife[i]; }
    public float rainHue(int i) { return rainHue[i]; }
    public int rainCount() { int c = 0; for (float l : rainLife) if (l > 0) c++; return c; }
    public void rainReset() { for (int i = 0; i < RAIN_MAX; i++) rainLife[i] = 0f; }
}
