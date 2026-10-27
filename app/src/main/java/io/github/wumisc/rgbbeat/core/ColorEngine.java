package io.github.wumisc.rgbbeat.core;

import android.graphics.Color;

/**
 * 色彩引擎：在 HSV 色轮上生成律动颜色。
 * v3.0 改进：更低饱和度 + 柔化处理，更接近 Miuix 风格的柔和高级感。
 */
public final class ColorEngine {

    private static final float SAT = 0.62f;  // 饱和度：降低，柔和高级不刺眼
    private static final float VAL = 0.92f;  // HSV 亮度：略降，避免过曝

    /** 根据当前色相与能量，计算某条边的 ARGB 颜色 */
    public static int edgeColor(float hue, float energy, float brightness, float alphaScale) {
        float a = clamp01(alphaScale) * 255f * (brightness / 255f);
        int rgb = Color.HSVToColor(new float[]{wrapHue(hue), SAT, VAL});
        // 拆出 RGB 并叠加透明度
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (argbByte((int) a) << 24) | (r << 16) | (g << 8) | b;
    }

    /** 纯色不透明版本（用于中心高亮） */
    public static int solidColor(float hue) {
        return Color.HSVToColor(new float[]{wrapHue(hue), SAT, VAL});
    }

    private static float wrapHue(float h) {
        h = h % 360f;
        return h < 0 ? h + 360f : h;
    }

    private static int argbByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
