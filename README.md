# RGBBeat — 乘车晕车缓解 + 音乐律动 · LSPosed 模块

一个基于 **LSPosed/Xposed** 的安卓增强模块：自动识别**乘车环境**（打车/导航应用或车辆运动），开启屏幕边缘**晕车缓解光效**并提示；同时叠加**音乐律动光效**、**精准节拍震动**与**高潮礼物特效**，听歌、打车两不误。

> **v2.2.2（当前）**：礼物特效整体重写 —— OpenGL 自适应抠背景（黑/白/灰/渐变底一律透明）、按素材原始比例 FIT_CENTER 居中完整呈现、三段素材统一染色（默认**白里粉**）、新增「背景抠除模式」与「礼物特效颜色」设置；修复「礼物特效根本不显示 / 只播第一段 / 设置页改了不生效」等一批链路问题。
>
> **v2.2.2 关键修复**：渲染层从 `GLSurfaceView`（SurfaceView）换成 `TextureView` + 自建 EGL。SurfaceView 会在窗口里创建独立图层，部分 ROM 上会挡住下层应用（桌面、本 App）的触摸，表现为「特效一出来就点不动」；TextureView 是普通 View，随窗口合成，不存在独立图层，从根本上消除该问题。
>
> **v1.8**：融合模式（晕车缓解 + 音乐律动）；打车/导航应用无障碍自动触发、离开自动回落；音乐自动触发（识别到音乐开光效、停止 4 秒自动关）；烟花爆炸在爆心**炸出歌手名**；音乐识别黑名单（视频/直播不误触发）；服务常驻（开机自启 + 无障碍拉起 + 打开 App 自动拉起）；精准节拍震动。

---

## ✨ 功能

| 功能 | 说明 |
|------|------|
| 晕车场景自动检测 | 打开打车/导航应用（高德、百度、腾讯、滴滴、曹操、T3 等内置 12 个）或检测到车辆运动，自动开启晕车缓解光效并 Toast 提示 |
| 手动选择应用触发 | 除内置打车/导航外，可额外勾选任意应用，在前台时强制触发 |
| 离开自动回落 | 切出触发应用后光效自动回归常规逻辑，不残留 |
| 音乐自动触发 | 节拍命中频率识别到音乐 → 自动开光效；音乐停止 4 秒 → 自动关（省电） |
| 音乐识别黑名单 | 视频/直播等带背景音乐的应用可拉黑，前台时不误触发音乐光效 |
| 屏幕边缘 RGB 光效 | 屏幕四周柔和渐变光晕，色相沿色轮漂移、宽度/亮度随能量脉动 |
| 烟花爆炸 + 歌手名 | 节拍命中时爆心绽放烟花（先升空后爆炸），并在爆心**炸出当前歌手名**（歌手优先/歌名兜底，随爆炸放大渐隐） |
| 精准节拍震动 | 按节拍触发短震动，时长 45~130ms、振幅 80~255，高音/强拍更烈 |
| 键盘环绕光效 | 软键盘弹出时自动改为环绕键盘上下左右四条光带 |
| 音频来源自适应 | 优先系统全局频谱（Visualizer）；不可用时自动回退麦克风拾音 |
| 歌词对接 | 支持 LyricInfo（LSPosed 模块）读取当前歌名/歌手/歌词行 |
| 高潮礼物特效 | 节拍命中且冷却已过时播放礼物视频（星际轨道 / 心海流光 / 鎏金凤凰），支持自动轮播或指定单段 |
| 特效呈现 | OpenGL 自适应抠背景 + 按素材原始比例 FIT_CENTER 居中，完整呈现、不裁切、不拉伸，黑底真正透明 |
| 服务常驻 | 开机自启 + 无障碍拉起 + 打开 App 自动拉起 + 服务自愈多保险 |
| 悬浮窗点击穿透 | 光效层不拦截任何触摸，纯视觉效果 |
| 颜色 DIY | 支持自定义光效主色，或跟随节奏自动彩虹；礼物特效可单独统一染色 |

---

## 🧠 工作原理

```
系统全局混音 / 麦克风 ──AudioCapture──▶ BeatDetector（节拍 onset + BPM）
                                        │ 写入 BeatState（volatile 共享，beatSeq 单调递增）
                                        ▼
  无障碍服务 AppTriggerService ──前台包名──▶ 晕车/打车触发判定（内置+手动+黑名单）
  车辆运动传感器 MotionSense  ──加速度──▶ 晕车缓解触发（转弯/颠簸/加减速）
                                        │
                                        ▼
  LightRenderer（渲染 45fps）        VibrationEngine（精准节拍震动）
  · 边缘光晕 / 烟花+歌手名           · 按节拍、能量、音高调节
  · 键盘环绕 / 色相漂移               · 兼容无幅度控制设备
        │
        └─ beatSeq 变化 + 冷却已过 + 未在播放 ──▶ HighlightEffectsRenderer
                                                    │ MediaPlayer → SurfaceTexture(OES)
                                                    ▼
                                        EffectVideoView（TextureView + EGL）
                                        · 局部对比度 + 彩度自适应抠背景
                                        · EffectLayout FIT_CENTER 等比居中
                                        · EffectTint 统一染色（默认白里粉）
```

- **音乐识别**：麦克风/频谱下以**节拍命中频率**判定是否在放音乐（有节奏的音乐才有稳定命中），配合 600ms 进入滞回 + 4s 退出滞回；避免能量阈值在低音量下永远判不到的问题。
- **晕车触发**：无障碍服务监听前台包名，命中内置打车/导航或手动勾选应用即强制开光效；切出后自动回落。车辆运动传感器检测到持续移动也判定乘车。
- **烟花**：节拍命中时一朵（1.2s 节流），空闲时自动绽放；爆心绘制歌手名，随爆炸放大上浮渐隐。
- **礼物特效触发**：用 `BeatState.beatSeq`（单调序号）做边沿检测，而不是只持续一帧的 `state.beat`，渲染线程不会漏拍；12s 冷却 + 「未在播放」双门限，避免视频连播刷屏。播放链路带 6s prepare 看门狗，解码器卡住会自动复位，不会把特效永久锁死。

---

## 🎬 礼物特效渲染（v2.2.0 重写）

| 环节 | 做法 |
|------|------|
| 解码 | `MediaPlayer` + `AssetFileDescriptor`（比 `android.resource://` 在部分 ROM 上更稳），静音、不打断正在播放的音乐 |
| 取帧 | `SurfaceTexture`（OES 外部纹理），帧率由解码器驱动 |
| 上屏 | 自建 EGL 上下文把着色器结果画进 `TextureView` 的 BufferQueue（普通 View，无独立图层，不影响触摸） |
| 抠背景 | 片元着色器按**局部对比度（4 邻域差分）+ 彩度**判断前景：平滑的黑/白/灰/渐变一律 alpha→0，线条、粒子、星点、彩色流光保留；视频上下边缘 8% 羽化，消除矩形硬边 |
| 居中 | `EffectLayout.fit()` 按素材原始宽高比算 NDC 半尺寸（FIT_CENTER），横屏素材完整显示、不裁切不拉伸 |
| 染色 | `EffectTint` 按亮度调制目标色（`uTint*(0.25+1.10*y)`），高亮处混白芯；默认**白里粉 `#FFB7D8`**，可切心形粉/紫/青/蓝/橙/绿/红/素材原色 |
| 合成 | RGBA8888 + 预乘 alpha，未播放时整帧清成全透明；不使用 SurfaceView，也不使用 `setZOrderOnTop` |
| 可调 | 设置项「背景抠除模式」自动/纯黑底/浅色·白底/不抠；「礼物特效颜色」9 种；切换后立即重绘，正在播放也生效 |

原始素材为 HEVC(hvc1)、moov 在文件尾，已统一转为 **H.264 Main / yuv420p / moov 前置 / 无音轨**，兼容 Android 8.0+ 硬解。

---

## 🔧 编译

环境要求：JDK 17+、Android SDK（compileSdk 35、build-tools 35）、Gradle 8.14（AGP 8.5.2）。

```bash
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM 回归测试（45 个）
```

> 首次运行会自动下载 Gradle 与依赖。若 SDK 目录非默认，创建 `local.properties`：
> ```
> sdk.dir=/path/to/Android/sdk
> ```

---

## 📲 安装与激活

1. **安装 LSPosed 框架**（需 Root / KernelSU 的 LSPosed 分支）并激活。
2. 安装 APK，打开 **LSPosed → 模块**，勾选启用 **RGBBeat**。
3. **推荐作用域勾选 SystemUI**（模块自启 hook 只注入 `com.android.systemui`）。
4. 重启手机（使 LSPosed 对 SystemUI 的 Hook 生效）。
5. 打开 RGBBeat App：
   - **授予悬浮窗权限**（显示光效的前提）。
   - **开启无障碍服务**（晕车/打车自动触发、服务常驻需要）：系统设置 → 无障碍 → RGBBeat 光效触发。
   - Android 13+ 允许通知（前台服务提示）。
6. 播放音乐即自动出现律动光效；打开打车/导航即自动进入晕车缓解模式。

> 说明：
> - 悬浮窗权限是显示光效的前提；即使未授予，服务与震动仍可运行（只是不绘制光效）。
> - 若想只在听歌时触发、不想误识别视频/直播背景音乐：设置 → 音乐识别黑名单 → 勾选对应 App。
> - 若你的 ROM 后台清理特别激进，请在系统设置里把 RGBBeat 加入"自启动 + 后台无限制"白名单。
> - **签名**：Release 附带的 APK 为 **debug 签名**（工程未配置 release keystore）。若你之前装的是官方 release 签名版本，需先卸载再安装（签名不一致无法覆盖，卸载会丢失应用内设置）。

---

## ⚙️ 设置项

| 设置 | 作用 |
|------|------|
| 工作模式 | 融合（音乐+晕车，默认）/ 音乐律动 / 晕车缓解 |
| 晕车场景自动检测 | 打车/导航应用或车辆运动触发晕车缓解 |
| 车辆运动触发 | 加速度计检测车辆移动判定乘车 |
| 进入乘车环境 Toast | 识别到乘车时弹「已进入乘车环境 · 晕车缓解已开启」 |
| 手动选择应用触发 | 额外勾选的应用在前台时强制触发 |
| 音乐自动触发 | 识别到音乐自动开光效、停止 4 秒关 |
| 音乐识别黑名单 | 黑名单内应用（视频/直播）前台时不触发音乐光效 |
| 开机自启 | 开机自动运行光效服务 |
| 光效模式 | 屏幕边缘 / 跑马灯 / 仅键盘环绕 / 自动切换 |
| 光效亮度 / 速度 | 整体亮度 20~255 / 色轮转速 5~120 |
| 音频来源 | 自动（频谱优先，麦克风回退）/ 仅频谱 / 仅麦克风 |
| 节拍灵敏度 / 冷却 | 阈值 0.5~3.0 / 两次节拍最短间隔 ms |
| 马达震动 / 强度 | 开启 / 关闭 / 0~255 |
| 音乐高潮自动播放 | 节拍命中且冷却已过时自动播放礼物视频特效 |
| 礼物特效 | 自动轮播 / 星际轨道 / 心海流光 / 鎏金凤凰 |
| 立即预览特效 | 手动播放一次，用于确认视频素材能正常显示（首页也有预览按钮） |
| 特效呈现 | OpenGL 自适应抠背景（平滑底色全透明）+ 按素材原始比例 FIT_CENTER 居中，完整呈现不裁切 |
| 背景抠除模式 | 自动（通吃黑/白/灰/渐变底）/ 纯黑底 / 浅色·白底 / 不抠（排查用），切换后立即生效 |
| 礼物特效颜色 | 三段素材统一染色，默认白里粉 `#FFB7D8`（白芯透粉）；可选心形粉 / 紫 / 青 / 蓝 / 橙 / 绿 / 红 / 素材原色 |

---

## 🚨 常见问题

- **光效不显示**：确认已授予悬浮窗权限；确认 LSPosed 已勾选启用并重启。
- **礼物特效不显示**：先在「设置 → 高潮礼物特效 → 立即预览特效」或首页「立即预览礼物特效」手动播一次。若仍无画面，看 App 内「查看调试日志」：
  - 没有 `GL surface ready` → 悬浮窗尚未创建（先开启服务 / 授予悬浮窗权限），或设备 OpenGL ES 2.0 不可用；本版本的 GL 层始终 VISIBLE，不再有「GONE 导致拿不到 Surface」的问题。
  - `MediaPlayer error` → 解码器不兼容。三段 `res/raw/*.mp4` 已统一转为 **H.264 Main / yuv420p / moov 前置 / 无音轨**（兼容 Android 8.0+ 硬解）；原始 HEVC(hvc1) 素材保留在交付包 `original-hevc-mp4/`，需要时可直接覆盖回 `res/raw/`。
  - `prepare timeout 6000ms` → 解码器 6 秒没吐出首帧，已自动复位；多为素材编码/解码器不匹配，换素材或转码即可。
- **特效一出现，桌面/App 就点不动（v2.2.0 / v2.2.1 的问题，v2.2.2 已修）**：旧实现用 `GLSurfaceView`（底层 `SurfaceView`）承载视频，它会在窗口里额外创建**独立图层**；部分 ROM 把这个图层当作遮挡层，挡住下层应用的触摸（系统窗口如状态栏、手势导航不受影响，所以会出现「能切后台但点不了图标」）。窗口自身的 `FLAG_NOT_TOUCHABLE` 对这种图层无效。v2.2.2 改为 `TextureView` + 自建 EGL，画面随窗口合成、无独立图层，触摸不再受影响。
- **特效只播第一段**：旧版 `stop()` 会把轮播索引重置为 -1，导致 `(current+1)%3` 永远算出 0；v2.2.0 已用 `EffectPicker` 修正并加回归测试。
- **特效灰黑底遮住界面 / 只看到局部**：`EffectVideoView` 做逐像素抠背景，`EffectLayout.fit()` 按素材原始宽高比 FIT_CENTER 居中，横屏素材（1568×720 / 992×720 / 896×528）完整呈现、不裁切、不拉伸。
- **特效底部有一层糊糊的雾 / 浅色矩形**：纯亮度色键只认黑底，遇到中亮度平滑灰雾、白底、渐变背景会把整块背景当「亮部特效」保留下来。现默认「自动」模式按**局部对比度 + 彩度**判断前景：平滑底色（黑/白/灰/渐变）透明，线条、粒子、星点、彩色水墨保留，并对视频上下边缘羽化消除硬边。换了特殊素材若仍有残留，到「设置 → 高潮礼物特效 → 背景抠除模式」切换：自动 / 纯黑底 / 浅色·白底 / 不抠（排查用）；切换后立即重绘，正在播放也生效。阈值在 `EffectVideoView.FRAGMENT_SHADER`，模式映射在 `EffectKeyMode`。
- **三段特效颜色不统一 / 白色线条在浅色界面上看不清**：设置 → 高潮礼物特效 → 礼物特效颜色，默认把三段统一染成白里粉 `#FFB7D8`（亮部收成白芯、中间调透粉，保留亮度层次），另有心形粉 / 紫 / 青 / 蓝 / 橙 / 绿 / 红 / 素材原色可选，切换后立即重绘。旧版存过默认「心形粉」的用户会一次性迁移到白里粉，之后自己选的不会被覆盖。
- **晕车（motion）模式下特效不自动触发**：触发条件为节拍命中，该模式没有音频输入、`beatSeq` 不递增，因此不会自动播放（手动预览不受影响）。如需该模式也播放，把 `LightRenderer` 的触发条件改为「节拍边沿 或 能量超过阈值」即可。
- **设置页改了没反应**：旧版设置页写入 `<包名>_preferences`，而服务/首页读取 `rgbbeat`，两者不通；本版本已统一为 `rgbbeat`，并在打开设置页时自动迁移旧值。
- **打车/导航不自动触发**：确认系统无障碍里已开启 RGBBeat 光效触发；看 logcat `RGBBeat/Trigger` 是否有 `accessibility connected`。
- **音乐不自动触发**：确认设置里"音乐自动触发"已打开；放歌时看 `RGBBeat/Service` 是否有 `musicActive -> true`。
- **视频/直播背景音乐误触发**：设置 → 音乐识别黑名单 → 勾选该 App。
- **震动无响应**：确认"马达震动"开关已开；看 logcat `RGBBeat/Service` 的 `beat hit` 是否有日志（无则未命中节拍，与马达无关）。
- **退出 App 后灯停**：已加服务多保险自愈（开机自启 + 无障碍拉起 + 打开 App 自动拉起）。若仍被强杀，请在系统"自启动管理/后台限制"中放行。
- **电池消耗**：渲染线程为 45fps，可视化不可避免耗电；不开音乐自动触发、或不需要时停止服务更省电。礼物特效只在播放那几秒占用 GL。
- **安全说明**：本模块仅做视觉/震动反馈，不读取音频内容、不上传数据、不注入非系统进程（仅 SystemUI 自启 hook）。

---

## 📁 工程结构

```
RGBBeat/
├── settings.gradle / build.gradle / gradle.properties
└── app/
    ├── build.gradle                 # LSPosed API (compileOnly) + AndroidX 依赖
    └── src/main/
        ├── AndroidManifest.xml      # LSPosed 模块声明 + 无障碍服务 + Service/Activity
        ├── assets/xposed_init       # Xposed 入口类
        ├── java/io/github/wumisc/rgbbeat/
        │   ├── XposedInit.java      # Hook SystemUI 拉起服务
        │   ├── core/
        │   │   ├── BeatService.java            # 常驻前台服务，组装整条链路（含自愈）
        │   │   ├── AudioCapture.java           # Visualizer 全局频谱 + 麦克风回退
        │   │   ├── BeatDetector.java           # 节拍 onset 检测 + BPM 估算（beatSeq）
        │   │   ├── BeatState.java              # 跨线程共享状态（含晕车/媒体字段）
        │   │   ├── LightRenderer.java          # 边缘光晕/烟花/歌手名渲染、特效触发
        │   │   ├── OverlayWindow.java          # 悬浮窗覆盖层（FrameLayout 叠加 GL 层）
        │   │   ├── LightOverlayView.java       # Canvas 光效绘制层
        │   │   ├── HighlightEffectsRenderer.java # 礼物特效播放状态机（MediaPlayer 生命周期）
        │   │   ├── EffectVideoView.java        # TextureView + EGL 渲染层 + 自适应抠背景着色器
        │   │   ├── EffectLayout.java           # FIT_CENTER 等比居中计算（纯 Java）
        │   │   ├── EffectKeyMode.java          # 抠背景模式映射（自动/黑底/浅底/不抠）
        │   │   ├── EffectTint.java             # 统一染色色表（默认白里粉）
        │   │   ├── EffectPicker.java           # 三段特效轮播/指定逻辑（纯 Java）
        │   │   ├── Particle3DEngine.java       # 3D 粒子氛围（默认关闭）
        │   │   ├── VibrationEngine.java        # 精准节拍震动
        │   │   ├── MotionSense.java            # 车辆运动传感器采集
        │   │   ├── AppTriggerService.java      # 无障碍触发 + 前台包名判定
        │   │   ├── LyricInfoReader.java        # LyricInfo 歌词/歌手反射读取
        │   │   ├── DiagLog.java                # 环形日志（App 内查看）
        │   │   ├── Prefs.java                  # 设置读取 + 旧值迁移
        │   │   └── BootReceiver.java           # 开机自启
        │   └── ui/  MainActivity / SettingsActivity / AppPickActivity / AboutActivity
        ├── res/  Material 3 布局、霓虹渐变配色、设置、字符串、raw 三段特效视频
        └── test/ 45 个 JVM 回归测试（特效轮播/尺寸/抠背景模式/染色/渲染稳定性）
```

---

## 📜 许可证

本项目基于 **AGPL-3.0** 许可分发（见 `LICENSE`）；使用本模块产生的视觉效果属本地行为，不构成对任何音乐内容的复制或再分发。

---

## 🎖️ 致敬

本模块建立在 **LSPosed / Xposed** 框架之上，致敬并感谢这些伟大的开源项目，以及整个 Xposed / LSPosed 社区——正是他们让安卓模块生态生生不息。

### 粒子氛围特效灵感来源

RGBBeat 的 **3D 粒子氛围引擎**（3D 粒子玫瑰、星场穿越、多形状变形）在设计与算法思路上借鉴了以下开源项目（仅借鉴思路与机制，实现均为原创 Java/Canvas 代码，并遵守各自原始许可证）：

| 项目 | 作者 | 借鉴点 |
|------|------|--------|
| [morph-particles](https://github.com/MisterPrada/morph-particles) | MisterPrada | 粒子在形状点云间变形的机制（玫瑰 ↔ 爱心 ↔ 歌手名点阵） |
| [qixi-particle-rose](https://github.com/mrsuperguo/qixi-particle-rose) | mrsuperguo | 旋转 3D 粒子玫瑰 + 音乐互动绽放 |
| [eternal-rose-code](https://github.com/Ling-Ta/eternal-rose-code) | Ling-Ta | 粒子玫瑰参数曲线与粒子汇聚效果 |
| [particle-rose-mobile](https://github.com/FBBer/particle-rose-mobile) | FBBer | 手机端可旋转 3D 粒子花束 |
| [Starfield-wallpaper-for-Lively](https://github.com/HaLo2FrEeEk/Starfield-wallpaper-for-Lively) | HaLo2FrEeEk | 星场粒子流动（星际穿越背景层） |

感谢以上作者与所有用代码创造浪漫的程序员们。

---

## 💖 支持捐赠

觉得好用，请作者喝杯奶茶：

<p align="center">
  <img src="donate_qr.webp" alt="微信捐赠二维码" width="240" />
</p>

微信扫一扫 · 感谢你的支持

---

*我希望我们能够改变一下这个世界，哪怕只有一点点，诸君共勉。*
