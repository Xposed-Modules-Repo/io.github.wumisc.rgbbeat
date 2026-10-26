package io.github.wumisc.rgbbeat.core;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;

/**
 * 音频采集：优先使用系统全局混音的 FFT 频谱（Visualizer, session 0），
 * 无需播放器接入即可跟随网易云/本地播放器等任何后台音乐；
 * 当 Visualizer 不可用或用户选择仅麦克风时，回退到 AudioRecord 拾音。
 * 每帧回调原始能量给 FrameListener（→ BeatDetector）。
 */
public final class AudioCapture {

    public interface FrameListener {
        /**
         * @param energy      原始能量（全频段幅度），未归一化
         * @param pitchLevel  主导音高强度 0~1（旋律/人声频段提取；麦克风无音高时取 0.5 中性）
         * @param pitchHue    主导音高对应的强调色相 0~360（越高越偏青→蓝→紫→品红）
         * @param nowMs       单调时钟
         */
        void onFrame(float energy, float pitchLevel, float pitchHue, long nowMs);
    }

    private static final String TAG = "RGBBeat/AudioCapture";
    private static final double BASE_A4 = 440.0;     // A4 基准频率

    private Visualizer visualizer;
    private AudioRecord mic;
    private Thread micThread;
    private volatile boolean running;
    private int sampleRate = 44100;   // Visualizer 采样率
    private int captureSize = 1024;   // Visualizer 捕获长度
    private long lastLogMs = 0;       // 调试日志节流

    private final FrameListener listener;

    public AudioCapture(FrameListener listener) {
        this.listener = listener;
    }

    public boolean start(String source) {
        stop();
        running = true;
        boolean useVisualizer = !"mic".equals(source);
        if (useVisualizer) {
            try {
                startVisualizer();
                DiagLog.d(TAG, "Visualizer 全局频谱已启动 sr=" + visualizer.getSamplingRate()
                        + " cap=" + visualizer.getCaptureSize());
                return true;
            } catch (Exception e) {
                DiagLog.d(TAG, "Visualizer 不可用，尝试麦克风: " + e.getMessage());
                stopVisualizer();
            }
        }
        if (!"visualizer".equals(source)) {
            if (startMic()) {
                DiagLog.d(TAG, "麦克风拾音已启动");
                return true;
            }
        }
        running = false;
        return false;
    }

    // ---------------- Visualizer 全局混音 ----------------

    private void startVisualizer() throws Exception {
        visualizer = new Visualizer(0);                       // session 0 = 全局输出混音
        visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        sampleRate = visualizer.getSamplingRate();
        captureSize = visualizer.getCaptureSize();

        visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override
            public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int rate) { }

            @Override
            public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                // 回调线程的未捕获异常同样会杀死进程（闪退），必须兜底
                try {
                    if (!running) return;
                    int n = (fft.length - 2) / 2;
                    float sum = 0f;
                    for (int i = 1; i <= n; i++) {
                        int re = fft[i * 2];        // 实部
                        int im = fft[i * 2 + 1];    // 虚部
                        sum += (float) Math.sqrt(re * re + im * im);
                    }
                    // 全频段能量：反映整体音量起伏，节拍/鼓点/重音都能形成 onset
                    float energy = sum / Math.max(1, n) / 128f;
                    float[] info = dominantInfo(fft);   // {freq, pitchLevel, pitchHue}
                    listener.onFrame(energy, info[1], info[2], SystemClock.elapsedRealtime());
                    logEverySecond(energy, info);
                } catch (Throwable t) {
                    DiagLog.w(TAG, "fft callback error: " + t);
                }
            }
        }, 60, true, false);

        visualizer.setEnabled(true);
    }

    /** 调试日志：每秒打印一次能量、主导音高、强调色相、主导频率，便于实机抓 logcat 定位问题 */
    private void logEverySecond(float energy, float[] info) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLogMs < 1000) return;
        lastLogMs = now;
        DiagLog.d(TAG, "fft energy=" + String.format("%.3f", energy)
                + " pitch=" + String.format("%.2f", info[1])
                + " hue=" + String.format("%.0f", info[2])
                + " freq=" + String.format("%.0f", info[0]) + "Hz"
                + " sr=" + sampleRate + " cap=" + captureSize + " src=visualizer");
    }

    /**
     * 从旋律/人声频段提取主导音高，返回 {主导频率Hz, 乐理强度0~1, 强调色相0~360}。
     * 峰值搜索限定在约 150Hz~5kHz：排除低频鼓点（其能量常压过真正旋律），使主导音高更贴近人声/旋律。
     * 无有效信号时返回强度 0.5 中性、色相 0。
     */
    private float[] dominantInfo(byte[] fft) {
        int bins = (fft.length - 2) / 2;
        if (bins <= 0) return new float[]{0f, 0.5f, 0f};
        double minF = 150.0, maxF = 5000.0;
        int iMin = Math.max(1, (int) (minF * captureSize / sampleRate));
        int iMax = Math.min(bins, (int) (maxF * captureSize / sampleRate));
        int peakIdx = -1;
        double peakMag = -1;
        for (int i = iMin; i <= iMax; i++) {
            double re = fft[i * 2];
            double im = fft[i * 2 + 1];
            double m = re * re + im * im;
            if (m > peakMag) { peakMag = m; peakIdx = i; }
        }
        if (peakIdx <= 0 || peakMag <= 0) return new float[]{0f, 0.5f, 0f};
        double freq = peakIdx * (double) sampleRate / captureSize;
        double midi = 69.0 + 12.0 * Math.log(freq / BASE_A4) / Math.log(2.0);
        float strength = scaleStrength(midi);
        // 强调色相：频率 150Hz~5kHz 映射到 180~350（青→蓝→紫→品红），音越高越偏紫/品红
        float t = (float) Math.max(0.0, Math.min(1.0, (freq - minF) / (maxF - minF)));
        float hue = 180f + t * 170f;
        return new float[]{(float) freq, strength, hue};
    }

    /**
     * 乐理感知音级强度 0~1。
     * 八度内十二半音级相对 C 的强度增量：E-F、B-C 半音仅差 0.02，其余自然音级全音差 0.06，
     * 升降号音取 0.03 居间；八度间抬升 0.34，使 B 到下一八度 C 亦为半音小差。
     */
    private static float scaleStrength(double midi) {
        int m = (int) Math.round(midi);
        int note = ((m % 12) + 12) % 12;         // 0=C ... 11=B
        int octave = m / 12 - 5;                 // 相对 C4 的八度偏移（C4=60 → 0）
        double[] inc = {
                0.00, 0.03, 0.06, 0.09,          // C  C#  D  D#
                0.12, 0.14, 0.17, 0.20,          // E  F  F#  G   (E-F 半音 +0.02)
                0.23, 0.26, 0.29, 0.32           // G# A  A#  B   (B-下一C 半音衔接)
        };
        double v = 0.25 + 0.34 * octave + inc[note];
        if (v < 0) v = 0;
        else if (v > 1) v = 1;
        return (float) v;
    }

    private void stopVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) { }
            visualizer = null;
        }
    }

    // ---------------- 麦克风回退 ----------------

    private boolean startMic() {
        try {
            int sampleRate = 44100;
            int minBuf = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) minBuf = 4096;
            int bufSize = Math.max(minBuf, 4096);
            mic = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
            if (mic.getState() != AudioRecord.STATE_INITIALIZED) return false;

            final short[] buf = new short[bufSize / 2];
            mic.startRecording();
            micThread = new Thread(() -> {
                while (running) {
                    try {
                        int read = mic.read(buf, 0, buf.length);
                        if (read <= 0) continue;
                        long sum = 0;
                        for (int i = 0; i < read; i++) sum += (long) buf[i] * buf[i];
                        double rms = Math.sqrt(sum / (double) read) / 32768.0;
                        // 麦克风无频谱音高信息，取中性 0.5 / 强调色相 0
                        listener.onFrame((float) rms, 0.5f, 0f, SystemClock.elapsedRealtime());
                    } catch (Throwable t) {
                        // 采集线程异常不外泄，避免杀死进程
                        DiagLog.w(TAG, "mic thread error: " + t);
                        try { Thread.sleep(50); } catch (InterruptedException ie) { return; }
                    }
                }
            }, "rgbbeat-mic");
            micThread.start();
            return true;
        } catch (Exception e) {
            DiagLog.w(TAG, "麦克风启动失败: " + e.getMessage());
            return false;
        }
    }

    private void stopMic() {
        running = false;
        if (micThread != null) {
            try { micThread.join(300); } catch (InterruptedException ignored) { }
            micThread = null;
        }
        if (mic != null) {
            try { mic.stop(); } catch (Exception ignored) { }
            mic.release();
            mic = null;
        }
    }

    public void stop() {
        stopVisualizer();
        stopMic();
        running = false;
    }
}
