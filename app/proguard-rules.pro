# LSPosed 模块：Xposed 入口类必须保留原名，禁止混淆
-keep class io.github.wumisc.rgbbeat.XposedInit { *; }

# 架构分层后的四个内核包全部保留：服务/渲染对象会被系统与 Xposed 反查，
# 渲染层还有大量匿名内部类（Runnable/回调），混淆后偶发的"类找不到"极难排查。
-keep class io.github.wumisc.rgbbeat.service.** { *; }
-keep class io.github.wumisc.rgbbeat.render.** { *; }
-keep class io.github.wumisc.rgbbeat.engine.** { *; }
-keep class io.github.wumisc.rgbbeat.data.** { *; }

# Xposed API 为 compileOnly，运行时不打包，无需处理
-dontwarn de.robv.android.xposed.**

# 保留注解与行号：崩溃栈可读，调试日志能对上源码
-keepattributes SourceFile,LineNumberTable,Annotation
