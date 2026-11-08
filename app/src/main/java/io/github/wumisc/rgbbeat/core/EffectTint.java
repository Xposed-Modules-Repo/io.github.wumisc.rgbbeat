package io.github.wumisc.rgbbeat.core;

/**
 * 礼物特效统一染色表（纯 Java，便于 JVM 单元测试）。
 *
 * 三段素材原本颜色不一（白/蓝线稿、蓝色水墨、金色凤凰），在浅色界面上白色线条
 * 尤其不明显。这里把抠背景后保留下来的前景按亮度映射到同一种颜色，
 * 暗部保留深色层次、亮部带白芯，三段观感统一。
 *
 * 默认「白里粉」：亮部收成白芯、中间调透出粉，像礼物特效的珠光质感，
 * 在浅色界面上也不会糊成一团玫红。
 */
public final class EffectTint {

    public static final String WHITE_PINK = "whitepink"; // 白里粉（默认）
    public static final String HEART = "heart";     // 心形粉 #FF29B7
    public static final String VIOLET = "violet";   // 玫瑰紫 #B92EFF
    public static final String CYAN = "cyan";       // 极光青 #29DBFF
    public static final String BLUE = "blue";       // 深海蓝 #294DFF
    public static final String ORANGE = "orange";   // 鎏金橙 #FF9429
    public static final String GREEN = "green";     // 荧光绿 #29FF29
    public static final String RED = "red";         // 中国红 #FF2A2A
    public static final String NONE = "none";       // 原色，不染色

    public static final String DEFAULT = WHITE_PINK;

    /** 与 res/values/arrays.xml 的 highlight_tint_values 顺序一致 */
    public static final String[] VALUES = {
            WHITE_PINK, HEART, VIOLET, CYAN, BLUE, ORANGE, GREEN, RED, NONE
    };

    private EffectTint() { }

    public static String normalize(String s) {
        if (s == null) return DEFAULT;
        for (String v : VALUES) if (v.equals(s)) return s;
        return DEFAULT;
    }

    /** 返回 0xRRGGBB；NONE 返回 -1 表示不染色 */
    public static int colorOf(String s) {
        switch (normalize(s)) {
            case HEART:  return 0xFF29B7;
            case VIOLET: return 0xB92EFF;
            case CYAN:   return 0x29DBFF;
            case BLUE:   return 0x294DFF;
            case ORANGE: return 0xFF9429;
            case GREEN:  return 0x29FF29;
            case RED:    return 0xFF2A2A;
            case NONE:   return -1;
            case WHITE_PINK:
            default:     return 0xFFB7D8;
        }
    }

    /**
     * @return {r, g, b, mix}，前三项 0..1；mix=0 表示保留素材原色，mix=1 表示完全染色
     */
    public static float[] resolve(String s) {
        int c = colorOf(s);
        if (c < 0) return new float[]{1f, 1f, 1f, 0f};
        return new float[]{
                ((c >> 16) & 0xFF) / 255f,
                ((c >> 8) & 0xFF) / 255f,
                (c & 0xFF) / 255f,
                1f
        };
    }
}
