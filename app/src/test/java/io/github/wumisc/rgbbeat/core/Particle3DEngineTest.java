package io.github.wumisc.rgbbeat.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 3D 粒子引擎核心稳定性测试（纯 Java，无需 Android）。 */
public class Particle3DEngineTest {

    private static void step(Particle3DEngine e, int frames, float dt) {
        for (int i = 0; i < frames; i++) {
            e.step(dt, i * 0.05f, 0.3f, i % 24 == 0, 0.5f, 3.5f, 12f, 1.4f, 0f, 0f);
        }
    }

    @Test
    public void cloudsAreFiniteAndBounded() {
        for (float[][] cloud : new float[][][]{
                Particle3DEngine.roseCloud(3000, 12f),
                Particle3DEngine.heartCloud(3000, 12f)}) {
            assertEquals(3000, cloud.length);
            for (float[] p : cloud) {
                assertTrue(Float.isFinite(p[0]) && Float.isFinite(p[1]) && Float.isFinite(p[2]));
                assertTrue(Math.abs(p[0]) <= 17f * 12f * 1.2f);
                assertTrue(Math.abs(p[1]) <= 17f * 12f * 1.2f);
            }
        }
    }

    @Test
    public void particlesConvergeToShape() {
        Particle3DEngine e = new Particle3DEngine(false, 1L);
        step(e, 5, 0.016f);
        double early = worldDistToTarget(e);
        step(e, 500, 0.016f);
        double late = worldDistToTarget(e);
        assertTrue("粒子应收敛到目标曲线: early=" + early + " late=" + late,
                late < early * 0.5f);
    }

    @Test
    public void morphTriggersVisibleParticleMotion() {
        Particle3DEngine e = new Particle3DEngine(false, 7L);
        step(e, 300, 0.016f); // 先收敛
        float[] before = e.sx.clone();
        e.morphTo(Particle3DEngine.heartCloud(e.shapeCount(), 12f));
        step(e, 40, 0.016f);
        double moved = 0;
        for (int i = 0; i < e.shapeCount(); i++) moved += Math.abs(e.sx[i] - before[i]);
        moved /= e.shapeCount();
        assertTrue("变形应驱动粒子明显移动: avgMove=" + moved, moved > 2f);
    }

    @Test
    public void morphAcrossShapesIsStable() {
        Particle3DEngine e = new Particle3DEngine(true, 3L);
        e.morphTo(Particle3DEngine.heartCloud(e.shapeCount(), 12f));
        e.morphTo(Particle3DEngine.roseCloud(e.shapeCount(), 12f));
        step(e, 200, 0.016f);
        step(e, 1, 0.016f); // 不抛异常即通过
    }

    @Test
    public void starfieldFlowsAndStaysFinite() {
        Particle3DEngine e = new Particle3DEngine(true, 5L);
        float prev = 0f;
        boolean grew = false;
        for (int i = 0; i < 100; i++) {
            e.step(0.033f, 0f, 0.9f, false, 0.5f, 3.5f, 12f, 1.4f, 0f, 0f);
            float sum = 0f;
            for (int s = 0; s < e.starCount(); s++) {
                sum += e.ssSize[s];
                assertTrue(Float.isFinite(e.ssx[s]) && Float.isFinite(e.ssy[s]));
            }
            if (i > 0 && sum > prev) grew = true;
            prev = sum;
        }
        assertTrue("星场高速流动时平均星点应变大（近大远小）", grew);
    }

    @Test
    public void rainSpawnsAndResets() {
        Particle3DEngine e = new Particle3DEngine(false, 11L);
        assertEquals(0, e.rainCount());
        e.rain(1.0f);
        assertTrue("节拍应生成粒子雨", e.rainCount() > 0);
        e.rainReset();
        assertEquals(0, e.rainCount());
    }

    @Test
    public void explodeSpreadsThenReconverges() {
        Particle3DEngine e = new Particle3DEngine(false, 13L);
        // 固定呼吸相位=0（breath 恒定 12），充分收敛到平衡
        for (int i = 0; i < 1500; i++) e.step(0.016f, 0f, 0.3f, false, 0.5f, 5.0f, 12f, 1.4f, 0f, 0f);
        double compact = worldSpread(e);
        e.explode();
        for (int i = 0; i < 3; i++) e.step(0.05f, 0f, 0.3f, false, 0.5f, 5.0f, 12f, 1.4f, 0f, 0f);
        double blown = worldSpread(e);
        assertTrue("炸开瞬间粒子应扩散: compact=" + compact + " blown=" + blown, blown > compact);
        for (int i = 0; i < 600; i++) e.step(0.016f, 0f, 0.3f, false, 0.5f, 5.0f, 12f, 1.4f, 0f, 0f);
        double reconverged = worldSpread(e);
        assertTrue("炸开后应重组收敛: blown=" + blown + " reconverged=" + reconverged,
                reconverged < blown);
    }

    @Test
    public void highAndEcoTiersDifferInCount() {
        Particle3DEngine high = new Particle3DEngine(true, 1L);
        Particle3DEngine eco = new Particle3DEngine(false, 1L);
        assertEquals(3000, high.shapeCount());
        assertEquals(700, eco.shapeCount());
        assertTrue(high.starCount() > eco.starCount());
    }

    @Test
    public void reactionSurfaceStable() {
        Particle3DEngine e = new Particle3DEngine(false, 2L);
        assertFalse(Float.isNaN(e.particleHue(0, 100f)));
        e.rain(0.5f);
        e.explode();
        step(e, 5, 0.016f);
    }

    private static double spread(Particle3DEngine e) {
        double s = 0;
        int n = e.shapeCount();
        for (int i = 0; i < n; i++) s += Math.hypot(e.sx[i], e.sy[i]);
        return s / n;
    }

    /** 世界空间：粒子到目标曲线的平均距离（对投影稳健） */
    private static double worldDistToTarget(Particle3DEngine e) {
        float[] x = wf("x", e), y = wf("y", e), z = wf("z", e);
        float[][] t = Particle3DEngine.roseCloud(e.shapeCount(), 1f);
        double sum = 0;
        int n = e.shapeCount();
        for (int i = 0; i < n; i++) {
            double d = Math.sqrt(Math.pow(x[i] - t[i][0], 2)
                    + Math.pow(y[i] - t[i][1], 2) + Math.pow(z[i] - t[i][2], 2));
            sum += d;
        }
        return sum / n;
    }

    /** 世界空间：粒子相对质心的平均展开 */
    private static double worldSpread(Particle3DEngine e) {
        float[] x = wf("x", e), y = wf("y", e), z = wf("z", e);
        int n = e.shapeCount();
        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < n; i++) { cx += x[i]; cy += y[i]; cz += z[i]; }
        cx /= n; cy /= n; cz /= n;
        double s = 0;
        for (int i = 0; i < n; i++) s += Math.sqrt(Math.pow(x[i] - cx, 2)
                + Math.pow(y[i] - cy, 2) + Math.pow(z[i] - cz, 2));
        return s / n;
    }

    private static float[] wf(String name, Particle3DEngine e) {
        try {
            java.lang.reflect.Field f = Particle3DEngine.class.getDeclaredField(name);
            f.setAccessible(true);
            return (float[]) f.get(e);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // ---- 新增形状：星形/音符/菱形 点云有效性与形状区分 ----

    @Test
    public void starNoteDiamondCloudsAreFiniteAndBounded() {
        assertShape(io.github.wumisc.rgbbeat.core.Particle3DEngine.starCloud(500, 1f), "star");
        assertShape(io.github.wumisc.rgbbeat.core.Particle3DEngine.noteCloud(500, 1f), "note");
        assertShape(io.github.wumisc.rgbbeat.core.Particle3DEngine.diamondCloud(500, 1f), "diamond");
        assertShape(io.github.wumisc.rgbbeat.core.Particle3DEngine.roseCloud(500, 1f), "rose");
    }

    private static void assertShape(float[][] c, String name) {
        assertEquals("形状应有足够点数: " + name, 500, c.length);
        for (float[] pt : c) {
            assertTrue(name + " 点应有限: " + java.util.Arrays.toString(pt),
                    Float.isFinite(pt[0]) && Float.isFinite(pt[1]) && Float.isFinite(pt[2]));
            assertTrue(name + " 点越界 x: " + pt[0], Math.abs(pt[0]) <= 20f);
            assertTrue(name + " 点越界 y: " + pt[1], Math.abs(pt[1]) <= 20f);
            assertTrue(name + " 点越界 z: " + pt[2], Math.abs(pt[2]) <= 2f);
        }
        // 形状应有明显轮廓展开（非退化）：x 范围 > 12
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float[] pt : c) { min = Math.min(min, pt[0]); max = Math.max(max, pt[0]); }
        assertTrue(name + " x 展开不足: " + (max - min), max - min > 12f);
        float ymin = Float.MAX_VALUE, ymax = -Float.MAX_VALUE;
        for (float[] pt : c) { ymin = Math.min(ymin, pt[1]); ymax = Math.max(ymax, pt[1]); }
        assertTrue(name + " y 展开不足: " + (ymax - ymin), ymax - ymin > 12f);
    }
}
