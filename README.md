# RGBBeat - 音乐律动特效 LSPosed 模块

🎵 音乐律动灯光特效 + 视频礼物特效 + 晕车缓解光效

## ✨ 功能

- **音乐律动**：实时音频捕获 + 节拍检测，屏幕边缘随音乐节奏变色脉动
- **高潮特效**：节拍命中时全屏播放视频特效（星际轨道 / 海洋 / 凤凰）
- **晕车缓解**：车辆运动检测，左右光带跟随加减速 / 转弯 / 颠簸
- **液态玻璃导航栏**：Kyant0/AndroidLiquidGlass 同款 LiquidBottomTabs

## 📦 下载

[最新 Release](https://github.com/Xposed-Modules-Repo/io.github.wumisc.rgbbeat/releases)

## 🛠️ 编译

```bash
# 需要 JDK 21 + Gradle 9.3+ + compileSdk 37
./gradlew assembleDebug
```

## 📝 使用

1. 安装 APK 后，在 LSPosed 中启用模块
2. 勾选作用域（SystemUI 或其他目标应用）
3. 打开 RGBBeat，开启服务开关
4. 播放音乐，即可看到边缘灯光效果

## 🎖️ 致敬

本项目液态玻璃导航栏基于 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 开源组件，致敬并感谢 Kyant0 大神的伟大作品。

同时感谢 LSPosed / Xposed 框架，以及整个安卓模块开发社区。

## 📜 License

GPL-3.0
