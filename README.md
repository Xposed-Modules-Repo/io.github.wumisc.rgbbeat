# RGBBeat — 乘车晕车缓解 + 音乐律动 · LSPosed 模块

一个基于 **LSPosed/Xposed** 的安卓增强模块：自动识别**乘车环境**（打车/导航应用或车辆运动），开启屏幕边缘**晕车缓解光效**并提示；同时叠加**音乐律动光效**、**精准节拍震动**与**高潮礼物特效**——让耳朵听到的内容，在视觉与触觉上也炸一次。

> **v3.0.0（当前）**：界面与架构全量重写。
>
> - **UI 全面 Compose 化**：删除全部 XML 布局与 `AppCompatActivity` 页面，改为单 Activity + 三页 Pager；
> - **液态玻璃底部导航**：选中胶囊由 Pager 实时滚动比例驱动（手指滑页面 → 胶囊弹簧跟随 → 高速滑动时被拉伸），前景色按胶囊亮度自动切换，深色/浅色都不糊；
> - **程序化夜场场景**：深色首页「黑丝丝光」、浅色首页「白丝珍珠」、深色关于页「美系肌肉轮廓」、浅色关于页「中山装轮廓」、功能页「丝光 vs 力量」分屏对撞 —— 全部逐帧 Canvas 绘制，跟着节拍呼吸、扫光、冲击波；
> - **共鸣球**：64 根径向频谱柱 + 三层冲击波 + 内芯电源图标，能量/色相/BPM 实时联动，点一下就是开关；
> - **窗口统一 `forceDarkAllowed=false`**：HyperOS/ColorOS 的"强制深色"不再把自定义配色二次压暗；
> - **内核分层**：`engine / render / service / data` 四层，UI 只通过 `LiveBeat` 桥接读取节拍，不再直接摸服务；
> - **构建升级**：AGP 9.5 + Kotlin 2.4 + Compose 1.12 + Gradle 9.8 + JDK 21 + compileSdk 37。

---

## ✨ 功能

| 功能 | 说明 |
|------|------|
| 晕车场景自动检测 | 打开打车/导航应用（高德、百度、腾讯、滴滴、曹操、T3 等内置 12 个）或检测到车辆运动，自动开启晕车缓解光效并 Toast 提示 |
| 手动选择应用触发 | 除内置打车/导航外，可额外勾选任意应用，在前台时强制触发（Compose 应用选择器，支持搜索） |
| 离开自动回落 | 切出触发应用后光效自动回归常规逻辑，不残留 |
| 音乐自动触发 | 节拍命中频率识别到音乐 → 自动开光效；音乐停止 4 秒 → 自动关（省电） |
| 音乐识别黑名单 | 视频/直播等带背景音乐的应用可拉黑，前台时不误触发音乐光效 |
| 屏幕边缘 RGB 光效 | 屏幕四周柔和渐变光晕，色相沿色轮漂移、宽度/亮度随能量脉动 |
| 烟花爆炸 + 歌手名 | 节拍命中时爆心绽放烟花，并在爆心炸出当前歌手名 |
| 精准节拍震动 | 按节拍触发短震动，时长 45~130ms、振幅 80~255，高音/强拍更烈 |
| 键盘环绕光效 | 软键盘弹出时自动改为环绕键盘四条光带 |
| 音频来源自适应 | 优先系统全局频谱（Visualizer）；不可用时自动回退麦克风拾音 |
| 歌词对接 | 支持 LyricInfo（LSPosed 模块）读取当前歌名/歌手/歌词行，首页实时显示 |
| 高潮礼物特效 | 节拍命中且冷却已过时播放礼物视频（星际轨道 / 心海流光 / 鎏金凤凰），支持轮播或指定 |
| 特效呈现 | OpenGL 自适应抠背景 + FIT_CENTER 居中，黑底真正透明；三段素材可统一染色 |
| 服务常驻 | 开机自启 + 无障碍拉起 + 打开 App 自动拉起 + 服务自愈多保险 |
| 悬浮窗点击穿透 | 光效层不拦截任何触摸，纯视觉效果 |
| 颜色 DIY | 自定义光效主色，或跟随节奏自动彩虹 |

---

## 🧠 架构

```
app/src/main/
├─ java/io/github/wumisc/rgbbeat/          # 内核（Java，保持原有稳定性）
│   ├─ engine/     AudioCapture · BeatDetector · BeatState · MotionSense · VibrationEngine · ColorEngine
│   ├─ render/     LightRenderer · OverlayWindow · EffectVideoView · HighlightEffectsRenderer
│   │              Particle3DEngine · EffectLayout · EffectTint · EffectKeyMode · KeyboardWatcher
│   ├─ service/    BeatService · AppTriggerService · BootReceiver
│   └─ data/       Prefs · DiagLog · LyricInfoReader
├─ kotlin/io/github/wumisc/rgbbeat/ui/      # 界面（Kotlin + Compose）
│   ├─ MainActivity.kt      单 Activity：Edge-to-Edge、forceDark 关闭、三页 Pager 宿主
│   ├─ theme/               夜色板 / 珍珠板双主题，onColor() 自动算前景色
│   ├─ live/LiveBeat.kt     服务 volatile 状态 → 60fps Compose 状态（脉冲边沿检测 + 指数衰减）
│   ├─ art/Scene.kt         程序化场景引擎：SILK / MUSCLE / SUIT / DUO + Aura 玻璃底
│   ├─ glass/               液态玻璃面板 + 液态玻璃导航（模糊折射、液体拉伸、亮度自适应前景）
│   ├─ components/          霓虹开关、分段控制、滑杆、色板、能量条
│   └─ screens/             首页 / 特效控制台 / 关于 / 应用选择面板
└─ res/                     只剩图标、捐赠码、无障碍配置与字符串
```

数据流：

```
音频/传感器 → BeatDetector / MotionSense → BeatState（volatile）
                                              ├─ LightRenderer（悬浮窗光效）
                                              ├─ VibrationEngine（节拍震动）
                                              └─ LiveBeat（Compose 每帧采样）→ 场景/共鸣球/导航
```

---

## 🔧 构建

```bash
# 环境：JDK 21、Android SDK（platform 37 / build-tools 36）
./gradlew :app:assembleRelease
```

- `compileSdk 37` / `targetSdk 36` / `minSdk 26`
- AGP 9.5.0（内置 Kotlin 支持）+ Kotlin 2.4.21 + Compose 1.12.1
- 签名：仓库不内置私钥，缺省产出 unsigned 包；把 `keystore.properties` 放到仓库根目录即可自动签名：

```properties
storeFile=../rgbbeat.jks
storePassword=****
keyAlias=rgbbeat
keyPassword=****
```

> 注意：v3.0.0 更换了构建链与包结构。如果你装的是 2.x，请先卸载再装（签名不同无法覆盖升级）。

---

## 🛠 已知边界

- 光效依赖 `SYSTEM_ALERT_WINDOW`；首次启动会跳授权页。
- 晕车自动识别依赖无障碍服务，需要手动开启「RGBBeat 光效触发」。
- 液态玻璃的模糊在 Android 12（API 31）以下自动退化为半透明渐变（系统不支持 RenderEffect）。
- 场景绘制按帧计算；省电时建议关闭「3D 粒子氛围」并选「节电 700」。

---

## 致敬

LSPosed / Xposed 及整个开源社区。纯本地运行，无联网、无广告。

作者：@酷安锦无名 · AGPL-3.0
