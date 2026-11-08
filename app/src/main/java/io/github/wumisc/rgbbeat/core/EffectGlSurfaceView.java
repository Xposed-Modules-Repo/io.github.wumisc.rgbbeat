package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 礼物特效的 OpenGL 渲染层。
 *
 * 原实现把 mp4 直接铺到 MATCH_PARENT 的 TextureView 上，存在三个致命问题：
 *  1. 横屏素材（1568x720 / 992x720 / 896x528）被拉到竖屏全屏 → 只能看到局部、比例失真；
 *  2. mp4 没有 alpha 通道，黑/灰底不透明 → 整屏蒙一层灰，挡住下面所有内容；
 *  3. TextureView 一旦 GONE 就拿不到 SurfaceTexture，播放请求永远挂起。
 *
 * 这里改成：
 *  · MediaPlayer 把解码帧写进 OES 外部纹理（SurfaceTexture）；
 *  · 片元着色器按亮度做色键，暗部 alpha→0（背景透明），只保留亮部流光；
 *  · 顶点按视频原始宽高比等比缩放居中（FIT_CENTER），完整呈现、不裁切、不拉伸；
 *  · 整个 Surface 用 RGBA8888 + TRANSLUCENT，未播放时清屏为全透明；
 *  · 保持 VISIBLE 且不依赖 alpha 隐藏，Surface 一定会创建。
 */
public final class EffectGlSurfaceView extends GLSurfaceView
        implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "RGBBeat/EffectGL";

    private static final String VERTEX_SHADER =
            "attribute vec2 aPos;\n"
            + "attribute vec2 aUV;\n"
            + "varying vec2 vRawUV;\n"
            + "void main() {\n"
            + "  vRawUV = aUV;\n"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
            + "}\n";

    /**
     * 自适应背景抠除：不假设背景是黑是白，按「局部对比度 + 彩度」判断前景。
     *  · 平滑的黑/白/灰底、渐变雾 → 与邻域差异小且中性 → alpha 0（糊糊的一层消失）
     *  · 线条、粒子、星点 → 局部对比高 → 保留
     *  · 蓝色水墨、金色流光 → 彩度高 → 保留
     *  · 上下边缘羽化，消除视频矩形的硬边
     * 另提供 dark / light / off 三个模式，供特殊素材手动切换。
     * 输出预乘 alpha，配合 GL_ONE / ONE_MINUS_SRC_ALPHA 混合。
     */
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "varying vec2 vRawUV;\n"
            + "uniform samplerExternalOES uTex;\n"
            + "uniform mat4 uST;\n"
            + "uniform vec2 uTexel;\n"
            + "uniform float uStrength;\n"
            + "uniform int uMode;\n"
            + "uniform vec3 uTint;\n"
            + "uniform float uTintMix;\n"
            + "\n"
            + "vec4 tap(vec2 rawUv) {\n"
            + "  vec2 uv = (uST * vec4(rawUv, 0.0, 1.0)).xy;\n"
            + "  return texture2D(uTex, uv);\n"
            + "}\n"
            + "float sstep(float e0, float e1, float x) {\n"
            + "  float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);\n"
            + "  return t * t * (3.0 - 2.0 * t);\n"
            + "}\n"
            + "void main() {\n"
            + "  vec4 c = tap(vRawUV);\n"
            + "  float mx = max(max(c.r, c.g), c.b);\n"
            + "  float mn = min(min(c.r, c.g), c.b);\n"
            + "  float chroma = mx - mn;\n"
            + "  float a = 1.0;\n"
            + "  bool feather = true;\n"
            + "  if (uMode == 3) {\n"
            + "    a = 1.0;\n"
            + "    feather = false;\n"
            + "  } else if (uMode == 1) {\n"
            + "    a = sstep(0.025, 0.22, mx);\n"
            + "  } else if (uMode == 2) {\n"
            + "    a = max(sstep(0.05, 0.30, 1.0 - mx), sstep(0.05, 0.22, chroma));\n"
            + "    a *= sstep(0.01, 0.08, 1.0 - mn);\n"
            + "  } else {\n"
            + "    vec2 d = uTexel * 2.0;\n"
            + "    vec3 l = tap(vRawUV - vec2(d.x, 0.0)).rgb;\n"
            + "    vec3 r = tap(vRawUV + vec2(d.x, 0.0)).rgb;\n"
            + "    vec3 u = tap(vRawUV + vec2(0.0, d.y)).rgb;\n"
            + "    vec3 b = tap(vRawUV - vec2(0.0, d.y)).rgb;\n"
            + "    vec3 avg = (l + r + u + b) * 0.25;\n"
            + "    float edge = max(max(abs(c.r - avg.r), abs(c.g - avg.g)), abs(c.b - avg.b));\n"
            + "    a = max(sstep(0.025, 0.14, edge), sstep(0.05, 0.22, chroma));\n"
            + "    a *= sstep(0.02, 0.10, mx);\n"
            + "  }\n"
            + "  if (feather) {\n"
            + "    float fy = min(vRawUV.y, 1.0 - vRawUV.y) / 0.08;\n"
            + "    a *= sstep(0.0, 1.0, min(1.0, fy));\n"
            + "  }\n"
            + "  a *= uStrength;\n"
            + "  float y = max(max(c.r, c.g), c.b);\n"
            + "  vec3 tinted = uTint * (0.25 + 1.10 * y);\n"
            + "  tinted = mix(tinted, vec3(1.0), smoothstep(0.72, 1.0, y) * 0.35);\n"
            + "  vec3 outRgb = mix(c.rgb, tinted, uTintMix);\n"
            + "  gl_FragColor = vec4(outRgb * a, a);\n"
            + "}\n";

    /** 1.0 = 完整显示所需的最大不裁切尺寸；<1 更小，>1 会裁切（不要用） */
    private static final float FIT_SCALE = 1.0f;

    private final float[] stMatrix = new float[16];
    private final FloatBuffer quad;
    private final Object quadLock = new Object();

    private int program = 0;
    private int texId = 0;
    private int aPos = 0, aUV = 0, uST = 0, uTex = 0, uStrength = 0,
            uTexelLoc = 0, uModeLoc = 0, uTintLoc = 0, uTintMixLoc = 0;
    private SurfaceTexture surfaceTexture;
    private Surface videoSurface;

    private int viewW = 1, viewH = 1;
    private int videoW = 0, videoH = 0;
    private volatile boolean glReady = false;
    private volatile boolean showing = false;
    private volatile float strength = 1f;
    private volatile int keyMode = EffectKeyMode.MODE_AUTO;
    private volatile float[] tint = EffectTint.resolve(EffectTint.DEFAULT);
    private volatile boolean glFailed = false;
    private ReadyCallback readyCallback;

    public interface ReadyCallback {
        void onGlReady(Surface surface);
    }

    public EffectGlSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        // 必须带 alpha 通道，否则色键出来的透明区域会被合成成黑色
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY); // 有帧才绘制，省电
        quad = ByteBuffer.allocateDirect(16 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        writeQuad(1f, 1f);
        // 注意：绝不能 setVisibility(GONE/INVISIBLE)，否则 Surface 不会创建
        setVisibility(VISIBLE);
    }

    public void setReadyCallback(ReadyCallback cb) {
        readyCallback = cb;
        if (glReady && videoSurface != null && cb != null) cb.onGlReady(videoSurface);
    }

    public boolean isGlReady() { return glReady && videoSurface != null; }

    public boolean isGlFailed() { return glFailed; }

    public Surface getVideoSurface() { return videoSurface; }

    /** GL / shader 初始化失败时由外部决定是否降级，避免整层黑屏 */
    public void setShowing(boolean show) {
        showing = show && !glFailed;
        requestRender(); // show=false 时重新清屏，把上一层残留画面擦成透明
    }

    public void setStrength(float s) { strength = Math.max(0f, Math.min(1f, s)); }

    /** 抠背景模式，见 {@link EffectKeyMode}。切换后立即重绘，无需重播视频 */
    public void setKeyMode(int mode) {
        keyMode = mode;
        requestRender();
    }

    /** 统一染色 {r,g,b,mix}，见 {@link EffectTint}。切换后立即重绘 */
    public void setTint(float[] t) {
        if (t == null || t.length < 4) return;
        tint = t;
        requestRender();
    }

    /** 由 MediaPlayer 的 onVideoSizeChanged / onPrepared 调用，保持素材原始比例 */
    public void setVideoSize(final int w, final int h) {
        if (w <= 0 || h <= 0) return;
        queueEvent(() -> {
            videoW = w;
            videoH = h;
            if (surfaceTexture != null) {
                try { surfaceTexture.setDefaultBufferSize(w, h); } catch (Throwable ignored) { }
            }
            updateAspectQuad();
            requestRender();
        });
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        program = buildProgram();
        if (program == 0) {
            glFailed = true;
            DiagLog.w(TAG, "GL program build failed, effect layer stays transparent");
            return;
        }
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aUV = GLES20.glGetAttribLocation(program, "aUV");
        uST = GLES20.glGetUniformLocation(program, "uST");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        uStrength = GLES20.glGetUniformLocation(program, "uStrength");
        uTexelLoc = GLES20.glGetUniformLocation(program, "uTexel");
        uModeLoc = GLES20.glGetUniformLocation(program, "uMode");
        uTintLoc = GLES20.glGetUniformLocation(program, "uTint");
        uTintMixLoc = GLES20.glGetUniformLocation(program, "uTintMix");

        texId = createExternalTexture();
        surfaceTexture = new SurfaceTexture(texId);
        surfaceTexture.setOnFrameAvailableListener(this);
        videoSurface = new Surface(surfaceTexture);
        glReady = true;
        DiagLog.d(TAG, "GL surface ready");
        if (readyCallback != null) readyCallback.onGlReady(videoSurface);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewW = Math.max(1, width);
        viewH = Math.max(1, height);
        GLES20.glViewport(0, 0, viewW, viewH);
        updateAspectQuad();
        requestRender();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 每帧先清成全透明：没播放 / 播放结束 / 播放失败都不会留下遮挡
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (!showing || !glReady || glFailed || program == 0 || texId == 0
                || surfaceTexture == null) return;
        try {
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(stMatrix);
        } catch (Throwable t) {
            DiagLog.w(TAG, "updateTexImage failed: " + t);
            return;
        }

        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uST, 1, false, stMatrix, 0);
        GLES20.glUniform1f(uStrength, strength);
        GLES20.glUniform2f(uTexelLoc,
                videoW > 0 ? 1f / videoW : 1f / 1280f,
                videoH > 0 ? 1f / videoH : 1f / 720f);
        GLES20.glUniform1i(uModeLoc, keyMode);
        float[] t = tint;
        GLES20.glUniform3f(uTintLoc, t[0], t[1], t[2]);
        GLES20.glUniform1f(uTintMixLoc, t[3]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GLES20.glUniform1i(uTex, 0);

        synchronized (quadLock) {
            quad.position(0);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
            quad.position(2);
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 16, quad);
        }
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glEnable(GLES20.GL_BLEND);
        // 预乘 alpha：src 已经乘过 a，用 ONE / ONE_MINUS_SRC_ALPHA
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        requestRender();
    }

    /** 等比居中：视频更宽则宽度撑满，上下留透明；视频更高则高度撑满，左右留透明 */
    private void updateAspectQuad() {
        float[] s = EffectLayout.fit(viewW, viewH, videoW, videoH);
        writeQuad(s[0] * FIT_SCALE, s[1] * FIT_SCALE);
    }

    private void writeQuad(float sx, float sy) {
        // TRIANGLE_STRIP：左下 / 右下 / 左上 / 右上；UV 配合 SurfaceTexture 的变换矩阵
        float[] v = {
                -sx, -sy, 0f, 0f,
                 sx, -sy, 1f, 0f,
                -sx,  sy, 0f, 1f,
                 sx,  sy, 1f, 1f
        };
        synchronized (quadLock) {
            quad.clear();
            quad.put(v);
            quad.position(0);
        }
    }

    private static int buildProgram() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vs == 0 || fs == 0) return 0;
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            DiagLog.w(TAG, "program link: " + GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteProgram(p);
            p = 0;
        }
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        return p;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            DiagLog.w(TAG, "shader compile: " + GLES20.glGetShaderInfoLog(s));
            GLES20.glDeleteShader(s);
            return 0;
        }
        return s;
    }

    private static int createExternalTexture() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    /** 悬浮窗销毁 / 服务停止时释放。调用前必须先释放 MediaPlayer */
    public void release() {
        showing = false;
        glReady = false;
        readyCallback = null;
        if (videoSurface != null) {
            try { videoSurface.release(); } catch (Throwable ignored) { }
            videoSurface = null;
        }
        if (surfaceTexture != null) {
            try { surfaceTexture.setOnFrameAvailableListener(null); } catch (Throwable ignored) { }
            try { surfaceTexture.release(); } catch (Throwable ignored) { }
            surfaceTexture = null;
        }
    }
}
