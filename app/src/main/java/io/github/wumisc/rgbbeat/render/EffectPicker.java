package io.github.wumisc.rgbbeat.render;

import io.github.wumisc.rgbbeat.data.*;
import io.github.wumisc.rgbbeat.engine.*;
import io.github.wumisc.rgbbeat.render.*;
import io.github.wumisc.rgbbeat.service.*;

/**
 * 高潮礼物特效的选择逻辑（纯 Java，不依赖 Android 运行时，便于 JVM 单元测试）。
 *
 * 原来「切到下一个特效」的索引直接写在渲染器里，且 stop() 会把索引重置为 -1，
 * 结果每次都算出 0，三段视频只有第一段能播。这里把轮播规则独立出来并加回归测试。
 */
public final class EffectPicker {

    public static final String CYCLE = "cycle";
    public static final String ORBIT = "orbit";
    public static final String OCEAN = "ocean";
    public static final String PHOENIX = "phoenix";

    /** 与 res/values/arrays.xml 的 highlight_effect_values 保持一致 */
    public static final String[] VALUES = {CYCLE, ORBIT, OCEAN, PHOENIX};

    public static final int COUNT = 3;

    private EffectPicker() { }

    /**
     * @param selection 设置值：cycle / orbit / ocean / phoenix，null 或未知值按 cycle 处理
     * @param current   上一次播放的索引（-1 表示尚未播放过）
     * @return          本次应播放的特效索引 0..2
     */
    public static int resolve(String selection, int current) {
        if (ORBIT.equals(selection)) return 0;
        if (OCEAN.equals(selection)) return 1;
        if (PHOENIX.equals(selection)) return 2;
        int next = (current + 1) % COUNT;
        if (next < 0) next += COUNT;
        return next;
    }

    /** 设置值合法性校验（防止 Preference 写入异常值） */
    public static String normalize(String selection) {
        if (selection == null) return CYCLE;
        for (String v : VALUES) if (v.equals(selection)) return selection;
        return CYCLE;
    }
}
