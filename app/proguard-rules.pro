# LSPosed 模块：Xposed 入口类必须保留原名，禁止混淆
-keep class com.rgbbeat.XposedInit { *; }
-keep class com.rgbbeat.core.** { *; }

# Xposed API 为 compileOnly，运行时不打包，无需处理
-dontwarn de.robv.android.xposed.**

# 保留所有原生入口
-keepattributes SourceFile,LineNumberTable
