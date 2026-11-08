package io.github.wumisc.rgbbeat.core;

/**
 * 礼物特效的等比居中计算（纯 Java，不依赖 Android 运行时，便于 JVM 单元测试）。
 *
 * 三段素材都是横屏（1568x720 / 992x720 / 896x528），直接铺到竖屏 MATCH_PARENT 的
 * TextureView 会被拉伸或裁切，用户只能看到局部。这里统一算出 FIT_CENTER 的 NDC 半尺寸，
 * 保证「完整呈现、居中、不裁切、不变形」。
 */
public final class EffectLayout {

    private EffectLayout() { }

    /**
     * @param viewW  渲染层宽（悬浮窗，通常为竖屏）
     * @param viewH  渲染层高
     * @param videoW 视频原始宽
     * @param videoH 视频原始高
     * @return NDC 坐标下的 {半宽, 半高}，均在 (0,1]，且至少一维等于 1（即刚好撑满，不裁切）
     */
    public static float[] fit(int viewW, int viewH, int videoW, int videoH) {
        float sx = 1f, sy = 1f;
        if (viewW > 0 && viewH > 0 && videoW > 0 && videoH > 0) {
            float viewAspect = (float) viewW / viewH;
            float videoAspect = (float) videoW / videoH;
            if (videoAspect >= viewAspect) {
                // 视频更宽：宽度撑满，上下留透明
                sy = viewAspect / videoAspect;
            } else {
                // 视频更高：高度撑满，左右留透明
                sx = videoAspect / viewAspect;
            }
        }
        return new float[]{sx, sy};
    }
}
