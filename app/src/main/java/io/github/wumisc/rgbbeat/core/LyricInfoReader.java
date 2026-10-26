package io.github.wumisc.rgbbeat.core;

import android.media.session.MediaController;
import android.os.Bundle;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LyricInfo（limczhh/LyricInfo，LSPosed 歌词模块）对接：
 * 它把音乐应用的歌曲信息+歌词整理为统一 JSON 写入 MediaMetadata.extras.lyricInfo，
 * 字段：songName / artist / lyric（逐行 LRC）/ translation（可选翻译）。
 *
 * 本类负责：
 *  · 从 MediaController 元数据读取并解析 lyricInfo JSON（全反射：本环境 android.jar
 *    为裁剪版缺 MediaMetadata/PlaybackState 类，运行时真机完整可用）；
 *  · 解析 LRC 时间轴（支持一行多个 [mm:ss.xx] 时间戳）；
 *  · 按播放进度定位当前/上一行/下一行歌词。
 *
 * LyricInfo 未安装或未写元数据时返回 null，调用方自动降级为普通歌名/歌手。
 */
public final class LyricInfoReader {

    // MediaMetadata 常量值（API 21+ 稳定，反射使用字符串字面量）
    static final String KEY_TITLE = "android.media.metadata.TITLE";
    static final String KEY_ARTIST = "android.media.metadata.ARTIST";
    static final String KEY_EXTRAS = "android.media.metadata.EXTRAS";

    /** 单个歌词行：时间(ms) + 文本 */
    public static final class LyricLine {
        public final long timeMs;
        public final String text;
        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    /** lyricInfo JSON 解析结果 */
    public static final class LyricInfo {
        public String songName = "";
        public String artist = "";
        public String lyric = "";        // 原文 LRC
        public String translation = "";  // 独立翻译 LRC（可选）
        public boolean usable() {
            return (!songName.isEmpty() || !lyric.isEmpty());
        }
    }

    private static final Pattern TS = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    /** 反射读取 controller.getMetadata() 的 extras Bundle（真机返回 MediaMetadata） */
    private static Bundle metadataExtras(MediaController controller) {
        try {
            Method m = MediaController.class.getMethod("getMetadata");
            Object md = m.invoke(controller);
            if (md == null) return null;
            Method gb = md.getClass().getMethod("getBundle", String.class);
            return (Bundle) gb.invoke(md, KEY_EXTRAS);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从 MediaController 元数据读取 lyricInfo JSON；不可用返回 null */
    public static LyricInfo read(MediaController controller) {
        try {
            if (controller == null) return null;
            Bundle extras = metadataExtras(controller);
            if (extras == null) return null;
            String json = extras.getString("lyricInfo");
            if (json == null || json.isEmpty()) return null;
            JSONObject o = new JSONObject(json);
            LyricInfo li = new LyricInfo();
            li.songName = o.optString("songName", "");
            li.artist = o.optString("artist", "");
            li.lyric = o.optString("lyric", "");
            li.translation = o.optString("translation", "");
            if (li.usable()) return li;
        } catch (Throwable ignored) { }
        return null;
    }

    /** 反射读取普通歌名/歌手（LyricInfo 不可用时的兜底） */
    public static String[] readTitleArtist(MediaController controller) {
        try {
            Method m = MediaController.class.getMethod("getMetadata");
            Object md = m.invoke(controller);
            if (md == null) return null;
            Method gs = md.getClass().getMethod("getString", String.class);
            String t = (String) gs.invoke(md, KEY_TITLE);
            if (t != null && !t.isEmpty()) {
                String a = (String) gs.invoke(md, KEY_ARTIST);
                return new String[]{t, a};
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** 反射读取播放进度(ms)；不可得返回 -1 */
    public static long readPositionMs(MediaController controller) {
        try {
            Method pm = MediaController.class.getMethod("getPlaybackState");
            Object ps = pm.invoke(controller);
            if (ps == null) return -1;
            Method gp = ps.getClass().getMethod("getPosition");
            Object v = gp.invoke(ps);
            if (v instanceof Long) return (Long) v;
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) { }
        return -1;
    }

    /** 解析 LRC：返回按时间排序的歌词行（跳过元数据行，一行多时间戳会拆开） */
    public static ArrayList<LyricLine> parseLrc(String lrc) {
        ArrayList<LyricLine> out = new ArrayList<>();
        if (lrc == null || lrc.isEmpty()) return out;
        String[] raw = lrc.split("\\r?\\n");
        for (String line : raw) {
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = TS.matcher(line);
            ArrayList<Long> stamps = new ArrayList<>();
            while (m.find()) {
                try {
                    int min = Integer.parseInt(m.group(1));
                    int sec = Integer.parseInt(m.group(2));
                    int frac = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
                    long ms = min * 60000L + sec * 1000L;
                    // 分数位：2 位为百分秒（×10ms），3 位为毫秒
                    ms += frac < 100 ? frac * 10L : frac;
                    stamps.add(ms);
                } catch (NumberFormatException ignored) { }
            }
            if (stamps.isEmpty()) continue; // [ti:xxx] 等元数据行跳过
            String text = line.replaceAll("\\[[^\\]]*\\]", "").trim();
            for (Long s : stamps) out.add(new LyricLine(s, text));
        }
        Collections.sort(out, new Comparator<LyricLine>() {
            @Override public int compare(LyricLine a, LyricLine b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return out;
    }

    /** 定位 posMs 处歌词行下标；无则 -1 */
    public static int findLine(ArrayList<LyricLine> lines, long posMs) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (posMs >= lines.get(i).timeMs) idx = i;
            else break;
        }
        return idx;
    }
}
