package io.github.wumisc.rgbbeat.render;

import io.github.wumisc.rgbbeat.data.*;
import io.github.wumisc.rgbbeat.engine.*;
import io.github.wumisc.rgbbeat.render.*;
import io.github.wumisc.rgbbeat.service.*;

/**
 * 礼物特效的「背景抠除模式」。纯 Java，便于 JVM 单元测试。
 *
 * 旧版只按亮度抠黑，遇到白底 / 灰底 / 渐变雾状背景会把整块背景当成亮部特效保留，
 * 在界面上糊一层。这里把模式独立出来，设置页可切换，默认 auto 通吃各种底色。
 */
public final class EffectKeyMode {

    public static final String AUTO = "auto";
    public static final String DARK = "dark";
    public static final String LIGHT = "light";
    public static final String OFF = "off";

    /** 与 EffectVideoView 片元着色器 uMode 一一对应 */
    public static final int MODE_AUTO = 0;
    public static final int MODE_DARK = 1;
    public static final int MODE_LIGHT = 2;
    public static final int MODE_OFF = 3;

    /** 与 res/values/arrays.xml 的 highlight_key_values 保持一致 */
    public static final String[] VALUES = {AUTO, DARK, LIGHT, OFF};

    private EffectKeyMode() { }

    /** 非法值一律回落到自动，避免 Preference 写入异常值导致 shader 走错分支 */
    public static String normalize(String s) {
        if (s == null) return AUTO;
        for (String v : VALUES) if (v.equals(s)) return s;
        return AUTO;
    }

    public static int toShaderMode(String s) {
        String v = normalize(s);
        if (DARK.equals(v)) return MODE_DARK;
        if (LIGHT.equals(v)) return MODE_LIGHT;
        if (OFF.equals(v)) return MODE_OFF;
        return MODE_AUTO;
    }
}
