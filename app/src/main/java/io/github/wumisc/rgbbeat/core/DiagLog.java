package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 调试日志落盘工具：在保留 logcat 的同时把日志写入文件，便于无需 adb 直接查看。
 * 优先尝试写入公共下载目录 /sdcard/Download/rgbbeat_diag.log（文件管理器可直接打开），
 * 无存储权限时自动回退到应用私有目录 files/rgbbeat_diag.log，并可通过 App 内
 * "查看调试日志"按钮读取显示。
 */
public final class DiagLog {

    private static final String TAG = "RGBBeat/DiagLog";
    private static final long MAX_BYTES = 512 * 1024; // 超过则轮转保留 .1
    private static File file;

    private DiagLog() { }

    /** 初始化落盘路径（幂等）。MainActivity / BeatService 均可调用。 */
    public static void init(Context c) {
        if (file != null) return;
        try {
            File sdcard = new File("/sdcard/Download/rgbbeat_diag.log");
            file = tryWritable(sdcard)
                    ? sdcard
                    : new File(c.getFilesDir(), "rgbbeat_diag.log");
        } catch (Throwable t) {
            file = new File(c.getFilesDir(), "rgbbeat_diag.log");
        }
        Log.i(TAG, "diag log -> " + file.getAbsolutePath());
    }

    private static boolean tryWritable(File f) {
        try {
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            if (!f.exists()) f.createNewFile();
            return f.canWrite();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 写一条日志：同时输出到 logcat 与日志文件；写文件失败不影响功能 */
    public static void d(String tag, String msg) {
        Log.i(tag, msg);
        append(tag, msg);
    }

    /** 写一条警告/错误日志：Log.w + 落盘 */
    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        append(tag, msg);
    }

    private static void append(String tag, String msg) {
        try {
            if (file == null) return;
            String line = new SimpleDateFormat("MM-dd HH:mm:ss.SSS").format(new Date())
                    + " [" + tag + "] " + msg + "\n";
            synchronized (DiagLog.class) {
                if (file.length() > MAX_BYTES) rotate();
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    fos.write(line.getBytes("UTF-8"));
                }
            }
        } catch (Throwable t) {
            // 日志写入失败绝不干扰光效/震动主流程
        }
    }

    private static void rotate() throws Exception {
        File bak = new File(file.getAbsolutePath() + ".1");
        if (bak.exists()) bak.delete();
        if (file.exists()) file.renameTo(bak);
        file.createNewFile();
    }

    /** 读取最近 maxLines 行（App 内"查看调试日志"使用，无需 adb） */
    public static String readLast(int maxLines) {
        if (file == null || !file.exists()) {
            return "(暂无日志：日志文件尚未建立)";
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            if (len == 0) return "(日志为空)";
            // 只读尾部最多 64KB，避免大日志拖慢 UI
            long pos = Math.max(0, len - 64 * 1024);
            if (pos > 0) raf.seek(pos);
            byte[] buf = new byte[(int) (len - pos)];
            raf.readFully(buf);
            String tail = new String(buf, "UTF-8");
            String[] lines = tail.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, lines.length - maxLines); i < lines.length; i++) {
                sb.append(lines[i]).append('\n');
            }
            return sb.toString();
        } catch (Throwable t) {
            return "(读取日志失败: " + t + ")";
        }
    }

    public static String path() {
        return file != null ? file.getAbsolutePath() : "(未初始化)";
    }
}
