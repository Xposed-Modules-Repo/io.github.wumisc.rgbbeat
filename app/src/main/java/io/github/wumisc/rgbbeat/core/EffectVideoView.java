package io.github.wumisc.rgbbeat.core;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 礼物特效的渲染层（TextureView + EGL 离屏 GL 绘制）。
 *
 * 为什么不用 GLSurfaceView：
 * `GLSurfaceView` 底层是 `SurfaceView`，会在窗口里额外创建一个**独立图层**。
 * 在部分 ROM 上，这个全屏图层会让下层应用（桌面、本 App 自己的界面）的触摸被吞掉 ——
 * 表现为「特效一出来，整台手机点不动」，而状态栏、手势导航还能用（系统窗口不受影响）。
 * v2.2.0 因此翻车，v2.2.1 去掉 setZOrderOnTop 后仍有应用级触摸被挡。
 *
 * 现在改成：MediaPlayer 解码到 SurfaceTexture(OES) → 我们自己建 EGL 环境，
 * 把色键处理后的画面**直接渲染进 TextureView 的 BufferQueue**。
 * TextureView 是普通 View，画面随窗口一起合成，不产生任何独立图层，
 * 因此不存在遮挡/拦截触摸的可能。
 *
 * 着色器与 v2.2.0 一致：自适应抠背景（局部对比度 + 彩度）、边缘羽化、统一染色。
 */
public final class EffectVideoView extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = "RGBBeat/EffectVideo";

    private static final String VERTEX_SHADER =
            "attribute vec2 aPos;\n"
            + "attribute vec2 aUV;\n"
            + "varying vec2 vRawUV;\n"
            + "void main() {\n"
            + "  vRawUV = aUV;\n"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
            + "}\n";

    /** 自适应抠背景 + 统一染色，逻辑同 v2.2.0，输出预乘 alpha */
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

    public interface ReadyCallback {
        void onGlReady(Surface surface);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final FloatBuffer quad;
    private final Object quadLock = new Object();
    private final AtomicBoolean renderQueued = new AtomicBoolean(false);

    private HandlerThread glThread;
    private Handler glHandler;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig eglConfig;

    private int program = 0;
    private int texId = 0;
    private int aPos = 0, aUV = 0, uST = 0, uTex = 0, uStrength = 0,
            uTexelLoc = 0, uModeLoc = 0, uTintLoc = 0, uTintMixLoc = 0;

    private SurfaceTexture decoderTexture;
    private volatile Surface decoderSurface;
    private Surface viewSurface;

    private volatile int viewW = 1, viewH = 1;
    private volatile int videoW = 0, videoH = 0;    private volatile boolean glReady = false;
    private volatile boolean showing = false;
    private volatile boolean glFailed = false;
    private volatile boolean released = false;
    private volatile float strength = 1f;
    private volatile int keyMode = EffectKeyMode.MODE_AUTO;
    private volatile float[] tint = EffectTint.resolve(EffectTint.DEFAULT);

    private final float[] stMatrix = new float[16];

    public EffectVideoView(Context context) {
        super(context);
        setOpaque(false);            // 内容带 alpha，必须让视图参与透明合成
        setSurfaceTextureListener(this);
        setVisibility(VISIBLE);      // 绝不能 GONE：TextureView 没 SurfaceTexture 就无法渲染
        quad = ByteBuffer.allocateDirect(16 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        updateQuad();
    }

    // ---------------- 对外 API（与旧渲染层保持一致） ----------------

    public void setReadyCallback(ReadyCallback cb) {
        readyCallback = cb;
        Surface s = decoderSurface;
        if (glReady && s != null && cb != null) cb.onGlReady(s);
    }

    private ReadyCallback readyCallback;

    public boolean isGlReady() {
        return glReady && decoderSurface != null && !released;
    }

    public boolean isGlFailed() { return glFailed; }

    public Surface getVideoSurface() { return decoderSurface; }

    /** 播放结束 / 停止时清成全透明；TextureView 内容会保留上一帧，所以必须重绘一次 */
    public void setShowing(boolean show) {
        showing = show && !glFailed;
        requestRender();
    }

    public void setStrength(float s) { strength = Math.max(0f, Math.min(1f, s)); }

    public void setKeyMode(int mode) {
        keyMode = mode;
        requestRender();
    }

    public void setTint(float[] t) {
        if (t == null || t.length < 4) return;
        tint = t;
        requestRender();
    }

    public void setVideoSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        videoW = w;
        videoH = h;
        updateQuad();
        requestRender();
    }

    /** 释放：停渲染线程 + 拆 EGL + 释放 Surface */
    public void release() {
        released = true;
        glReady = false;
        readyCallback = null;
        Surface s = decoderSurface;
        decoderSurface = null;
        if (s != null) {
            try { s.release(); } catch (Throwable ignored) { }
        }
        Handler h = glHandler;
        if (h != null) {
            h.post(this::teardownGl);
        }
    }

    // ---------------- TextureView 生命周期 ----------------

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        viewW = Math.max(1, width);
        viewH = Math.max(1, height);
        if (released) return;
        if (glThread == null) {
            glThread = new HandlerThread("rgbbeat-effect-gl");
            glThread.start();
            glHandler = new Handler(glThread.getLooper());
        }
        glHandler.post(() -> setupGl(surfaceTexture));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        viewW = Math.max(1, width);
        viewH = Math.max(1, height);
        updateQuad();
        requestRender();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        // 自己释放，返回 true 表示由我们接管
        Handler h = glHandler;
        if (h != null) {
            h.post(this::teardownGl);
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) { }

    // ---------------- GL 线程 ----------------

    private void setupGl(SurfaceTexture viewSt) {
        try {
            if (!initEgl(viewSt)) {
                glFailed = true;
                DiagLog.w(TAG, "EGL init failed, effect layer stays transparent");
                return;
            }
            program = buildProgram();
            if (program == 0) {
                glFailed = true;
                DiagLog.w(TAG, "shader build failed, effect layer stays transparent");
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
            decoderTexture = new SurfaceTexture(texId);
            decoderTexture.setOnFrameAvailableListener(st -> requestRender());
            decoderSurface = new Surface(decoderTexture);
            glReady = true;
            DiagLog.d(TAG, "GL ready (TextureView) " + viewW + "x" + viewH);

            final Surface ready = decoderSurface;
            main.post(() -> {
                if (!released && readyCallback != null) readyCallback.onGlReady(ready);
            });
            drawFrame();   // 先画一帧全透明，避免残留
        } catch (Throwable t) {
            glFailed = true;
            DiagLog.w(TAG, "setupGl failed: " + t);
        }
    }

    private boolean initEgl(SurfaceTexture viewSt) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false;
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false;

        int[] configAttribs = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
                || numConfigs[0] == 0) {
            return false;
        }
        eglConfig = configs[0];

        int[] ctxAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false;

        viewSurface = new Surface(viewSt);
        int[] surfAttribs = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, viewSurface, surfAttribs, 0);
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false;

        return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
    }

    private void teardownGl() {
        glReady = false;
        showing = false;
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface);
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext);
                }
                EGL14.eglTerminate(eglDisplay);
            }
        } catch (Throwable ignored) { }
        eglSurface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;

        if (decoderTexture != null) {
            try { decoderTexture.setOnFrameAvailableListener(null); } catch (Throwable ignored) { }
            try { decoderTexture.release(); } catch (Throwable ignored) { }
            decoderTexture = null;
        }
        // 解码端 Surface 也要收掉，否则 SurfaceTexture 销毁后还挂着死引用
        Surface ds = decoderSurface;
        decoderSurface = null;
        if (ds != null) {
            try { ds.release(); } catch (Throwable ignored) { }
        }
        if (viewSurface != null) {
            try { viewSurface.release(); } catch (Throwable ignored) { }
            viewSurface = null;
        }
        if (program != 0) {
            try { GLES20.glDeleteProgram(program); } catch (Throwable ignored) { }
            program = 0;
        }
        if (released && glThread != null) {
            glThread.quitSafely();
            glThread = null;
            glHandler = null;
        }
    }

    private void requestRender() {
        final Handler h = glHandler;
        if (h == null || released) return;
        if (!renderQueued.compareAndSet(false, true)) return;
        h.post(() -> {
            renderQueued.set(false);
            try {
                drawFrame();
            } catch (Throwable t) {
                DiagLog.w(TAG, "drawFrame error: " + t);
            }
        });
    }

    private void drawFrame() {
        if (!glReady || glFailed || program == 0) return;
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return;

        GLES20.glViewport(0, 0, viewW, viewH);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (showing && decoderTexture != null) {
            try {
                decoderTexture.updateTexImage();
                decoderTexture.getTransformMatrix(stMatrix);
            } catch (Throwable t) {
                DiagLog.w(TAG, "updateTexImage failed: " + t);
                EGL14.eglSwapBuffers(eglDisplay, eglSurface);
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
            // 预乘 alpha
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDisableVertexAttribArray(aPos);
            GLES20.glDisableVertexAttribArray(aUV);
        }

        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    }

    /** 等比居中：视频更宽则宽度撑满，上下留透明；视频更高则高度撑满，左右留透明 */
    private void updateQuad() {
        float[] s = EffectLayout.fit(viewW, viewH, videoW, videoH);
        float sx = s[0], sy = s[1];
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

    // ---------------- shader / texture ----------------

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
}
